package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the controller structure runtime state contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureRuntimeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void invalidation_requests_preserve_version_until_formation_changes() {
        StructureRuntime runtime = new StructureRuntime();

        assertThat(runtime.formed()).isFalse();
        assertThat(runtime.version()).isZero();

        runtime.requestCheck();
        runtime.onBlockChanged(null);

        assertThat(runtime.snapshot().dirty()).isTrue();
        assertThat(runtime.version()).isZero();
    }

    @Test
    void snapshots_do_not_expose_mutable_structure_collections() {
        StructureRuntime runtime = new StructureRuntime();

        assertThat(runtime.snapshot().criticalChunks()).isUnmodifiable();
    }

    @Test
    void accepted_attached_state_is_not_replaced_by_a_controller_refresh() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        setField(controller, "components", new ArrayList<>());
        setField(controller, "foundModifiers", new LinkedHashMap<>());
        StructureRuntime runtime = new StructureRuntime(controller);
        StructureSnapshot accepted = new StructureSnapshot(null, null, null, Direction.NORTH,
                Direction.EAST, 3, true, 9L, "error", "mismatch", null, false, false,
                Set.of(new ChunkPos(1, 2)));

        runtime.accept(accepted);

        assertThat(runtime.snapshot()).isEqualTo(accepted);
        assertThat(runtime.formed()).isTrue();
        assertThat(runtime.version()).isEqualTo(9L);
    }

    @Test
    void snapshot_keeps_both_error_and_block_mismatch_diagnostics() {
        StructureRuntime runtime = new StructureRuntime();
        StructureSnapshot accepted = new StructureSnapshot(null, null, null, Direction.SOUTH,
                Direction.SOUTH, 1, false, 2L, "level-error", "block-mismatch", null, true, true, Set.of());

        runtime.accept(accepted);

        assertThat(runtime.snapshot().lastStructureError()).isEqualTo("level-error");
        assertThat(runtime.snapshot().structureMismatchDiagnostic()).isEqualTo("block-mismatch");
    }

    @Test
    void attached_runtime_request_check_publishes_dirty_invalidation() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        StructureRuntime runtime = new StructureRuntime(controller);

        runtime.requestCheck();

        assertThat(runtime.snapshot().dirty()).isTrue();
        assertThat(runtime.version()).isZero();
    }

    @Test
    void attached_runtime_snapshot_does_not_read_back_controller_structure_fields() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        setField(controller, "components", new ArrayList<>());
        setField(controller, "foundModifiers", new LinkedHashMap<>());
        StructureRuntime runtime = controller.runtime().structure();
        StructureSnapshot accepted = new StructureSnapshot(null, null, null, Direction.NORTH,
                Direction.EAST, 3, true, 9L, "error", "mismatch", null, false, false,
                Set.of(new ChunkPos(1, 2)));

        runtime.accept(accepted);
        setField(controller, "structureVersion", 1L);
        setField(controller, "matchedStructureStage", 0);

        assertThat(controller.structureSnapshot()).isEqualTo(accepted);
        assertThat(controller.getStructureVersion()).isEqualTo(9L);
        assertThat(controller.getMatchedStructureStage()).isEqualTo(3);
    }

    @Test
    void detached_invalidation_updates_execution_and_published_state_together() {
        StructureRuntime runtime = new StructureRuntime();

        runtime.publish(new StructureRuntime.ExecutionState(
                new StructureSnapshot(null, null, null, Direction.SOUTH, Direction.SOUTH,
                        1, false, 2L, null, "mismatch", null, false, true, Set.of()),
                null, null, null, null, null, false, 0L, 0L, 0, -1L,
                null, false, null, null));

        runtime.requestCheck();

        assertThat(runtime.snapshot().dirty()).isTrue();
        Field executionStateField;
        try {
            executionStateField = StructureRuntime.class.getDeclaredField("executionState");
            executionStateField.setAccessible(true);
            StructureRuntime.ExecutionState executionState =
                    (StructureRuntime.ExecutionState) executionStateField.get(runtime);
            assertThat(executionState.snapshot().dirty()).isTrue();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void tick_requires_the_runtime_boundary_arguments() {
        StructureRuntime runtime = new StructureRuntime();

        assertThatThrownBy(() -> runtime.tick(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

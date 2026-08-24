package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
        StructureRuntime runtime = new StructureRuntime(controller);
        StructureSnapshot accepted = new StructureSnapshot(null, null, null, Direction.NORTH,
                Direction.EAST, 3, true, 9L, "error", "mismatch", false, false,
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
                Direction.SOUTH, 1, false, 2L, "level-error", "block-mismatch", true, true, Set.of());

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
    void tick_requires_the_runtime_boundary_arguments() {
        StructureRuntime runtime = new StructureRuntime();

        assertThatThrownBy(() -> runtime.tick(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

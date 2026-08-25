package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the final controller-owned structure runtime contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureRuntimeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void newRuntimeStartsUnformedAndDirty() throws Exception {
        MachineControllerBlockEntity controller = controller();

        assertThat(controller.structureSnapshot().formed()).isFalse();
        assertThat(controller.structureSnapshot().version()).isEqualTo(1L);
        assertThat(controller.structureSnapshot().dirty()).isTrue();
    }

    @Test
    void invalidationRequestKeepsThePublishedStructureSnapshotImmutable() throws Exception {
        MachineControllerBlockEntity controller = controller();

        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));
        StructureSnapshot snapshot = controller.structureSnapshot();

        assertThat(snapshot.dirty()).isTrue();
        assertThat(snapshot.criticalChunks()).isUnmodifiable();
        assertThat(snapshot.criticalChunks()).containsExactlyInAnyOrderElementsOf(java.util.Set.of());
    }

    @Test
    void structureBoundaryRequiresLevelAndControllerPosition() throws Exception {
        MachineControllerBlockEntity controller = controller();

        assertThatThrownBy(() -> controller.tickStructure(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.handleStructureChunkChanged(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedCheckRequestsDoNotInventAFormationVersion() throws Exception {
        MachineControllerBlockEntity controller = controller();
        long configuredVersion = controller.structureSnapshot().version();

        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));
        controller.onStructureBlockChanged(controller.getBlockPos().offset(1, 0, 0));

        assertThat(controller.structureSnapshot().version()).isEqualTo(configuredVersion);
    }

    private static MachineControllerBlockEntity controller() {
        return RuntimeTestFixtures.controller(MMCR.id("test_cube"));
    }
}

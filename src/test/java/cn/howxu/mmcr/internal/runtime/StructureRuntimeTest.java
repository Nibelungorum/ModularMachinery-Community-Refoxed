package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.StructureRuntime;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

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
        StructureRuntime runtime = runtime();

        assertThat(runtime.formed()).isFalse();
        assertThat(runtime.version()).isZero();
        assertThat(runtime.snapshot().dirty()).isTrue();
    }

    @Test
    void invalidationRequestKeepsThePublishedStructureSnapshotImmutable() throws Exception {
        StructureRuntime runtime = runtime();

        runtime.requestCheck();
        StructureSnapshot snapshot = runtime.snapshot();

        assertThat(snapshot.dirty()).isTrue();
        assertThat(snapshot.criticalChunks()).isUnmodifiable();
        assertThat(snapshot.criticalChunks()).containsExactlyInAnyOrderElementsOf(java.util.Set.of());
    }

    @Test
    void structureBoundaryRequiresLevelAndControllerPosition() throws Exception {
        StructureRuntime runtime = runtime();

        assertThatThrownBy(() -> runtime.tick(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.onChunkStateChanged(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatedCheckRequestsDoNotInventAFormationVersion() throws Exception {
        StructureRuntime runtime = runtime();

        runtime.requestCheck();
        runtime.requestCheck();

        assertThat(runtime.version()).isZero();
    }

    private static StructureRuntime runtime() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        Constructor<StructureRuntime> constructor = StructureRuntime.class
                .getDeclaredConstructor(MachineControllerBlockEntity.class);
        constructor.setAccessible(true);
        return constructor.newInstance(controller);
    }
}

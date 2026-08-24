package cn.howxu.mmcr.internal.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the controller structure runtime state contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructureRuntimeTest {

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
}

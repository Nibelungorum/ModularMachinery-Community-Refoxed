package cn.howxu.mmcr.internal.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the multiblock detector export payload contract.
 * @author howxu <dev@howxu.cn>
 */
class PktMultiblockDetectorExportPayloadTest {
    @Test
    void export_payload_distinguishes_java_and_kubejs() {
        assertThat(new PktMultiblockDetectorExportPayload(false).kubeJs()).isFalse();
        assertThat(new PktMultiblockDetectorExportPayload(true).kubeJs()).isTrue();
    }
}

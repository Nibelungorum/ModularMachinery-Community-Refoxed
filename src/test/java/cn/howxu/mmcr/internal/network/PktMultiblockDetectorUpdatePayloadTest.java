package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the multiblock detector state update payload contract.
 * @author howxu <dev@howxu.cn>
 */
class PktMultiblockDetectorUpdatePayloadTest {
    @Test
    void update_payload_carries_the_complete_selection_and_mask_state() {
        var selection = new MultiblockDetectorSelection(
                new BlockPos(10, 20, 30), Direction.NORTH,
                new BlockPos(8, 18, 28), new BlockPos(12, 22, 32));
        var payload = new PktMultiblockDetectorUpdatePayload(selection, true);

        assertThat(payload.selection()).isEqualTo(selection);
        assertThat(payload.maskEnabled()).isTrue();
        assertThat(PktMultiblockDetectorUpdatePayload.TYPE).isNotNull();
    }
}

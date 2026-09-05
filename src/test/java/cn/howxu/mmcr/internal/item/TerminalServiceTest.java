package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static java.lang.Integer.MAX_VALUE;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies server-side terminal state derivation helpers.
 * @author howxu <dev@howxu.cn>
 */
class TerminalServiceTest {
    @Test
    void preview_layers_are_sorted_and_bounded_from_the_structure_pattern() {
        BlockArray pattern = new BlockArray(Map.of(
                new BlockPos(0, 4, 0), new BlockPredicate.Any(),
                new BlockPos(0, -2, 0), new BlockPredicate.Any(),
                new BlockPos(0, 0, 0), new BlockPredicate.Any()));

        assertThat(TerminalService.previewLayers(pattern, 2)).containsExactly(-2, 0);
    }

    @Test
    void invalid_saved_stage_falls_back_to_the_first_controller_stage() {
        assertThat(TerminalService.effectiveStage(List.of(2, 5), 1)).hasValue(2);
    }

    @Test
    void no_controller_stage_means_no_stage_can_be_used_for_preview_derivation() {
        assertThat(TerminalService.effectiveStage(List.of(), 1)).isEmpty();
    }

    @Test
    void preview_layer_accepts_all_or_a_layer_from_the_current_structure() {
        assertThat(TerminalService.previewLayerAllowed(MAX_VALUE, List.of())).isTrue();
        assertThat(TerminalService.previewLayerAllowed(0, List.of(-2, 0, 4))).isTrue();
        assertThat(TerminalService.previewLayerAllowed(1, List.of(-2, 0, 4))).isFalse();
    }
}

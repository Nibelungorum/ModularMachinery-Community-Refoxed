package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
}

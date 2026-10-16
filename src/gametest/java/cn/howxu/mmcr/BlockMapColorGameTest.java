package cn.howxu.mmcr;

import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.MapColor;

/**
 * Verifies that registered machine blocks are eligible map surfaces.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BlockMapColorGameTest {
    private BlockMapColorGameTest() {
    }

    public static void registeredBlocksAreVisibleMapSurfaces(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        for (var entry : ModBlocks.BLOCKS.entrySet()) {
            for (var state : entry.getValue().get().getStateDefinition().getPossibleStates()) {
                MapColor color = state.getMapColor(helper.getLevel(), pos);
                // Both Xaero writers skip a surface before texture sampling when its map color is zero.
                helper.assertTrue(color != null && color != MapColor.NONE && color.col != 0,
                        "Map surface must not expose the ground below: " + entry.getKey() + " " + state);
            }
        }
        helper.succeed();
    }
}

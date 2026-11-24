package cn.howxu.mmcr.compat.athena.loaded;

import cn.howxu.mmcr.compat.athena.AthenaModelBridge;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Athena-backed model bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedAthenaModelBridge implements AthenaModelBridge {
    @Override
    public boolean available() { return true; }

    @Override
    public boolean collectParts(BlockStateModel sourceModel, BlockAndTintGetter level, BlockPos pos,
                                BlockState appearance, RandomSource random, List<BlockStateModelPart> parts) {
        if (!(sourceModel instanceof AthenaBakedModel athenaModel)) {
            return false;
        }
        athenaModel.collectParts(level, pos, appearance, random, parts);
        return true;
    }
}

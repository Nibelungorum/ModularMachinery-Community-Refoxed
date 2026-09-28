package cn.howxu.mmcr.compat.athena.loaded;

import cn.howxu.mmcr.compat.athena.AthenaModelBridge;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Athena-backed model bridge for the NeoForge 1.21.1 baked-model API.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class LoadedAthenaModelBridge implements AthenaModelBridge {
    @Override
    public ModelData modelData(BakedModel sourceModel, BlockAndTintGetter level, BlockPos pos,
                               BlockState appearance, ModelData fallback) {
        if (!(sourceModel instanceof AthenaBakedModel athenaModel)) {
            return null;
        }
        return athenaModel.getModelData(level, pos, appearance, fallback);
    }
}

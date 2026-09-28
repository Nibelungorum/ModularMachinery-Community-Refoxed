package cn.howxu.mmcr.compat.athena;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * No-op Athena bridge used when Athena is absent.
 *
 * @author howxu <dev@howxu.cn>
 */
final class UnavailableAthenaModelBridge implements AthenaModelBridge {
    static final UnavailableAthenaModelBridge INSTANCE = new UnavailableAthenaModelBridge();

    private UnavailableAthenaModelBridge() {
    }

    @Override
    public ModelData modelData(BakedModel sourceModel, BlockAndTintGetter level, BlockPos pos,
                               BlockState appearance, ModelData fallback) {
        return null;
    }
}

package cn.howxu.mmcr.compat.athena;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Isolates optional Athena model integration from the core renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AthenaModelBridge {
    static AthenaModelBridge get() {
        return AthenaModelBridgeBootstrap.bridge();
    }

    @Nullable
    ModelData modelData(BakedModel sourceModel, BlockAndTintGetter level, BlockPos pos,
                        BlockState appearance, ModelData fallback);
}

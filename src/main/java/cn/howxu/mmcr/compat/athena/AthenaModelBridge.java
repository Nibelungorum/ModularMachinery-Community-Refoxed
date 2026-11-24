package cn.howxu.mmcr.compat.athena;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Isolates optional Athena model integration from the core renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface AthenaModelBridge {
    static AthenaModelBridge get() {
        return AthenaModelBridgeBootstrap.bridge();
    }

    default boolean available() { return false; }

    boolean collectParts(BlockStateModel sourceModel, BlockAndTintGetter level, BlockPos pos,
                         BlockState appearance, RandomSource random, List<BlockStateModelPart> parts);
}

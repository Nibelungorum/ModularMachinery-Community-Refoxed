package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.internal.tile.LinkedAppearanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the vanilla appearance state exposed to connected-texture renderers.
 *
 * @author howxu <dev@howxu.cn>
 */
final class AppearanceStateResolver {
    private AppearanceStateResolver() {
    }

    static BlockState resolve(BlockState self, BlockGetter level, BlockPos pos,
                              @Nullable MachineAppearanceSpec.TextureSource source) {
        if (source == null || source.overrideTexture() != null) {
            return self;
        }
        Block block = BuiltInRegistries.BLOCK.get(source.blockId());
        if (block == null) {
            return self;
        }
        BlockState appearance = block.defaultBlockState();
        return Block.isShapeFullBlock(appearance.getShape(level, pos)) ? appearance : self;
    }

    static BlockState resolveLinked(BlockState self, BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LinkedAppearanceBlockEntity component)
                || component.linkedControllerPositions().isEmpty()) {
            return self;
        }
        return resolve(self, level, pos, component.appearanceSource());
    }
}

package cn.howxu.mmcr.internal.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Immutable multiblock preview data sent from the server to one client.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MultiblockPreviewSnapshot(ResourceKey<Level> dimension, BlockPos controllerPos, List<Entry> entries) {
    public MultiblockPreviewSnapshot {
        controllerPos = controllerPos.immutable();
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(BlockPos relativePos, BlockState state) {
        public Entry {
            relativePos = relativePos.immutable();
        }
    }
}

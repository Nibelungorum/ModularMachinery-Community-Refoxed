package cn.howxu.mmcr.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

/**
 * Immutable schema-backed chunk used exclusively by a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewChunk extends LevelChunk {
    private final PreviewLevel level;

    PreviewChunk(PreviewLevel level, ChunkPos position) {
        super(level, position);
        this.level = level;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return level.getBlockState(position);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return level.getBlockEntity(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return level.getFluidState(position);
    }

    @Override
    public BlockState setBlockState(BlockPos position, BlockState state, int flags) {
        throw new UnsupportedOperationException("preview chunks are immutable");
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        throw new UnsupportedOperationException("preview chunks do not store block entities");
    }
}

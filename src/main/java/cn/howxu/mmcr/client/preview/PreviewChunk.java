package cn.howxu.mmcr.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

/**
 * Immutable schema-backed chunk used exclusively by a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
final class PreviewChunk extends LevelChunk {
    private final PreviewLevel level;
    private final ChunkPos position;

    PreviewChunk(PreviewLevel level, ChunkPos position) {
        super(level, position);
        this.level = level;
        this.position = position;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return this.position.contains(position) ? level.getBlockState(position) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return this.position.contains(position) ? level.getBlockEntity(position) : null;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return this.position.contains(position) ? level.getFluidState(position) : Blocks.AIR.defaultBlockState().getFluidState();
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

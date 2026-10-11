package cn.howxu.mmcr.internal.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Immutable controller position that reuses neighbors in the standard direct-signal query.
 *
 * @author howxu <dev@howxu.cn>
 */
final class DirectSignalPosition extends BlockPos {
    private final BlockPos[] neighbors;

    DirectSignalPosition(BlockPos position) {
        super(position.getX(), position.getY(), position.getZ());
        Direction[] directions = Direction.values();
        neighbors = new BlockPos[directions.length];
        for (Direction direction : directions) {
            neighbors[direction.ordinal()] = super.relative(direction);
        }
    }

    @Override
    public BlockPos relative(Direction direction) {
        return neighbors[direction.ordinal()];
    }
}

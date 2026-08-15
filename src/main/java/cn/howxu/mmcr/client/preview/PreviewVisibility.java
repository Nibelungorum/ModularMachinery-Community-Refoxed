package cn.howxu.mmcr.client.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Determines which schema blocks are rendered in a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface PreviewVisibility permits PreviewVisibility.All, PreviewVisibility.SingleLayer {
    PreviewVisibility ALL = new All();

    boolean isVisible(BlockPos position, BlockState state);

    static PreviewVisibility singleLayer(int y) {
        return new SingleLayer(y);
    }

    /** @author howxu <dev@howxu.cn> */
    record All() implements PreviewVisibility {
        @Override
        public boolean isVisible(BlockPos position, BlockState state) {
            return true;
        }
    }

    /** @author howxu <dev@howxu.cn> */
    record SingleLayer(int y) implements PreviewVisibility {
        @Override
        public boolean isVisible(BlockPos position, BlockState state) {
            return position.getY() == y;
        }
    }
}

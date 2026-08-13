package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Builds server-authoritative multiblock preview snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MultiblockPreviewBuilder {
    private MultiblockPreviewBuilder() {}

    public static Optional<BlockState> previewState(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlockState ofState -> Optional.of(ofState.state());
            case BlockPredicate.OfBlock ofBlock -> Optional.of(ofBlock.block().defaultBlockState());
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream()
                    .map(MultiblockPreviewBuilder::previewState)
                    .flatMap(Optional::stream)
                    .findFirst();
            default -> Optional.empty();
        };
    }
}

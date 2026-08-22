package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * Selects representative blocks for unformed multiblock hints and previews.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MultiblockPreviewPredicates {
    private MultiblockPreviewPredicates() {}

    public static Optional<BlockPredicate> representative(BlockPredicate predicate) {
        if (predicate instanceof BlockPredicate.AnyOf anyOf) {
            return anyOf.children().stream()
                    .flatMap(child -> representative(child).stream())
                    .min(Comparator.comparingInt(MultiblockPreviewPredicates::exactStatePriority).reversed()
                            .thenComparingInt(MultiblockPreviewPredicates::priority));
        }
        return Optional.of(predicate).filter(MultiblockPreviewPredicates::hasRepresentableBlock);
    }

    public static <T> Optional<T> representativeValue(BlockPredicate predicate, Function<BlockPredicate, Optional<T>> mapper) {
        return representative(predicate).flatMap(mapper);
    }

    public static Optional<BlockState> state(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlockState ofState -> Optional.of(ofState.state());
            case BlockPredicate.OfBlock ofBlock -> Optional.of(ofBlock.block().defaultBlockState());
            case BlockPredicate.DeferredBlock deferred -> Optional.of(deferred.supplier().get().defaultBlockState());
            case BlockPredicate.OfTag ofTag -> BlockPredicate.blocksInTag(ofTag.tag()).stream()
                    .findFirst().map(Block::defaultBlockState);
            default -> Optional.empty();
        };
    }

    public static Optional<BlockState> machineCouplerState() {
        return Optional.of(ModBlocks.MODULE_BRIDGE.get().defaultBlockState());
    }

    private static boolean hasRepresentableBlock(BlockPredicate predicate) {
        return block(predicate).isPresent();
    }

    private static int priority(BlockPredicate predicate) {
        return block(predicate)
                .map(MultiblockPreviewPredicates::blockPriority)
                .orElse(3);
    }

    private static int exactStatePriority(BlockPredicate predicate) {
        return predicate instanceof BlockPredicate.OfBlockState ? 1 : 0;
    }

    private static Optional<Block> block(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlock ofBlock -> Optional.of(ofBlock.block());
            case BlockPredicate.OfBlockState ofState -> Optional.of(ofState.state().getBlock());
            case BlockPredicate.DeferredBlock deferred -> Optional.of(deferred.supplier().get());
            case BlockPredicate.OfTag ofTag -> BlockPredicate.blocksInTag(ofTag.tag()).stream().findFirst();
            default -> Optional.empty();
        };
    }

    private static int blockPriority(Block block) {
        if (block instanceof SmartInterfaceBlock) return 2;
        if (block instanceof IOPortBlock) return 1;
        return 0;
    }
}

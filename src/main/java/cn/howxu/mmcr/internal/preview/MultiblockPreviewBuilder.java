package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
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
            case BlockPredicate.OfBlockState ignored -> MultiblockPreviewPredicates.state(predicate);
            case BlockPredicate.OfBlock ignored -> MultiblockPreviewPredicates.state(predicate);
            case BlockPredicate.DeferredBlock ignored -> MultiblockPreviewPredicates.state(predicate);
            case BlockPredicate.OfTag ignored -> MultiblockPreviewPredicates.state(predicate);
            case BlockPredicate.AnyOf ignored -> MultiblockPreviewPredicates.representativeValue(predicate, MultiblockPreviewPredicates::state);
            case BlockPredicate.MachineCoupler ignored -> MultiblockPreviewPredicates.machineCouplerState();
            default -> Optional.empty();
        };
    }

    public static MultiblockPreviewSnapshot build(Level level, BlockPos controllerPos, BlockArray rotatedPattern, int maxEntries) {
        if (level == null || rotatedPattern == null || rotatedPattern.isEmpty() || maxEntries <= 0) {
            return new MultiblockPreviewSnapshot(level == null ? Level.OVERWORLD : level.dimension(), controllerPos, List.of());
        }
        List<MultiblockPreviewSnapshot.Entry> entries = new ArrayList<>();
        for (var entry : rotatedPattern.pattern().entrySet()) {
            if (entries.size() >= maxEntries) break;
            BlockPos relativePos = entry.getKey();
            BlockPos worldPos = controllerPos.offset(relativePos);
            if (!level.getBlockState(worldPos).isAir()) continue;
            previewState(entry.getValue()).ifPresent(state -> entries.add(new MultiblockPreviewSnapshot.Entry(relativePos, state)));
        }
        return new MultiblockPreviewSnapshot(level.dimension(), controllerPos, entries);
    }
}

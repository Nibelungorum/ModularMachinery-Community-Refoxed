package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.machine.PatternDefinition;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;

/**
 * Internal conversion boundary for public startup machine declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicMachineAdapter {
    private PublicMachineAdapter() {
    }

    public static cn.howxu.mmcr.api.machine.BlockArray toBlockArray(PatternDefinition pattern) {
        LinkedHashMap<BlockPos, cn.howxu.mmcr.api.machine.BlockPredicate> entries = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, Character> symbolsByPosition = new LinkedHashMap<>();
        BlockPos controller = null;
        int xOrigin = pattern.width() / 2;
        int yOrigin = pattern.height() / 2;
        int zOrigin = pattern.depth() / 2;

        for (int rowIndex = 0; rowIndex < pattern.height(); rowIndex++) {
            int y = rowIndex - yOrigin;
            for (int layerIndex = 0; layerIndex < pattern.depth(); layerIndex++) {
                int z = layerIndex - zOrigin;
                String row = pattern.layers().get(layerIndex).get(rowIndex);
                for (int columnIndex = 0; columnIndex < pattern.width(); columnIndex++) {
                    char symbol = row.charAt(columnIndex);
                    if (symbol == ' ') continue;
                    int x = columnIndex - xOrigin;
                    BlockPos pos = new BlockPos(x, y, z);
                    entries.put(pos, toBlockPredicate(pattern.predicates().get(symbol)));
                    symbolsByPosition.put(pos, symbol);
                    if (symbol == pattern.controllerSymbol()) controller = pos;
                }
            }
        }

        if (controller != null && !controller.equals(BlockPos.ZERO)) {
            LinkedHashMap<BlockPos, cn.howxu.mmcr.api.machine.BlockPredicate> normalized = new LinkedHashMap<>();
            LinkedHashMap<BlockPos, Character> normalizedSymbols = new LinkedHashMap<>();
            for (var entry : entries.entrySet()) {
                BlockPos normalizedPos = entry.getKey().subtract(controller);
                normalized.put(normalizedPos, entry.getValue());
                normalizedSymbols.put(normalizedPos, symbolsByPosition.get(entry.getKey()));
            }
            entries = normalized;
            symbolsByPosition = normalizedSymbols;
        }
        return new cn.howxu.mmcr.api.machine.BlockArray(entries, java.util.Map.of(), symbolsByPosition);
    }

    private static cn.howxu.mmcr.api.machine.BlockPredicate toBlockPredicate(
            cn.howxu.mmcr.api.publicapi.machine.BlockPredicate predicate) {
        return predicate.block()
                .<cn.howxu.mmcr.api.machine.BlockPredicate>map(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock::new)
                .or(() -> predicate.tag().map(cn.howxu.mmcr.api.machine.BlockPredicate.OfTag::new))
                .orElseGet(() -> new cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf(
                        predicate.alternatives().stream()
                                .map(PublicMachineAdapter::toBlockPredicate)
                                .toList()));
    }
}

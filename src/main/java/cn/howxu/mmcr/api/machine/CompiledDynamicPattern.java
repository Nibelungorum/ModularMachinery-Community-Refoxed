package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-facing cache for a dynamic-length structure segment.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CompiledDynamicPattern(
        DynamicPatternSpec spec,
        Map<Direction, BlockArray> startPatterns,
        Map<Direction, BlockArray> endPatterns,
        Map<Direction, BlockPos> offsetStarts,
        Map<Direction, BlockPos> structureSizeOffsets,
        Map<Direction, List<Direction>> allowedFaces
) {

    public CompiledDynamicPattern {
        if (spec == null) throw new IllegalArgumentException("spec null");
        startPatterns = copyEnumMap(startPatterns);
        endPatterns = copyEnumMap(endPatterns);
        offsetStarts = copyEnumMap(offsetStarts);
        structureSizeOffsets = copyEnumMap(structureSizeOffsets);
        allowedFaces = copyListEnumMap(allowedFaces);
    }

    public BlockArray startPattern(Direction facing) {
        return startPatterns.get(facing);
    }

    public @Nullable BlockArray endPattern(Direction facing) {
        return endPatterns.get(facing);
    }

    public BlockPos offsetStart(Direction facing) {
        return offsetStarts.get(facing);
    }

    public BlockPos structureSizeOffset(Direction facing) {
        return structureSizeOffsets.get(facing);
    }

    public List<Direction> allowedFaces(Direction facing) {
        return allowedFaces.getOrDefault(facing, List.of());
    }

    private static <T> Map<Direction, T> copyEnumMap(Map<Direction, T> source) {
        EnumMap<Direction, T> copy = new EnumMap<>(Direction.class);
        if (source != null) copy.putAll(source);
        return Map.copyOf(copy);
    }

    private static Map<Direction, List<Direction>> copyListEnumMap(Map<Direction, List<Direction>> source) {
        EnumMap<Direction, List<Direction>> copy = new EnumMap<>(Direction.class);
        if (source != null) {
            for (var entry : source.entrySet()) copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}

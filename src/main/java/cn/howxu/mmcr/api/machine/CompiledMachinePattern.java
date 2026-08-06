package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable per-machine structure data derived from a raw south-facing pattern.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CompiledMachinePattern(
        Machine machine,
        Map<Direction, BlockArray> rotatedPatterns,
        Map<Direction, BoundingBox> boundingBoxes,
        Map<Direction, List<BlockPos>> componentPositions,
        Map<Direction, List<BlockPos>> portPositions,
        List<CompiledDynamicPattern> dynamicPatterns
) {

    public CompiledMachinePattern {
        if (machine == null) throw new IllegalArgumentException("machine null");
        rotatedPatterns = copyEnumMap(rotatedPatterns);
        boundingBoxes = copyEnumMap(boundingBoxes);
        componentPositions = copyListEnumMap(componentPositions);
        portPositions = copyListEnumMap(portPositions);
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions) {
        this(machine, rotatedPatterns, boundingBoxes, componentPositions, portPositions, List.of());
    }

    public BlockArray rotatedPattern(Direction facing) {
        return rotatedPatterns.get(facing);
    }

    public BoundingBox boundingBox(Direction facing) {
        return boundingBoxes.get(facing);
    }

    public List<BlockPos> componentPositions(Direction facing) {
        return componentPositions.getOrDefault(facing, List.of());
    }

    public List<BlockPos> portPositions(Direction facing) {
        return portPositions.getOrDefault(facing, List.of());
    }

    private static <T> Map<Direction, T> copyEnumMap(Map<Direction, T> source) {
        EnumMap<Direction, T> copy = new EnumMap<>(Direction.class);
        if (source != null) copy.putAll(source);
        return Map.copyOf(copy);
    }

    private static Map<Direction, List<BlockPos>> copyListEnumMap(Map<Direction, List<BlockPos>> source) {
        EnumMap<Direction, List<BlockPos>> copy = new EnumMap<>(Direction.class);
        if (source != null) {
            for (var entry : source.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(copy);
    }
}

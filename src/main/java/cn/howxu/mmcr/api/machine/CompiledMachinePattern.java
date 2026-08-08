package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        List<CompiledDynamicPattern> dynamicPatterns,
        Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> modifierReplacements
) {

    public CompiledMachinePattern {
        if (machine == null) throw new IllegalArgumentException("machine null");
        rotatedPatterns = copyEnumMap(rotatedPatterns);
        boundingBoxes = copyEnumMap(boundingBoxes);
        componentPositions = copyListEnumMap(componentPositions);
        portPositions = copyListEnumMap(portPositions);
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
        modifierReplacements = copyModifierReplacementEnumMap(modifierReplacements);
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions) {
        this(machine, rotatedPatterns, boundingBoxes, componentPositions, portPositions, List.of(), Map.of());
    }

    public CompiledMachinePattern(
            Machine machine,
            Map<Direction, BlockArray> rotatedPatterns,
            Map<Direction, BoundingBox> boundingBoxes,
            Map<Direction, List<BlockPos>> componentPositions,
            Map<Direction, List<BlockPos>> portPositions,
            List<CompiledDynamicPattern> dynamicPatterns) {
        this(machine, rotatedPatterns, boundingBoxes, componentPositions, portPositions, dynamicPatterns, Map.of());
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

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(Direction facing) {
        return modifierReplacements.getOrDefault(facing, Map.of());
    }

    public Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements(
        Direction facing, Direction rollFacing) {
        if (!facing.getAxis().isVertical()) return modifierReplacements(facing);
        return machine instanceof DynamicMachine dynamic
                ? dynamic.rotatedModifierReplacements(facing, rollFacing)
                : Map.of();
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

    private static Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> copyModifierReplacementEnumMap(
            Map<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> source) {
        EnumMap<Direction, Map<BlockPos, List<SingleBlockModifierReplacement>>> copy = new EnumMap<>(Direction.class);
        if (source != null) {
            for (var entry : source.entrySet()) {
                LinkedHashMap<BlockPos, List<SingleBlockModifierReplacement>> positionCopy = new LinkedHashMap<>();
                for (var positionEntry : entry.getValue().entrySet()) {
                    positionCopy.put(positionEntry.getKey(), List.copyOf(positionEntry.getValue()));
                }
                copy.put(entry.getKey(), java.util.Collections.unmodifiableMap(positionCopy));
            }
        }
        return Map.copyOf(copy);
    }
}

package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * Immutable full snapshot of one publicly visible structure stage.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureStage(
        int number,
        BlockArray pattern,
        PortRequirementSpec portRequirements,
        PortTierRequirementSpec portTierRequirements,
        List<DynamicPatternSpec> dynamicPatterns,
        MachineStructureRequirements requirements,
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
        Map<BlockPos, Identifier> levelSlots) {

    public MachineStructureStage(int number, BlockArray pattern, PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements, List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
            Map<BlockPos, Identifier> levelSlots) {
        this(number, pattern, portRequirements, portTierRequirements, dynamicPatterns,
                MachineStructureRequirements.EMPTY, modifierReplacements, levelSlots);
    }

    public MachineStructureStage {
        if (number < 1) throw new IllegalArgumentException("stage number must be positive");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(portRequirements, "portRequirements");
        Objects.requireNonNull(portTierRequirements, "portTierRequirements");
        pattern = new BlockArray(pattern.pattern(), copyNestedMap(pattern.tagsByPosition()), pattern.symbolsByPosition());
        dynamicPatterns = List.copyOf(dynamicPatterns);
        requirements = (requirements == null ? MachineStructureRequirements.EMPTY : requirements).validate(pattern);
        MachineStructureRequirementCompiler.Compiled compiled =
                MachineStructureRequirementCompiler.compile(pattern, requirements);
        modifierReplacements = mergeNestedMaps(modifierReplacements, compiled.modifierReplacements());
        levelSlots = Collections.unmodifiableMap(mergeMaps(levelSlots, compiled.levelSlots()));
    }

    private static <T> Map<BlockPos, List<T>> copyNestedMap(Map<BlockPos, List<T>> source) {
        Map<BlockPos, List<T>> copy = new LinkedHashMap<>();
        source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<BlockPos, List<SingleBlockModifierReplacement>> mergeNestedMaps(
            Map<BlockPos, List<SingleBlockModifierReplacement>> explicit,
            Map<BlockPos, List<SingleBlockModifierReplacement>> compiled) {
        Map<BlockPos, List<SingleBlockModifierReplacement>> merged = new LinkedHashMap<>();
        if (explicit != null) explicit.forEach((position, values) -> merged.put(position, List.copyOf(values)));
        compiled.forEach((position, values) -> merged.merge(position, List.copyOf(values), (left, right) -> {
            java.util.ArrayList<SingleBlockModifierReplacement> combined = new java.util.ArrayList<>(left);
            combined.addAll(right);
            return List.copyOf(combined);
        }));
        return Collections.unmodifiableMap(merged);
    }

    private static <T> Map<BlockPos, T> mergeMaps(Map<BlockPos, T> explicit, Map<BlockPos, T> compiled) {
        Map<BlockPos, T> merged = new LinkedHashMap<>();
        if (explicit != null) merged.putAll(explicit);
        compiled.forEach((position, value) -> {
            T existing = merged.putIfAbsent(position, value);
            if (existing != null && !existing.equals(value)) {
                throw new IllegalArgumentException("conflicting compiled requirement at " + position);
            }
        });
        return merged;
    }
}

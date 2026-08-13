package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
        Map<BlockPos, Identifier> levelSlots) {

    public MachineStructureStage {
        if (number < 1) throw new IllegalArgumentException("stage number must be positive");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(portRequirements, "portRequirements");
        Objects.requireNonNull(portTierRequirements, "portTierRequirements");
        pattern = new BlockArray(Map.copyOf(pattern.pattern()), copyNestedMap(pattern.tagsByPosition()));
        dynamicPatterns = List.copyOf(dynamicPatterns);
        modifierReplacements = copyNestedMap(modifierReplacements);
        levelSlots = Map.copyOf(levelSlots);
    }

    private static <T> Map<BlockPos, List<T>> copyNestedMap(Map<BlockPos, List<T>> source) {
        Map<BlockPos, List<T>> copy = new LinkedHashMap<>();
        source.forEach((position, values) -> copy.put(position, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}

package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Server-reloadable structure data for an already registered startup machine.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineStructureDefinition(
        Identifier machineId,
        BlockArray pattern,
        PortRequirementSpec portRequirements,
        PortTierRequirementSpec portTierRequirements,
        List<DynamicPatternSpec> dynamicPatterns,
        Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements,
        Map<BlockPos, Identifier> levelSlots
) {
    public MachineStructureDefinition {
        if (machineId == null) throw new IllegalArgumentException("machineId null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        portRequirements = portRequirements == null ? PortRequirementSpec.none() : portRequirements;
        portTierRequirements = portTierRequirements == null ? PortTierRequirementSpec.none() : portTierRequirements;
        dynamicPatterns = List.copyOf(dynamicPatterns == null ? List.of() : dynamicPatterns);
        modifierReplacements = modifierReplacements == null ? Map.of() : Map.copyOf(modifierReplacements);
        levelSlots = levelSlots == null ? Map.of() : Map.copyOf(levelSlots);
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(machineId, pattern, portRequirements, PortTierRequirementSpec.none(), dynamicPatterns, modifierReplacements, Map.of());
    }

    public MachineStructureDefinition(
            Identifier machineId,
            BlockArray pattern,
            PortRequirementSpec portRequirements,
            PortTierRequirementSpec portTierRequirements,
            List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements) {
        this(machineId, pattern, portRequirements, portTierRequirements, dynamicPatterns, modifierReplacements, Map.of());
    }
}

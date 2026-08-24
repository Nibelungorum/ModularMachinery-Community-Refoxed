package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable aggregate view published by a machine controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRuntimeSnapshot(
        StructureSnapshot structure,
        List<ProcessingComponent> components,
        List<MachineCapability> capabilities,
        long capabilityVersion,
        Map<String, List<RecipeModifier>> foundModifiers,
        Map<Identifier, MachineLevel> foundLevels,
        Set<BlockPos> linkedPortPositions,
        FactorySnapshot factory) {

    public ControllerRuntimeSnapshot {
        structure = structure == null ? StructureSnapshot.empty() : structure;
        components = List.copyOf(components == null ? List.of() : components);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        Map<String, List<RecipeModifier>> modifierCopy = new LinkedHashMap<>();
        if (foundModifiers != null) {
            foundModifiers.forEach((key, value) -> modifierCopy.put(key, List.copyOf(value == null ? List.of() : value)));
        }
        foundModifiers = Map.copyOf(modifierCopy);
        foundLevels = Map.copyOf(foundLevels == null ? Map.of() : foundLevels);
        linkedPortPositions = Set.copyOf(linkedPortPositions == null ? Set.of() : linkedPortPositions);
        factory = factory == null ? FactorySnapshot.empty() : factory;
    }
}

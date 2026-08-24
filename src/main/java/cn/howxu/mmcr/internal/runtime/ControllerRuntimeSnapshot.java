package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
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
        long modifierVersion,
        long stateVersion,
        Map<String, List<RecipeModifier>> foundModifiers,
        Map<Identifier, MachineLevel> foundLevels,
        Set<BlockPos> linkedPortPositions,
        ModuleConnectionStatus moduleConnectionStatus,
        int installedModuleCount,
        ComponentRuntime.CapabilityAggregate capabilityAggregate,
        CraftingStateSnapshot crafting,
        FactorySnapshot factory) {

    public ControllerRuntimeSnapshot {
        structure = structure == null ? StructureSnapshot.empty() : structure;
        components = List.copyOf(components == null ? List.of() : components);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        Map<String, List<RecipeModifier>> modifierCopy = new LinkedHashMap<>();
        if (foundModifiers != null) {
            foundModifiers.forEach((key, value) -> modifierCopy.put(key, List.copyOf(value == null ? List.of() : value)));
        }
        foundModifiers = immutableMap(modifierCopy);
        foundLevels = immutableMap(new LinkedHashMap<>(foundLevels == null ? Map.of() : foundLevels));
        linkedPortPositions = Set.copyOf(linkedPortPositions == null ? Set.of() : linkedPortPositions);
        moduleConnectionStatus = moduleConnectionStatus == null
                ? ModuleConnectionStatus.disconnected() : moduleConnectionStatus;
        if (installedModuleCount < 0) throw new IllegalArgumentException("installedModuleCount must not be negative");
        capabilityAggregate = capabilityAggregate == null
                ? new ComponentRuntime.CapabilityAggregate(0L, 0L, null, null) : capabilityAggregate;
        crafting = crafting == null ? CraftingStateSnapshot.empty(structure.version(), capabilityVersion, modifierVersion) : crafting;
        factory = factory == null ? FactorySnapshot.empty() : factory;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public long totalStoredEnergy() {
        return capabilityAggregate.storedEnergy();
    }

    public long totalCapacityEnergy() {
        return capabilityAggregate.energyCapacity();
    }

    public FluidStack primaryFluid() {
        return capabilityAggregate.primaryFluid();
    }

    public FluidStack primaryOutputFluid() {
        return capabilityAggregate.primaryOutputFluid();
    }
}

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
        crafting = crafting == null ? CraftingStateSnapshot.empty(structure.version(), capabilityVersion, modifierVersion) : crafting;
        factory = factory == null ? FactorySnapshot.empty() : factory;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public long totalStoredEnergy() {
        return capabilityAggregate().storedEnergy();
    }

    public long totalCapacityEnergy() {
        return capabilityAggregate().energyCapacity();
    }

    public FluidStack primaryFluid() {
        return capabilityAggregate().primaryFluid();
    }

    public FluidStack primaryOutputFluid() {
        return capabilityAggregate().primaryOutputFluid();
    }

    private ComponentRuntime.CapabilityAggregate capabilityAggregate() {
        long storedEnergy = 0L;
        long energyCapacity = 0L;
        FluidStack primaryFluid = FluidStack.EMPTY;
        FluidStack primaryOutputFluid = FluidStack.EMPTY;
        for (MachineCapability capability : capabilities) {
            if (capability.storage() instanceof cn.howxu.mmcr.api.capability.storage.LongValueStorage energy) {
                storedEnergy += energy.amount();
                energyCapacity += energy.capacity();
            } else if (capability.storage() instanceof cn.howxu.mmcr.api.capability.storage.ResourceStorage<?> resourceStorage
                    && resourceStorage.resourceType() == net.neoforged.neoforge.transfer.fluid.FluidResource.class) {
                for (int slot = 0; slot < resourceStorage.size(); slot++) {
                    Object resource = resourceStorage.resource(slot);
                    if (!(resource instanceof net.neoforged.neoforge.transfer.fluid.FluidResource fluidResource)
                            || fluidResource.isEmpty()) continue;
                    FluidStack stack = fluidResource.toStack((int) Math.min(resourceStorage.amount(slot), Integer.MAX_VALUE));
                    if (stack.isEmpty()) continue;
                    if (capability.ioType() == cn.howxu.mmcr.util.IOType.INPUT && primaryFluid.isEmpty()) {
                        primaryFluid = stack;
                    } else if (capability.ioType() == cn.howxu.mmcr.util.IOType.OUTPUT && primaryOutputFluid.isEmpty()) {
                        primaryOutputFluid = stack;
                    }
                }
            }
        }
        return new ComponentRuntime.CapabilityAggregate(storedEnergy, energyCapacity, primaryFluid, primaryOutputFluid);
    }
}

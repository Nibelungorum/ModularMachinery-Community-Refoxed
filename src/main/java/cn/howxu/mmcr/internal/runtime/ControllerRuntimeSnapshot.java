package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

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
        FactorySnapshot factory,
        List<ComponentPresentation> componentPresentations,
        List<CapabilityPresentation> capabilityPresentations,
        List<String> foundLevelIds,
        String machineId,
        String machineName,
        int controllerRole,
        boolean factorySupported,
        boolean factoryControllerPresent,
        int parallelControllerCount,
        long maxParallelControllerCount,
        long maxParallelism) {

    public ControllerRuntimeSnapshot {
        structure = structure == null ? StructureSnapshot.empty() : structure;
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
        componentPresentations = List.copyOf(componentPresentations == null ? List.of() : componentPresentations);
        capabilityPresentations = List.copyOf(capabilityPresentations == null ? List.of() : capabilityPresentations);
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        machineId = machineId == null ? "" : machineId;
        machineName = machineName == null ? "" : machineName;
        if (controllerRole < 0 || parallelControllerCount < 0 || maxParallelControllerCount < 0 || maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid controller presentation values");
        }
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

    /**
     * Immutable component identity captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record ComponentPresentation(BlockPos position, @Nullable String kindId,
                                        @Nullable IOType ioType, List<String> tags) {
        public ComponentPresentation {
            position = position == null ? BlockPos.ZERO : position.immutable();
            tags = List.copyOf(tags == null ? List.of() : tags);
        }
    }

    /**
     * Immutable capability values captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record CapabilityPresentation(@Nullable Identifier typeId, @Nullable IOType ioType,
                                         long amount, long capacity, List<StorageSlot> slots) {
        public CapabilityPresentation {
            if (amount < 0L || capacity < 0L) throw new IllegalArgumentException("Capability values must not be negative");
            slots = List.copyOf(slots == null ? List.of() : slots);
        }
    }

    /**
     * Immutable storage slot values captured when this runtime snapshot was published.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record StorageSlot(String resourceId, long amount, long capacity) {
        public StorageSlot {
            resourceId = resourceId == null ? "" : resourceId;
            if (amount < 0L || capacity < 0L) throw new IllegalArgumentException("Storage values must not be negative");
        }
    }
}

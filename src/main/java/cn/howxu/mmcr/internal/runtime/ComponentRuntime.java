package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionCoordinator;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the effective component, modifier, level, link, and capability views of a controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentRuntime {
    private List<ProcessingComponent> components = List.of();
    private List<MachineCapability> capabilities = List.of();
    private long capabilityVersion;
    private long modifierVersion;
    private long craftingStateVersion;
    private Map<String, List<RecipeModifier>> foundModifiers = Map.of();
    private Map<Identifier, MachineLevel> foundLevels = Map.of();
    private Set<BlockPos> linkedPortPositions = Set.of();
    private ModuleConnectionStatus moduleConnectionStatus = ModuleConnectionStatus.disconnected();
    private int installedModuleCount;

    public void replaceComponents(List<ProcessingComponent> components) {
        List<ProcessingComponent> nextComponents = List.copyOf(components == null ? List.of() : components);
        List<MachineCapability> nextCapabilities = capabilitiesFor(nextComponents);
        boolean capabilitiesChanged = !this.capabilities.equals(nextCapabilities);
        this.components = nextComponents;
        if (!capabilitiesChanged) return;
        this.capabilities = nextCapabilities;
        capabilityVersion++;
        craftingStateVersion++;
    }

    public List<ProcessingComponent> components() {
        return components;
    }

    public List<MachineCapability> capabilities() {
        return capabilities;
    }

    public long capabilityVersion() {
        return capabilityVersion;
    }

    public long modifierVersion() {
        return modifierVersion;
    }

    public long craftingStateVersion() {
        return craftingStateVersion;
    }

    public void replaceModifiers(Map<String, List<RecipeModifier>> modifiers) {
        Map<String, List<RecipeModifier>> next = new LinkedHashMap<>();
        if (modifiers != null) {
            modifiers.forEach((key, value) -> next.put(key, List.copyOf(value == null ? List.of() : value)));
        }
        if (foundModifiers.equals(next)) return;
        foundModifiers = Collections.unmodifiableMap(next);
        modifierVersion++;
        craftingStateVersion++;
    }

    public Map<String, List<RecipeModifier>> foundModifiers() {
        return foundModifiers;
    }

    public List<RecipeModifier> modifierList() {
        return foundModifiers.values().stream().flatMap(List::stream).toList();
    }

    public void replaceLevels(Map<Identifier, MachineLevel> levels) {
        Map<Identifier, MachineLevel> next = new LinkedHashMap<>(levels == null ? Map.of() : levels);
        foundLevels = Collections.unmodifiableMap(next);
    }

    public Map<Identifier, MachineLevel> foundLevels() {
        return foundLevels;
    }

    public void replaceLinkedPortPositions(Set<BlockPos> positions) {
        linkedPortPositions = Set.copyOf(positions == null ? Set.of() : positions);
    }

    public Set<BlockPos> linkedPortPositions() {
        return linkedPortPositions;
    }

    public boolean hasLinkedPort(BlockPos position) {
        return position != null && linkedPortPositions.contains(position);
    }

    public void refreshModuleConnectionState(MachineControllerBlockEntity controller) {
        moduleConnectionStatus = ModuleConnectionCoordinator.connectionStatus(controller);
        installedModuleCount = ModuleConnectionCoordinator.installedModuleCount(controller);
    }

    public ModuleConnectionStatus moduleConnectionStatus() {
        return moduleConnectionStatus;
    }

    public int installedModuleCount() {
        return installedModuleCount;
    }

    public Optional<Identifier> connectedHostId() {
        return moduleConnectionStatus.connected()
                ? Optional.of(moduleConnectionStatus.connectedHostId())
                : Optional.empty();
    }

    public CapabilityAggregate capabilityAggregate() {
        long storedEnergy = 0L;
        long energyCapacity = 0L;
        FluidStack primaryFluid = FluidStack.EMPTY;
        FluidStack primaryOutputFluid = FluidStack.EMPTY;
        for (MachineCapability capability : capabilities) {
            if (capability.storage() instanceof LongValueStorage energy) {
                storedEnergy += energy.amount();
                energyCapacity += energy.capacity();
            } else if (capability.storage() instanceof ResourceStorage<?> resourceStorage
                    && resourceStorage.resourceType() == FluidResource.class) {
                for (int slot = 0; slot < resourceStorage.size(); slot++) {
                    Object resource = resourceStorage.resource(slot);
                    if (!(resource instanceof FluidResource fluidResource) || fluidResource.isEmpty()) continue;
                    FluidStack stack = fluidResource.toStack((int) Math.min(resourceStorage.amount(slot), Integer.MAX_VALUE));
                    if (stack.isEmpty()) continue;
                    if (capability.ioType() == IOType.INPUT && primaryFluid.isEmpty()) {
                        primaryFluid = stack;
                    } else if (capability.ioType() == IOType.OUTPUT && primaryOutputFluid.isEmpty()) {
                        primaryOutputFluid = stack;
                    }
                }
            }
        }
        return new CapabilityAggregate(storedEnergy, energyCapacity, primaryFluid, primaryOutputFluid);
    }

    public int maxParallelism(Machine machine) {
        if (machine == null || !machine.parallelizable()) return 1;
        long max = 0L;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity parallel) {
                max += parallel.currentParallelism();
            }
        }
        long levelBonus = foundLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToLong(foundLevel -> foundLevel.modifier().parallelismBonus())
                .sum();
        long effective = Math.max(1L, max) + levelBonus;
        long bounded = Math.min(Integer.MAX_VALUE, Math.max(1L, effective));
        return (int) Math.min(Math.max(1, machine.maxParallelism()), bounded);
    }

    public void clear() {
        replaceComponents(List.of());
        replaceModifiers(Map.of());
        replaceLevels(Map.of());
        replaceLinkedPortPositions(Set.of());
        moduleConnectionStatus = ModuleConnectionStatus.disconnected();
        installedModuleCount = 0;
    }

    private static List<MachineCapability> capabilitiesFor(List<ProcessingComponent> components) {
        List<MachineCapability> result = new ArrayList<>();
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof CapabilityHost host) {
                try {
                    result.addAll(host.capabilitySnapshot().capabilities());
                } catch (RuntimeException ignored) {
                    // A partially initialized port must not invalidate the controller runtime snapshot.
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Immutable capability-level aggregate used by controller presentation callers.
     */
    public record CapabilityAggregate(long storedEnergy, long energyCapacity,
                                      FluidStack primaryFluid, FluidStack primaryOutputFluid) {
        public CapabilityAggregate {
            primaryFluid = primaryFluid == null ? FluidStack.EMPTY : primaryFluid.copy();
            primaryOutputFluid = primaryOutputFluid == null ? FluidStack.EMPTY : primaryOutputFluid.copy();
        }

        @Override
        public FluidStack primaryFluid() {
            return primaryFluid.copy();
        }

        @Override
        public FluidStack primaryOutputFluid() {
            return primaryOutputFluid.copy();
        }
    }
}

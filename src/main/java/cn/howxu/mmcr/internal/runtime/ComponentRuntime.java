package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the effective component, modifier, level, link, module, and capability state of a controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentRuntime {
    private List<ProcessingComponent> components = List.of();
    private List<MachineCapability> capabilities = List.of();
    private List<CapabilityIdentity> capabilityIdentity = List.of();
    private CapabilityAggregate capabilityAggregate = new CapabilityAggregate(0L, 0L, null, null);
    private long capabilityVersion;
    private long modifierVersion;
    private long stateVersion;
    private Map<String, List<RecipeModifier>> foundModifiers = Map.of();
    private Map<Identifier, MachineLevel> foundLevels = Map.of();
    private Set<BlockPos> linkedPortPositions = Set.of();
    private ModuleConnectionStatus moduleConnectionStatus = ModuleConnectionStatus.disconnected();
    private int installedModuleCount;

    public void replaceComponents(List<ProcessingComponent> components) {
        List<ProcessingComponent> nextComponents = List.copyOf(components == null ? List.of() : components);
        CapabilityState capabilityState = capabilityStateFor(nextComponents);
        List<MachineCapability> nextCapabilities = capabilityState.capabilities();
        List<CapabilityIdentity> nextIdentity = capabilityState.identity();
        boolean componentsChanged = !this.components.equals(nextComponents);
        boolean capabilitiesChanged = !capabilityIdentity.equals(nextIdentity);
        this.components = nextComponents;
        if (componentsChanged) stateVersion++;
        this.capabilities = nextCapabilities;
        this.capabilityAggregate = capabilityAggregate(nextCapabilities);
        if (!capabilitiesChanged) return;
        this.capabilityIdentity = nextIdentity;
        capabilityVersion++;
    }

    public List<ProcessingComponent> components() {
        return components;
    }

    public List<MachineCapability> capabilities() {
        return capabilities;
    }

    public List<ControllerRuntimeSnapshot.ComponentPresentation> componentPresentations() {
        List<ControllerRuntimeSnapshot.ComponentPresentation> snapshots = new ArrayList<>(components.size());
        for (ProcessingComponent component : components) {
            MachineComponent machineComponent = component.getComponent();
            snapshots.add(new ControllerRuntimeSnapshot.ComponentPresentation(
                    component.getPos(),
                    machineComponent == null || machineComponent.kind() == null ? null : machineComponent.kind().id(),
                    machineComponent == null ? null : machineComponent.ioType(),
                    component.tags()));
        }
        return List.copyOf(snapshots);
    }

    public List<ControllerRuntimeSnapshot.CapabilityPresentation> capabilityPresentations() {
        List<ControllerRuntimeSnapshot.CapabilityPresentation> snapshots = new ArrayList<>(capabilities.size());
        for (MachineCapability capability : capabilities) {
            CapabilityStorage storage = capability.storage();
            if (storage instanceof LongValueStorage value) {
                snapshots.add(new ControllerRuntimeSnapshot.CapabilityPresentation(
                        capability.type() == null ? null : capability.type().id(), capability.ioType(),
                        value.amount(), value.capacity(), List.of()));
            } else if (storage instanceof ResourceStorage<?> resourceStorage) {
                snapshots.add(resourcePresentation(capability, resourceStorage));
            } else {
                snapshots.add(new ControllerRuntimeSnapshot.CapabilityPresentation(
                        capability.type() == null ? null : capability.type().id(), capability.ioType(), 0L, 0L, List.of()));
            }
        }
        return List.copyOf(snapshots);
    }

    public long capabilityVersion() {
        return capabilityVersion;
    }

    public long modifierVersion() {
        return modifierVersion;
    }

    public long stateVersion() {
        return stateVersion;
    }

    public void replaceModifiers(Map<String, List<RecipeModifier>> modifiers) {
        Map<String, List<RecipeModifier>> next = new LinkedHashMap<>();
        if (modifiers != null) {
            modifiers.forEach((key, value) -> next.put(key, List.copyOf(value == null ? List.of() : value)));
        }
        if (foundModifiers.size() == next.size()) {
            var current = foundModifiers.entrySet().iterator();
            var candidate = next.entrySet().iterator();
            boolean orderedEqual = true;
            while (current.hasNext()) {
                if (!Objects.equals(current.next(), candidate.next())) {
                    orderedEqual = false;
                    break;
                }
            }
            if (orderedEqual) return;
        }
        foundModifiers = immutableMap(next);
        modifierVersion++;
        stateVersion++;
    }

    public Map<String, List<RecipeModifier>> foundModifiers() {
        return foundModifiers;
    }

    public List<RecipeModifier> modifierList() {
        return foundModifiers.values().stream().flatMap(List::stream).toList();
    }

    public void replaceLevels(Map<Identifier, MachineLevel> levels) {
        Map<Identifier, MachineLevel> next = new LinkedHashMap<>(levels == null ? Map.of() : levels);
        if (foundLevels.equals(next)) return;
        foundLevels = immutableMap(next);
        stateVersion++;
    }

    public Map<Identifier, MachineLevel> foundLevels() {
        return foundLevels;
    }

    public void replaceLinkedPortPositions(Set<BlockPos> positions) {
        Set<BlockPos> next = Set.copyOf(positions == null ? Set.of() : positions);
        if (linkedPortPositions.equals(next)) return;
        linkedPortPositions = next;
        stateVersion++;
    }

    public Set<BlockPos> linkedPortPositions() {
        return linkedPortPositions;
    }

    public boolean hasLinkedPort(BlockPos position) {
        return position != null && linkedPortPositions.contains(position);
    }

    public void replaceModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        if (status == null) status = ModuleConnectionStatus.disconnected();
        if (installedModuleCount < 0) throw new IllegalArgumentException("installedModuleCount must not be negative");
        if (moduleConnectionStatus.equals(status) && this.installedModuleCount == installedModuleCount) return;
        moduleConnectionStatus = status;
        this.installedModuleCount = installedModuleCount;
        stateVersion++;
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
        return capabilityAggregate;
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
        replaceModuleConnectionState(ModuleConnectionStatus.disconnected(), 0);
    }

    private static CapabilityState capabilityStateFor(List<ProcessingComponent> components) {
        List<MachineCapability> result = new ArrayList<>();
        List<CapabilityIdentity> identities = new ArrayList<>();
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof CapabilityHost host) {
                try {
                    for (MachineCapability capability : host.capabilitySnapshot().capabilities()) {
                        identities.add(CapabilityIdentity.of(component.getPos(), capability));
                        result.add(capability);
                    }
                } catch (RuntimeException ignored) {
                    // A partially initialized port must not invalidate the controller runtime snapshot.
                }
            }
        }
        return new CapabilityState(List.copyOf(result), List.copyOf(identities));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static ControllerRuntimeSnapshot.CapabilityPresentation resourcePresentation(
            MachineCapability capability, ResourceStorage<?> storage) {
        List<ControllerRuntimeSnapshot.StorageSlot> slots = new ArrayList<>(storage.size());
        long amount = 0L;
        long capacity = 0L;
        for (int slot = 0; slot < storage.size(); slot++) {
            Object resource = storage.resource(slot);
            long slotAmount = storage.amount(slot);
            long slotCapacity = storage.capacityResource(slot, resource);
            slots.add(new ControllerRuntimeSnapshot.StorageSlot(String.valueOf(resource), slotAmount, slotCapacity));
            amount += slotAmount;
            capacity += slotCapacity;
        }
        return new ControllerRuntimeSnapshot.CapabilityPresentation(
                capability.type() == null ? null : capability.type().id(), capability.ioType(), amount, capacity, slots);
    }

    private static CapabilityAggregate capabilityAggregate(List<MachineCapability> capabilities) {
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

    private record CapabilityState(List<MachineCapability> capabilities, List<CapabilityIdentity> identity) { }

    private record CapabilityIdentity(BlockPos componentPos, Identifier type, IOType ioType, List<String> tags,
                                      String storageType, Object storageIdentity) {
        private static CapabilityIdentity of(BlockPos componentPos, MachineCapability capability) {
            CapabilityStorage storage = capability.storage();
            return new CapabilityIdentity(componentPos.immutable(), capability.type().id(), capability.ioType(),
                    List.copyOf(capability.view().tags()), storage == null ? "" : storage.getClass().getName(),
                    storageIdentity(storage));
        }

        private static Object storageIdentity(CapabilityStorage storage) {
            return storage;
        }
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

        public FluidStack primaryFluid() {
            return primaryFluid.copy();
        }

        public FluidStack primaryOutputFluid() {
            return primaryOutputFluid.copy();
        }
    }
}

package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
    private static final ExecutionStatus UNSPECIFIED_TICK_OPERATION_FAILURE = new ExecutionStatus(
            Identifier.fromNamespaceAndPath("mmcr", "capability_tick_operation_failure"), StatusSeverity.FAILURE,
            Identifier.fromNamespaceAndPath("mmcr", "capability_tick"),
            Map.of("reason", "operation_failed_without_status"));
    private List<ProcessingComponent> components = List.of();
    private List<MachineCapability> capabilities = List.of();
    private List<CapabilityIdentity> capabilityIdentity = List.of();
    private CapabilityAggregate capabilityAggregate = new CapabilityAggregate(0L, 0L, null, null);
    private long capabilityVersion;
    private long modifierVersion;
    private long stateVersion;
    private long componentPresentationEpoch;
    private long capabilityPresentationEpoch;
    private long levelVersion;
    private long cachedComponentPresentationEpoch = Long.MIN_VALUE;
    private List<ControllerRuntimeSnapshot.ComponentPresentation> cachedComponentPresentations = List.of();
    private long cachedCapabilityPresentationEpoch = Long.MIN_VALUE;
    private List<ControllerRuntimeSnapshot.CapabilityPresentation> cachedCapabilityPresentations = List.of();
    private Map<String, List<RecipeModifier>> foundModifiers = Map.of();
    private List<RecipeModifier> flattenedModifiers = List.of();
    private Map<Identifier, MachineLevel> foundLevels = Map.of();
    private Set<BlockPos> linkedPortPositions = Set.of();
    private ModuleConnectionStatus moduleConnectionStatus = ModuleConnectionStatus.disconnected();
    private int installedModuleCount;
    private List<UpgradeBusSnapshot> upgradeBuses = List.of();
    private List<ItemStack> upgradeItems = List.of();
    private Map<Identifier, Long> upgradeModifierUnits = Map.of();
    private List<RecipeModifier> upgradeModifiers = List.of();
    private long upgradeContentRevision;
    private boolean modifiersAllowed = true;

    public boolean replaceComponents(List<ProcessingComponent> components) {
        List<ProcessingComponent> nextComponents = List.copyOf(components == null ? List.of() : components);
        CapabilityState capabilityState = capabilityStateFor(nextComponents);
        List<MachineCapability> nextCapabilities = capabilityState.capabilities();
        List<CapabilityIdentity> nextIdentity = capabilityState.identity();
        boolean componentsChanged = !this.components.equals(nextComponents);
        boolean capabilitiesChanged = !capabilityIdentity.equals(nextIdentity);
        CapabilityAggregate nextAggregate = capabilityAggregate(nextCapabilities);
        boolean capabilityValuesChanged = !capabilityAggregate.equals(nextAggregate);
        this.components = nextComponents;
        if (componentsChanged) {
            stateVersion++;
            componentPresentationEpoch++;
        }
        this.capabilities = nextCapabilities;
        this.capabilityAggregate = nextAggregate;
        if (capabilitiesChanged || capabilityValuesChanged) capabilityPresentationEpoch++;
        if (capabilitiesChanged) {
            this.capabilityIdentity = nextIdentity;
            capabilityVersion++;
        }
        return componentsChanged;
    }

    public List<ProcessingComponent> components() {
        return components;
    }

    public List<MachineCapability> capabilities() {
        return capabilities;
    }

    /**
     * Plans each tick facet in snapshot order and commits the resulting operations atomically.
     */
    public CapabilityTickResult executeTickPhase(CapabilityTickContext context) {
        Objects.requireNonNull(context, "context");
        List<CapabilityOperation> operations = new ArrayList<>();
        boolean stateChanged = false;
        for (MachineCapability capability : context.capabilitySnapshot().capabilities()) {
            TickFacet facet = capability.facet(TickFacet.class).orElse(null);
            if (facet == null) continue;
            CapabilityTickResult result = Objects.requireNonNull(facet.plan(context), "tick facet result");
            operations.addAll(result.operations());
            stateChanged |= result.stateChanged();
            if (result.failure() != null) {
                return new CapabilityTickResult(operations, result.failure(), stateChanged);
            }
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (CapabilityOperation operation : operations) {
                CapabilityResult result = operation.commit(transaction);
                if (result == null || !result.success()) {
                    ExecutionStatus failure = result == null || result.status() == null
                            ? UNSPECIFIED_TICK_OPERATION_FAILURE : result.status();
                    return new CapabilityTickResult(operations, failure, stateChanged);
                }
            }
            transaction.commit();
        }
        if (stateChanged) markCapabilityPresentationChanged();
        return new CapabilityTickResult(operations, null, stateChanged);
    }

    public List<ControllerRuntimeSnapshot.ComponentPresentation> componentPresentations() {
        if (cachedComponentPresentationEpoch == componentPresentationEpoch) return cachedComponentPresentations;
        List<ControllerRuntimeSnapshot.ComponentPresentation> snapshots = new ArrayList<>(components.size());
        for (ProcessingComponent component : components) {
            MachineComponent machineComponent = component.getComponent();
            snapshots.add(new ControllerRuntimeSnapshot.ComponentPresentation(
                    component.getPos(),
                    machineComponent == null || machineComponent.kind() == null ? null : machineComponent.kind().id(),
                    machineComponent == null ? null : machineComponent.ioType(),
                    component.tags()));
        }
        cachedComponentPresentations = List.copyOf(snapshots);
        cachedComponentPresentationEpoch = componentPresentationEpoch;
        return cachedComponentPresentations;
    }

    public List<ControllerRuntimeSnapshot.CapabilityPresentation> capabilityPresentations() {
        if (cachedCapabilityPresentationEpoch == capabilityPresentationEpoch) return cachedCapabilityPresentations;
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
        cachedCapabilityPresentations = List.copyOf(snapshots);
        cachedCapabilityPresentationEpoch = capabilityPresentationEpoch;
        return cachedCapabilityPresentations;
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

    public long levelVersion() {
        return levelVersion;
    }

    public long capabilityPresentationEpoch() {
        return capabilityPresentationEpoch;
    }

    public boolean replaceModifiers(Map<String, List<RecipeModifier>> modifiers) {
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
            if (orderedEqual) return false;
        }
        foundModifiers = immutableMap(next);
        rebuildModifierList();
        modifierVersion++;
        stateVersion++;
        return true;
    }

    public Map<String, List<RecipeModifier>> foundModifiers() {
        return foundModifiers;
    }

    public List<RecipeModifier> modifierList() {
        return flattenedModifiers;
    }

    public boolean replaceUpgradeBuses(List<UpgradeBusSnapshot> buses) {
        return replaceUpgradeBuses(buses, false);
    }

    public void refreshUpgradeBuses(List<UpgradeBusSnapshot> buses) {
        replaceUpgradeBuses(buses, true);
    }

    public List<UpgradeBusSnapshot> upgradeBuses() {
        return upgradeBuses;
    }

    public Set<BlockPos> upgradeBusPositions() {
        return upgradeBuses.stream().map(UpgradeBusSnapshot::position).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<ItemStack> upgradeItems() {
        return copyStacks(upgradeItems);
    }

    public Map<Identifier, Long> upgradeModifierUnits() {
        return upgradeModifierUnits;
    }

    public long upgradeContentRevision() {
        return upgradeContentRevision;
    }

    public void setModifiersAllowed(boolean allowed) {
        if (modifiersAllowed == allowed) return;
        modifiersAllowed = allowed;
        rebuildModifierList();
        modifierVersion++;
        stateVersion++;
    }

    public boolean replaceLevels(Map<Identifier, MachineLevel> levels) {
        Map<Identifier, MachineLevel> next = new LinkedHashMap<>(levels == null ? Map.of() : levels);
        if (foundLevels.equals(next)) return false;
        foundLevels = immutableMap(next);
        levelVersion++;
        stateVersion++;
        return true;
    }

    public Map<Identifier, MachineLevel> foundLevels() {
        return foundLevels;
    }

    public boolean replaceLinkedPortPositions(Set<BlockPos> positions) {
        Set<BlockPos> next = Set.copyOf(positions == null ? Set.of() : positions);
        if (linkedPortPositions.equals(next)) return false;
        linkedPortPositions = next;
        stateVersion++;
        return true;
    }

    public Set<BlockPos> linkedPortPositions() {
        return linkedPortPositions;
    }

    public boolean hasLinkedPort(BlockPos position) {
        return position != null && linkedPortPositions.contains(position);
    }

    public boolean replaceModuleConnectionState(ModuleConnectionStatus status, int installedModuleCount) {
        if (status == null) status = ModuleConnectionStatus.disconnected();
        if (installedModuleCount < 0) throw new IllegalArgumentException("installedModuleCount must not be negative");
        if (moduleConnectionStatus.equals(status) && this.installedModuleCount == installedModuleCount) return false;
        moduleConnectionStatus = status;
        this.installedModuleCount = installedModuleCount;
        stateVersion++;
        return true;
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

    public void markCapabilityPresentationChanged() {
        capabilityAggregate = capabilityAggregate(capabilities);
        capabilityPresentationEpoch++;
    }

    public long maxParallelism(Machine machine) {
        if (machine == null || !machine.parallelizable()) {
            return 1L;
        }
        long max = 0L;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity parallel) {
                int current = parallel.currentParallelism();
                max += current;
            }
        }
        long levelBonus = foundLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue)
                .mapToLong(foundLevel -> foundLevel.modifier().parallelismBonus())
                .sum();
        long effective = Math.max(1L, max) + levelBonus;
        long bounded = Math.min(Long.MAX_VALUE, Math.max(1L, effective));
        return Math.min(Math.max(1L, machine.maxParallelism()), bounded);
    }

    public void clear() {
        replaceComponents(List.of());
        replaceModifiers(Map.of());
        replaceLevels(Map.of());
        replaceLinkedPortPositions(Set.of());
        replaceModuleConnectionState(ModuleConnectionStatus.disconnected(), 0);
        replaceUpgradeBuses(List.of());
    }

    private static CapabilityState capabilityStateFor(List<ProcessingComponent> components) {
        List<MachineCapability> result = new ArrayList<>();
        List<CapabilityIdentity> identities = new ArrayList<>();
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof CapabilityHost host) {
                try {
                    for (MachineCapability capability : host.capabilities()) {
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

    private boolean replaceUpgradeBuses(List<UpgradeBusSnapshot> buses, boolean forceRefresh) {
        List<UpgradeBusSnapshot> next = new ArrayList<>();
        if (buses != null) next.addAll(buses);
        next.sort(Comparator.comparing(UpgradeBusSnapshot::position, ComponentRuntime::comparePositions));
        next = List.copyOf(next);
        if (!forceRefresh && upgradeBuses.equals(next)) return false;

        upgradeBuses = next;
        List<ItemStack> items = new ArrayList<>();
        Map<Identifier, Long> units = new LinkedHashMap<>();
        for (UpgradeBusSnapshot bus : next) {
            List<ItemStack> stacks = bus.stacks();
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (stack.isEmpty()) continue;
                items.add(stack.copy());
                Identifier modifierId = ModifierRegistry.modifierFor(stack);
                MMCR.LOG.info("[upgrade-bus-debug] item: busPos={} slot={} stack={} modifierId={} "
                                + "definitionPresent={} count={}",
                        bus.position(), slot, stack, modifierId,
                        modifierId != null && ModifierRegistry.get(modifierId) != null, stack.getCount());
                if (modifierId != null) units.merge(modifierId, (long) stack.getCount(), Long::sum);
            }
        }
        upgradeItems = List.copyOf(items);
        upgradeModifierUnits = immutableMap(units);
        upgradeModifiers = upgradeModifiers(units);
        MMCR.LOG.info("[upgrade-bus-debug] aggregate: busCount={} itemCount={} modifierUnits={} "
                        + "upgradeModifiers={} registeredBindings={}",
                next.size(), items.size(), units, describeModifiers(upgradeModifiers), ModifierRegistry.modifierItems());
        upgradeContentRevision++;
        rebuildModifierList();
        MMCR.LOG.info("[upgrade-bus-debug] rebuilt modifiers: {}", describeModifiers(flattenedModifiers));
        modifierVersion++;
        stateVersion++;
        return true;
    }

    private List<RecipeModifier> upgradeModifiers(Map<Identifier, Long> units) {
        List<RecipeModifier> result = new ArrayList<>();
        for (Map.Entry<Identifier, Long> entry : units.entrySet()) {
            ModifierDefinition definition = ModifierRegistry.get(entry.getKey());
            if (definition == null) continue;
            for (RecipeModifier modifier : definition.modifiers()) {
                result.add(withUnitCount(modifier, entry.getValue()));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> describeModifiers(List<RecipeModifier> modifiers) {
        return modifiers.stream()
                .map(modifier -> modifier.getTarget() + "/" + modifier.getIOTarget() + "/"
                        + modifier.getOperation() + "/" + modifier.getModifier())
                .toList();
    }

    private void rebuildModifierList() {
        if (!modifiersAllowed) {
            flattenedModifiers = List.of();
            return;
        }
        List<RecipeModifier> modifiers = new ArrayList<>();
        foundModifiers.values().forEach(modifiers::addAll);
        modifiers.addAll(upgradeModifiers);
        flattenedModifiers = List.copyOf(modifiers);
    }

    private static RecipeModifier withUnitCount(RecipeModifier modifier, long count) {
        if (count <= 1L) return modifier;
        float value = switch (modifier.getOperation()) {
            case ADD, SUBTRACT -> (float) ((double) modifier.getModifier() * count);
            case MULTIPLY, DIVIDE -> power(modifier.getModifier(), count);
        };
        return new RecipeModifier(modifier.getTarget(), modifier.getIOTarget(), value,
                modifier.getOperation(), modifier.affectsChance());
    }

    private static float power(float base, long exponent) {
        float result = 1F;
        float factor = base;
        long remaining = exponent;
        while (remaining > 0L) {
            if ((remaining & 1L) != 0L) result *= factor;
            remaining >>>= 1;
            if (remaining > 0L) factor *= factor;
        }
        return result;
    }

    private static int comparePositions(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return List.copyOf(stacks.stream().map(ItemStack::copy).toList());
    }

    /** Immutable content snapshot for one bound Upgrade Bus. */
    public record UpgradeBusSnapshot(BlockPos position, List<ItemStack> stacks) {
        public UpgradeBusSnapshot {
            position = position == null ? BlockPos.ZERO : position.immutable();
            stacks = copyStacks(stacks == null ? List.of() : stacks);
        }
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
            String resourceId = resource == null || (resource instanceof Resource empty && empty.isEmpty())
                    ? "" : String.valueOf(resource);
            slots.add(new ControllerRuntimeSnapshot.StorageSlot(resourceId, slotAmount, slotCapacity));
            amount = saturatedAdd(amount, slotAmount);
            capacity = saturatedAdd(capacity, slotCapacity);
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
                storedEnergy = saturatedAdd(storedEnergy, energy.amount());
                energyCapacity = saturatedAdd(energyCapacity, energy.capacity());
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

    private static long saturatedAdd(long current, long value) {
        return value > 0L && current > Long.MAX_VALUE - value ? Long.MAX_VALUE : current + value;
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

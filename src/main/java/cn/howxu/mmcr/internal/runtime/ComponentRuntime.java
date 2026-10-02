package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TickFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.CapabilityStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickContext;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.modifier.ModifierTarget;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyHandler;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.MMCR;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
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
            MMCR.id("capability_tick_operation_failure"), StatusSeverity.FAILURE,
            MMCR.id("capability_tick"),
            FailureOccurrence.at(BuiltinFailureReasons.OPERATION_FAILED_WITHOUT_STATUS,
                    MMCR.id("capability_tick"),
                    FailurePhase.CAPABILITY_COMMIT, null, null,
                    Map.of("raw_reason_id", "operation_failed_without_status")));
    private List<ProcessingComponent> components = List.of();
    private List<MachineCapability> capabilities = List.of();
    private List<CapabilityIdentity> capabilityIdentity = List.of();
    private List<CapabilityPresentationSegment> capabilityPresentationSegments = List.of();
    private Map<BlockPos, List<Integer>> capabilitySourceIndices = Map.of();
    private Map<MachineCapability, List<Integer>> capabilityAliasIndices = Map.of();
    private List<Integer> capabilityProviderIndices = List.of();
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
    private Map<String, List<MachineModifier>> foundModifiers = Map.of();
    private List<MachineModifier> flattenedModifiers = List.of();
    private Map<ResourceLocation, MachineLevel> foundLevels = Map.of();
    private Set<BlockPos> linkedPortPositions = Set.of();
    private ModuleConnectionStatus moduleConnectionStatus = ModuleConnectionStatus.disconnected();
    private int installedModuleCount;
    private List<UpgradeBusSnapshot> upgradeBuses = List.of();
    private List<ItemStack> upgradeItems = List.of();
    private Map<ResourceLocation, Long> upgradeModifierUnits = Map.of();
    private List<MachineModifier> upgradeModifiers = List.of();
    private List<MachineModifier> smartInterfaceModifiers = List.of();
    private long upgradeContentRevision;
    private boolean modifiersAllowed = true;

    public boolean replaceComponents(List<ProcessingComponent> components) {
        List<ProcessingComponent> nextComponents = List.copyOf(components == null ? List.of() : components);
        CapabilityState capabilityState = capabilityStateFor(nextComponents);
        List<MachineCapability> nextCapabilities = capabilityState.capabilities();
        List<CapabilityIdentity> nextIdentity = capabilityState.identity();
        boolean componentsChanged = !this.components.equals(nextComponents);
        boolean capabilitiesChanged = !capabilityIdentity.equals(nextIdentity);
        List<CapabilityPresentationSegment> nextSegments = new ArrayList<>(capabilityState.segments());
        boolean presentationsChanged = nextSegments.size() != capabilityPresentationSegments.size();
        Map<BlockPos, List<Integer>> nextSources = new LinkedHashMap<>();
        Map<MachineCapability, List<Integer>> nextAliases = new IdentityHashMap<>();
        Map<MachineCapability, Integer> representatives = new IdentityHashMap<>();
        List<Integer> nextProviders = new ArrayList<>();
        for (int index = 0; index < nextSegments.size(); index++) {
            CapabilityPresentationSegment segment = nextSegments.get(index);
            if (index < capabilityPresentationSegments.size()
                    && capabilityPresentationSegments.get(index).sameSource(segment)) {
                segment = capabilityPresentationSegments.get(index);
                nextSegments.set(index, segment);
            } else {
                presentationsChanged = true;
            }
            Integer representative = representatives.get(segment.capability);
            if (representative == null) {
                representative = index;
                representatives.put(segment.capability, representative);
                nextProviders.add(representative);
            }
            List<Integer> sourceProviders = nextSources.computeIfAbsent(segment.sourcePos, ignored -> new ArrayList<>());
            if (!sourceProviders.contains(representative)) sourceProviders.add(representative);
            nextAliases.computeIfAbsent(segment.capability, ignored -> new ArrayList<>()).add(index);
        }
        this.components = nextComponents;
        if (componentsChanged) {
            stateVersion++;
            componentPresentationEpoch++;
        }
        this.capabilities = nextCapabilities;
        this.capabilityIdentity = nextIdentity;
        capabilityPresentationSegments = List.copyOf(nextSegments);
        capabilitySourceIndices = nextSources;
        capabilityAliasIndices = nextAliases;
        capabilityProviderIndices = List.copyOf(nextProviders);
        if (presentationsChanged) {
            capabilityPresentationEpoch++;
            cachedCapabilityPresentations = List.of();
        }
        if (capabilitiesChanged) {
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
     * Plans each tick facet in snapshot order and commits the resulting native operations.
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
        if (operations.isEmpty()) {
            if (stateChanged) markCapabilityPresentationChanged();
            return new CapabilityTickResult(operations, null, stateChanged);
        }
        for (CapabilityOperation operation : operations) {
            CapabilityResult result = operation.commit();
            if (result == null || !result.success()) {
                ExecutionStatus failure = result == null || result.status() == null
                        ? UNSPECIFIED_TICK_OPERATION_FAILURE : result.status();
                return new CapabilityTickResult(operations, failure, stateChanged);
            }
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
                    machineComponent == null || machineComponent.kind() == null ? null : machineComponent.kind().ioType(),
                    component.tags()));
        }
        cachedComponentPresentations = List.copyOf(snapshots);
        cachedComponentPresentationEpoch = componentPresentationEpoch;
        return cachedComponentPresentations;
    }

    public List<ControllerRuntimeSnapshot.CapabilityPresentation> capabilityPresentations() {
        if (cachedCapabilityPresentationEpoch == capabilityPresentationEpoch) return cachedCapabilityPresentations;
        List<ControllerRuntimeSnapshot.CapabilityPresentation> snapshots = new ArrayList<>(capabilities.size());
        for (CapabilityPresentationSegment segment : capabilityPresentationSegments) {
            if (segment.dirty) segment.refresh();
            snapshots.addAll(segment.rows);
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

    public boolean replaceModifiers(Map<String, List<MachineModifier>> modifiers) {
        Map<String, List<MachineModifier>> next = new LinkedHashMap<>();
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

    public Map<String, List<MachineModifier>> foundModifiers() {
        return foundModifiers;
    }

    public List<MachineModifier> modifierList() {
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
        return upgradeBuses.stream().map(UpgradeBusSnapshot::position).collect(Collectors.toUnmodifiableSet());
    }

    public List<ItemStack> upgradeItems() {
        return copyStacks(upgradeItems);
    }

    public Map<ResourceLocation, Long> upgradeModifierUnits() {
        return upgradeModifierUnits;
    }

    public long upgradeContentRevision() {
        return upgradeContentRevision;
    }

    public boolean replaceSmartInterfaceModifiers(List<MachineModifier> modifiers) {
        List<MachineModifier> next = List.copyOf(modifiers == null ? List.of() : modifiers);
        if (smartInterfaceModifiers.equals(next)) return false;
        smartInterfaceModifiers = next;
        rebuildModifierList();
        modifierVersion++;
        stateVersion++;
        return true;
    }

    public void setModifiersAllowed(boolean allowed) {
        if (modifiersAllowed == allowed) return;
        modifiersAllowed = allowed;
        rebuildModifierList();
        modifierVersion++;
        stateVersion++;
    }

    public boolean replaceLevels(Map<ResourceLocation, MachineLevel> levels) {
        Map<ResourceLocation, MachineLevel> next = new LinkedHashMap<>(levels == null ? Map.of() : levels);
        if (foundLevels.equals(next)) return false;
        foundLevels = immutableMap(next);
        rebuildModifierList();
        modifierVersion++;
        levelVersion++;
        stateVersion++;
        return true;
    }

    public Map<ResourceLocation, MachineLevel> foundLevels() {
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

    public Optional<ResourceLocation> connectedHostId() {
        return moduleConnectionStatus.connected()
                ? Optional.of(moduleConnectionStatus.connectedHostId())
                : Optional.empty();
    }

    public void markCapabilityPresentationChanged() {
        capabilityPresentationEpoch++;
        for (CapabilityPresentationSegment segment : capabilityPresentationSegments) segment.dirty = true;
    }

    /** Invalidates the occurrences at an absolute source position, including aliases at other hosts. */
    public void markCapabilityPresentationChanged(@Nullable BlockPos sourcePos) {
        List<Integer> indices = sourcePos == null ? null : capabilitySourceIndices.get(sourcePos);
        if (indices == null) {
            markCapabilityPresentationChanged();
            return;
        }
        capabilityPresentationEpoch++;
        if (indices.size() == 1) {
            CapabilityPresentationSegment seed = capabilityPresentationSegments.get(indices.getFirst());
            CapabilityStorage publishedStorage = seed.storage;
            Object publishedResourceStorage = seed.resourceStorage;
            List<Integer> aliases = capabilityAliasIndices.get(seed.capability);
            boolean sameBacking = true;
            for (int alias = 0; alias < aliases.size(); alias++) {
                CapabilityPresentationSegment segment = capabilityPresentationSegments.get(aliases.get(alias));
                segment.dirty = true;
                if (segment.storage != publishedStorage || segment.resourceStorage != publishedResourceStorage) sameBacking = false;
            }
            if (sameBacking) {
                CapabilityStorage currentStorage = CapabilityFactories.valueStorage(seed.capability, CapabilityStorage.class);
                Object currentResourceStorage = presentationStorage(seed.capability);
                markCapabilitiesSharingStorage(publishedStorage, publishedResourceStorage, currentStorage, currentResourceStorage,
                        null, seed.capability);
                return;
            }
        }
        Set<Object> changedStorage = Collections.newSetFromMap(new IdentityHashMap<>(indices.size() * 4));
        for (int index = 0; index < indices.size(); index++) {
            MachineCapability capability = capabilityPresentationSegments.get(indices.get(index)).capability;
            List<Integer> aliases = capabilityAliasIndices.get(capability);
            for (int alias = 0; alias < aliases.size(); alias++) {
                CapabilityPresentationSegment segment = capabilityPresentationSegments.get(aliases.get(alias));
                segment.dirty = true;
                changedStorage.add(segment.storage);
                changedStorage.add(segment.resourceStorage);
            }
            changedStorage.add(CapabilityFactories.valueStorage(capability, CapabilityStorage.class));
            changedStorage.add(presentationStorage(capability));
        }
        markCapabilitiesSharingStorage(null, null, null, null, changedStorage, null);
    }

    private void markCapabilitiesSharingStorage(Object first, Object second, Object third, Object fourth,
                                               @Nullable Set<Object> others, @Nullable MachineCapability seed) {
        for (int provider = 0; provider < capabilityProviderIndices.size(); provider++) {
            MachineCapability capability = capabilityPresentationSegments.get(capabilityProviderIndices.get(provider)).capability;
            if (capability == seed) continue;
            List<Integer> aliases = capabilityAliasIndices.get(capability);
            boolean resolved = false;
            boolean currentMatches = false;
            for (int alias = 0; alias < aliases.size(); alias++) {
                CapabilityPresentationSegment segment = capabilityPresentationSegments.get(aliases.get(alias));
                if (matchesStorage(segment.storage, first, second, third, fourth, others)
                        || matchesStorage(segment.resourceStorage, first, second, third, fourth, others)) {
                    segment.dirty = true;
                    continue;
                }
                if (!resolved) {
                    resolved = true;
                    ValueFacet<?> valueFacet = capability.facet(ValueFacet.class).orElse(null);
                    CapabilityStorage currentStorage = valueFacet == null ? null : valueFacet.storage();
                    Object currentResourceStorage = presentationStorage(capability);
                    currentMatches = matchesStorage(currentStorage, first, second, third, fourth, others)
                            || matchesStorage(currentResourceStorage, first, second, third, fourth, others);
                }
                if (currentMatches) segment.dirty = true;
            }
        }
        // A custom facet may have published rows while the dependency scan was still in progress.
        cachedCapabilityPresentationEpoch = Long.MIN_VALUE;
    }

    private static boolean matchesStorage(Object storage, Object first, Object second, Object third, Object fourth,
                                          @Nullable Set<Object> others) {
        return storage != null && (storage == first || storage == second || storage == third || storage == fourth
                || (others != null && others.contains(storage)));
    }

    private static Object presentationStorage(MachineCapability capability) {
        IItemHandler items = CapabilityFactories.itemHandler(capability);
        if (items != null) return items;
        IFluidHandler fluids = CapabilityFactories.fluidHandler(capability);
        return fluids != null ? fluids : CapabilityFactories.energyStorage(capability);
    }

    public long maxParallelism(Machine machine) {
        if (machine == null || !machine.parallelizable()) {
            return 1L;
        }
        long max = 0L;
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof ParallelControllerBlockEntity parallel) {
                max = saturatingAdd(max, parallel.currentParallelism());
            }
        }
        long effective = boundedLong(MachineModifier.apply(flattenedModifiers, ModifierTarget.PARALLELISM,
                Math.max(1L, max), false), 1L, Long.MAX_VALUE);
        return Math.min(Math.max(1L, machine.maxParallelism()), Math.max(1L, effective));
    }

    private static long saturatingAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        if (second < 0L && first < Long.MIN_VALUE - second) return Long.MIN_VALUE;
        return first + second;
    }

    public void clear() {
        replaceComponents(List.of());
        cachedComponentPresentations = List.of();
        cachedCapabilityPresentations = List.of();
        replaceModifiers(Map.of());
        replaceLevels(Map.of());
        replaceSmartInterfaceModifiers(List.of());
        replaceLinkedPortPositions(Set.of());
        replaceModuleConnectionState(ModuleConnectionStatus.disconnected(), 0);
        replaceUpgradeBuses(List.of());
    }

    private static CapabilityState capabilityStateFor(List<ProcessingComponent> components) {
        List<MachineCapability> result = new ArrayList<>();
        List<CapabilityIdentity> identities = new ArrayList<>();
        List<CapabilityPresentationSegment> segments = new ArrayList<>();
        for (ProcessingComponent component : components) {
            if (component.getContainer() instanceof CapabilityHost host) {
                try {
                    for (MachineCapability capability : host.capabilities()) {
                        List<CapabilityIdentity> occurrenceIdentity = new ArrayList<>();
                        for (IOType direction : capability.view().directions().values()) {
                            occurrenceIdentity.add(CapabilityIdentity.of(component.getPos(), capability, direction));
                        }
                        CapabilityPresentationSegment segment = new CapabilityPresentationSegment(
                                component.getPos().immutable(), capability, List.copyOf(occurrenceIdentity));
                        identities.addAll(occurrenceIdentity);
                        result.add(capability);
                        segments.add(segment);
                    }
                } catch (RuntimeException ignored) {
                    // A partially initialized port must not invalidate the controller runtime snapshot.
                }
            }
        }
        return new CapabilityState(List.copyOf(result), List.copyOf(identities), List.copyOf(segments));
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
        Map<ResourceLocation, Long> units = new LinkedHashMap<>();
        for (UpgradeBusSnapshot bus : next) {
            List<ItemStack> stacks = bus.stacks();
            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (stack.isEmpty()) continue;
                items.add(stack.copy());
                ResourceLocation modifierId = ModifierRegistry.modifierFor(stack);
                if (modifierId != null) units.merge(modifierId, (long) stack.getCount(), Long::sum);
            }
        }
        upgradeItems = List.copyOf(items);
        upgradeModifierUnits = immutableMap(units);
        upgradeModifiers = upgradeModifiers(units);
        upgradeContentRevision++;
        rebuildModifierList();
        modifierVersion++;
        stateVersion++;
        return true;
    }

    private List<MachineModifier> upgradeModifiers(Map<ResourceLocation, Long> units) {
        List<MachineModifier> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry : units.entrySet()) {
            ModifierDefinition definition = ModifierRegistry.get(entry.getKey());
            if (definition == null) continue;
            for (MachineModifier modifier : definition.modifiers()) {
                result.add(withUnitCount(modifier, entry.getValue()));
            }
        }
        return List.copyOf(result);
    }

    private void rebuildModifierList() {
        List<MachineModifier> modifiers = new ArrayList<>();
        foundLevels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .map(Map.Entry::getValue)
                .map(MachineLevel::modifier)
                .forEach(definition -> modifiers.addAll(definition.modifiers()));
        if (modifiersAllowed) {
            foundModifiers.values().forEach(modifiers::addAll);
            modifiers.addAll(upgradeModifiers);
        }
        modifiers.addAll(smartInterfaceModifiers);
        flattenedModifiers = List.copyOf(modifiers);
    }

    private static MachineModifier withUnitCount(MachineModifier modifier, long count) {
        if (count <= 1L || !(modifier instanceof MachineModifier.Numeric numeric)) return modifier;
        double value = switch (numeric.operation()) {
            case ADD, SUBTRACT -> saturatingMultiply(numeric.value(), count);
            case MULTIPLY, DIVIDE -> power(numeric.value(), count);
        };
        return new MachineModifier.Numeric(numeric.target(), value, numeric.operation(), numeric.affectsChance());
    }

    private static double power(double base, long exponent) {
        double result = 1D;
        double factor = base;
        long remaining = exponent;
        while (remaining > 0L) {
            if ((remaining & 1L) != 0L) result = saturatingMultiply(result, factor);
            remaining >>>= 1;
            if (remaining > 0L) factor = saturatingMultiply(factor, factor);
        }
        return result;
    }

    private static double saturatingMultiply(double first, double second) {
        double result = first * second;
        if (Double.isFinite(result)) return result;
        return Math.copySign(Double.MAX_VALUE, first * Math.signum(second));
    }

    private static long boundedLong(double value, long minimum, long maximum) {
        if (value <= minimum) return minimum;
        if (value >= maximum) return maximum;
        return Math.round(value);
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

    private static ControllerRuntimeSnapshot.CapabilityPresentation itemPresentation(
            MachineCapability capability, IItemHandler handler, IOType direction) {
        List<ControllerRuntimeSnapshot.StorageSlot> slots = new ArrayList<>(handler.getSlots());
        long amount = 0L;
        long capacity = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var stack = handler.getStackInSlot(slot);
            long slotAmount = handler instanceof LongItemStorage storage ? storage.amount(slot) : stack.getCount();
            long slotCapacity = handler instanceof LongItemStorage storage ? storage.capacity(slot) : handler.getSlotLimit(slot);
            slots.add(new ControllerRuntimeSnapshot.StorageSlot(stack.isEmpty() ? "" : String.valueOf(stack.getItem()),
                    slotAmount, slotCapacity));
            amount = saturatedAdd(amount, slotAmount);
            capacity = saturatedAdd(capacity, slotCapacity);
        }
        return new ControllerRuntimeSnapshot.CapabilityPresentation(
                capability.type() == null ? null : capability.type().id(), direction, amount, capacity, slots);
    }

    private static ControllerRuntimeSnapshot.CapabilityPresentation fluidPresentation(
            MachineCapability capability, IFluidHandler handler, IOType direction) {
        List<ControllerRuntimeSnapshot.StorageSlot> slots = new ArrayList<>(handler.getTanks());
        long amount = 0L;
        long capacity = 0L;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var stack = handler.getFluidInTank(tank);
            long tankAmount = handler instanceof LongFluidStorage storage ? storage.amount(tank) : stack.getAmount();
            long tankCapacity = handler instanceof LongFluidStorage storage ? storage.capacity(tank) : handler.getTankCapacity(tank);
            slots.add(new ControllerRuntimeSnapshot.StorageSlot(stack.isEmpty() ? "" : String.valueOf(stack.getFluid()),
                    tankAmount, tankCapacity));
            amount = saturatedAdd(amount, tankAmount);
            capacity = saturatedAdd(capacity, tankCapacity);
        }
        return new ControllerRuntimeSnapshot.CapabilityPresentation(
                capability.type() == null ? null : capability.type().id(), direction, amount, capacity, slots);
    }

    private static long saturatedAdd(long current, long value) {
        return value > 0L && current > Long.MAX_VALUE - value ? Long.MAX_VALUE : current + value;
    }

    /**
     * Owns immutable rows for one capability occurrence in the current component order.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class CapabilityPresentationSegment {
        private final BlockPos sourcePos;
        private final MachineCapability capability;
        private CapabilityStorage storage;
        private Object resourceStorage;
        private List<CapabilityIdentity> identity;
        private List<ControllerRuntimeSnapshot.CapabilityPresentation> rows = List.of();
        private boolean dirty = true;

        private CapabilityPresentationSegment(BlockPos sourcePos, MachineCapability capability,
                                              List<CapabilityIdentity> identity) {
            this.sourcePos = sourcePos;
            this.capability = capability;
            this.storage = CapabilityFactories.valueStorage(capability, CapabilityStorage.class);
            this.resourceStorage = presentationStorage(capability);
            this.identity = identity;
        }

        private boolean sameSource(CapabilityPresentationSegment other) {
            return capability == other.capability && storage == other.storage && resourceStorage == other.resourceStorage
                    && sourcePos.equals(other.sourcePos) && identity.equals(other.identity);
        }

        private void refresh() {
            CapabilityStorage currentStorage = CapabilityFactories.valueStorage(capability, CapabilityStorage.class);
            LongValueStorage currentValue = currentStorage instanceof LongValueStorage value ? value : null;
            Object currentResourceStorage = presentationStorage(capability);
            List<ControllerRuntimeSnapshot.CapabilityPresentation> snapshots = new ArrayList<>(identity.size());
            List<CapabilityIdentity> currentIdentity = new ArrayList<>(identity.size());
            for (IOType direction : capability.view().directions().values()) {
                currentIdentity.add(CapabilityIdentity.of(sourcePos, capability, direction));
                if (currentValue != null) {
                    snapshots.add(new ControllerRuntimeSnapshot.CapabilityPresentation(
                            capability.type() == null ? null : capability.type().id(), direction,
                            currentValue.amount(), currentValue.capacity(), List.of()));
                } else if (currentResourceStorage instanceof IItemHandler items) {
                    snapshots.add(itemPresentation(capability, items, direction));
                } else if (currentResourceStorage instanceof IFluidHandler fluids) {
                    snapshots.add(fluidPresentation(capability, fluids, direction));
                } else if (currentResourceStorage instanceof IEnergyStorage energy) {
                    long amount = energy instanceof LongEnergyHandler handler ? handler.getAmountAsLong() : energy.getEnergyStored();
                    long capacity = energy instanceof LongEnergyHandler handler ? handler.getCapacityAsLong() : energy.getMaxEnergyStored();
                    snapshots.add(new ControllerRuntimeSnapshot.CapabilityPresentation(
                            capability.type() == null ? null : capability.type().id(), direction, amount, capacity, List.of()));
                } else {
                    snapshots.add(new ControllerRuntimeSnapshot.CapabilityPresentation(
                            capability.type() == null ? null : capability.type().id(), direction, 0L, 0L, List.of()));
                }
            }
            rows = List.copyOf(snapshots);
            storage = currentStorage;
            resourceStorage = currentResourceStorage;
            identity = List.copyOf(currentIdentity);
            dirty = false;
        }
    }

    /**
     * Captures execution identity and presentation sources from the complete host capability view.
     *
     * @author howxu <dev@howxu.cn>
     */
    private record CapabilityState(List<MachineCapability> capabilities, List<CapabilityIdentity> identity,
                                   List<CapabilityPresentationSegment> segments) { }

    private record CapabilityIdentity(BlockPos componentPos, ResourceLocation type, IOType ioType, List<String> tags,
        String storageType, Object storageIdentity) {
        private static CapabilityIdentity of(BlockPos componentPos, MachineCapability capability, IOType direction) {
            CapabilityStorage storage = CapabilityFactories.valueStorage(capability, CapabilityStorage.class);
            return new CapabilityIdentity(componentPos.immutable(), capability.type().id(), direction,
                    List.copyOf(capability.view().tags()), storage == null
                            ? capability.getClass().getName() : storage.getClass().getName(),
                    storage == null ? capability : storage);
        }
    }
}

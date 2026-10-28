package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.plan.InputRoutingIndex;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirementHandler;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Overlap;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Port;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Scope;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-thread capture of native search rules, resource predicates and slot-specific output policies.
 *
 * @author howxu <dev@howxu.cn>
 */
final class RecipeSearchCapture {
    private final List<MachineCapability> capabilities;
    private final @Nullable InputRoutingFacet routing;
    private final @Nullable InputRoutingIndex routingIndex;
    private final Set<Integer> routedSources;
    private final boolean hasPrefetch;
    private final Map<MatchingKey, Map<Integer, LoadedMekanismBridge.ChemicalSearchPolicy>> chemicalPolicies = new HashMap<>();
    private final Map<Boolean, List<Scope>> scopeCache = new HashMap<>();
    private final Map<MatchingKey, List<Integer>> matchingCapabilities = new HashMap<>();
    private final Map<LiveKey, Boolean> liveMatches = new HashMap<>();
    private final Map<MachineRequirement, Boolean> nativeHandlers = new IdentityHashMap<>();
    private final Map<Object, Map<OutputPolicy, long[]>> outputCapacities = new IdentityHashMap<>();
    private final List<AsyncCapabilitySnapshot> pools = new ArrayList<>();
    private final Map<Integer, AsyncCapabilitySnapshot> snapshots = new HashMap<>();
    private final Map<Object, Map<Object, Integer>> resourceCells = new IdentityHashMap<>();
    private final Map<Object, Integer> valueCells = new IdentityHashMap<>();
    private final Map<Object, Set<AsyncResourceValue>> matchingResources = new HashMap<>();
    private final Map<ChemicalPredicate, Boolean> chemicalValidity = new HashMap<>();
    private final Map<MachineRequirement, OutputResource> outputResources = new IdentityHashMap<>();
    private final RecipeSearchResources resources = new RecipeSearchResources();
    private final Map<PortKey, CapturedPorts> portLayouts = new HashMap<>();
    private final Map<MachineRecipe, Overlap> overlaps = new IdentityHashMap<>();
    private final ResourcePort[] resourcePorts;
    private final boolean[] liveCapabilities;

    private RecipeSearchCapture(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities.capabilities();
        resourcePorts = new ResourcePort[this.capabilities.size()];
        liveCapabilities = new boolean[this.capabilities.size()];
        hasPrefetch = this.capabilities.stream().anyMatch(capability -> capability.facet(RecipeEnergyPrefetchFacet.class).isPresent());
        routing = capabilities.facets(InputRoutingFacet.class).stream().findFirst().orElse(null);
        routingIndex = routing == null ? null : InputRoutingIndex.capture(routing);
        routedSources = routing == null ? Set.of() : routing.sources().stream()
                .map(InputRoutingFacet.Source::capabilityIndex).collect(Collectors.toUnmodifiableSet());
    }

    static RecipeSearchSnapshot capture(ControllerRuntimeSnapshot runtime, long catalogVersion,
                                         long maximumParallelism, List<EffectiveRecipe> recipes,
                                         CapabilitySnapshot capabilities, boolean routingOmitted) {
        return new RecipeSearchCapture(capabilities).capture(runtime, catalogVersion, maximumParallelism, recipes,
                routingOmitted);
    }

    private RecipeSearchSnapshot capture(ControllerRuntimeSnapshot runtime, long catalogVersion,
                                          long maximumParallelism, List<EffectiveRecipe> recipes, boolean routingOmitted) {
        Set<ResourceLocation> needed = new HashSet<>();
        for (EffectiveRecipe recipe : recipes) {
            for (MachineRequirement requirement : recipe.requirements()) {
                if (!nativeHandlers.computeIfAbsent(requirement, RecipeSearchCapture::nativeHandler)) continue;
                needed.addAll(requirement.type().capabilityIds());
                if (requirement instanceof LoadedHeatRequirement) needed.add(MekanismRecipeTypes.HEAT);
                if (!outputResources.containsKey(requirement)) {
                    OutputResource output = nativeOutput(requirement);
                    if (output != null) {
                        outputResources.put(requirement, output);
                        resources.add(requirement.type().id(), output.value());
                    }
                }
            }
        }
        try (var ignored = AsyncPlanningFacet.beginCaptureScope()) {
            for (int index = 0; index < capabilities.size(); index++) {
                MachineCapability capability = capabilities.get(index);
                if (!needed.contains(capability.view().type().id())) continue;
                AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElse(null);
                if (facet == null) continue;
                AsyncCapabilitySnapshot snapshot = facet.captureSnapshot();
                snapshots.put(index, snapshot);
                Object storage = resourceStorage(capability);
                liveCapabilities[index] = storage instanceof NativeReservationAccess access && access.requiresServerOutputPlanning()
                        || capability.facet(RecipeEnergyPrefetchFacet.class).isPresent()
                        || capability.facet(EnergyOutputAdmissionFacet.class).isPresent();
                if (snapshot instanceof AsyncCapabilitySnapshot.Resource resource && storage != null) {
                    int[] slotPools = new int[resource.slots().size()];
                    for (int slot = 0; slot < slotPools.length; slot++) {
                        int pool = resourceCell(storage, resource, slot);
                        slotPools[slot] = pool;
                        ((AsyncCapabilitySnapshot.Resource) pools.get(pool)).slots().getFirst().resource()
                                .ifPresent(value -> resources.add(capability.view().type().id(), value));
                    }
                    ItemHandlerFacet items = capability.facet(ItemHandlerFacet.class).orElse(null);
                    resourcePorts[index] = new ResourcePort(storage, slotPools, items != null && items.supportsLargeStacks(),
                            outputCapacities.computeIfAbsent(storage, ignoredStorage -> new HashMap<>()));
                }
            }
        }
        List<Candidate> candidates = new ArrayList<>(recipes.size());
        for (EffectiveRecipe recipe : recipes) {
            MachineRecipe source = recipe.source();
            FailureReason staticFailure = !runtime.moduleConnectionStatus().canRunRecipe(source.requiredHostIds())
                    ? BuiltinFailureReasons.MODULE_CONNECTION : source.stageRequirements().stream()
                    .anyMatch(stage -> Math.max(1, runtime.structure().matchedStage()) < stage.minStage())
                    ? BuiltinFailureReasons.STAGE_INSUFFICIENT : AsyncRequirementPlanner.capturedRequirementFailure(runtime, source);
            List<Rule> rules = new ArrayList<>();
            RecipeSearchProof.Reason proof = null;
            int proofIndex = -1;
            boolean material = false;
            for (int index = 0; index < recipe.requirements().size(); index++) {
                MachineRequirement requirement = recipe.requirements().get(index);
                boolean input = requirement.io() == RecipeModifier.IOType.INPUT;
                boolean materialRule = input && requirement.type().capabilityIds().stream()
                        .anyMatch(id -> RequirementPlanner.isMaterialInput(IOType.INPUT, id));
                material |= materialRule;
                if (materialRule && routingOmitted) { proof = RecipeSearchProof.Reason.INCOMPLETE_ROUTING; proofIndex = index; }
                if (requirement instanceof EnergyRequirement energy && energy.io() == RecipeModifier.IOType.INPUT
                        && hasPrefetch
                        && proof == null) { proof = RecipeSearchProof.Reason.LIVE_CAPABILITY; proofIndex = index; }
                if (!nativeHandlers.computeIfAbsent(requirement, RecipeSearchCapture::nativeHandler)) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.OPAQUE_REQUIREMENT; proofIndex = index; }
                    continue;
                }
                List<Integer> matching = matching(requirement);
                if (materialRule && (routingOmitted || routing != null && matching.stream().anyMatch(occurrence ->
                        !routedSources.contains(occurrence)))) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.INCOMPLETE_ROUTING; proofIndex = index; }
                }
                boolean live = liveMatches.computeIfAbsent(new LiveKey(requirement.type().id(), matching),
                        ignored -> matching.stream().anyMatch(occurrence -> {
                    AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
                    boolean shapeMatches = requirement instanceof EnergyRequirement ? snapshot instanceof AsyncCapabilitySnapshot.Scalar
                            : requirement instanceof LoadedHeatRequirement ? snapshot instanceof AsyncCapabilitySnapshot.Heat
                            : snapshot instanceof AsyncCapabilitySnapshot.Resource;
                    return !shapeMatches || snapshot instanceof AsyncCapabilitySnapshot.Resource && resourcePorts[occurrence] == null
                            || liveCapabilities[occurrence];
                }));
                if (live && !(requirement instanceof LevelRequirement) && !(requirement instanceof StageRequirement)) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.LIVE_CAPABILITY; proofIndex = index; }
                    continue;
                }
                rules.add(rule(index, requirement, source.allowPartialOutputs(), matching));
            }
            List<Scope> scopes = scopes(material);
            candidates.add(new Candidate(source.id(), source.priority(), source.inputRequirementCount(),
                    Math.min(maximumParallelism, recipe.parallelismLimit()), staticFailure == null ? null : staticFailure.id(),
                    rules, scopes, overlaps.computeIfAbsent(source, RecipeSearchCapture::overlap), proof, proofIndex));
        }
        return new RecipeSearchSnapshot(new RecipeSearchSnapshot.Stamp(catalogVersion, runtime.structure().version(),
                runtime.capabilityVersion(), runtime.modifierVersion(), runtime.stateVersion(), runtime.upgradeContentRevision(),
                routing == null ? -1L : routing.revision(), runtime.maxParallelism(), runtime.factory().laneLimit()), pools, candidates);
    }

    private List<Scope> scopes(boolean material) {
        return scopeCache.computeIfAbsent(material, ignored -> {
            List<InputScope> scopes = routing == null ? List.of(InputScope.legacy()) : routing.scopes(material);
            return scopes.stream().map(scope -> {
                Set<Integer> allowed = new HashSet<>();
                if (routingIndex != null) {
                    var predicate = routingIndex.allowed(scope);
                    for (int index = 0; index < capabilities.size(); index++) if (predicate.test(index)) allowed.add(index);
                }
                return new Scope(scope, allowed);
            }).toList();
        });
    }

    private List<Integer> matching(MachineRequirement requirement) {
        MatchingKey key = new MatchingKey(requirement.type().id(), requirement.io(), requirement.tags(),
                requirement instanceof LoadedChemicalRequirement chemical ? chemicalPredicate(chemical) : null);
        return matchingCapabilities.computeIfAbsent(key, ignored -> findMatching(requirement));
    }

    private List<Integer> findMatching(MachineRequirement requirement) {
        List<Integer> matching = new ArrayList<>();
        // Keep occurrence indexes, including repeated references to the very same capability object.
        var ids = requirement.type().capabilityIds();
        IOType direction = IOType.valueOf(requirement.io().name());
        for (int index = 0; index < capabilities.size(); index++) {
            MachineCapability capability = capabilities.get(index);
            boolean heat = requirement instanceof LoadedHeatRequirement && snapshots.get(index) instanceof AsyncCapabilitySnapshot.Heat;
            if (!(ids.contains(capability.view().type().id()) || heat)
                    || !capability.view().directions().supports(direction)
                    || !requirement.tags().isEmpty() && requirement.tags().stream().noneMatch(capability.view()::matchesTag)) continue;
            if (requirement instanceof LoadedChemicalRequirement chemical
                    && !LoadedMekanismBridge.acceptsChemicalSearchPort(chemical, capability)
                    || requirement instanceof LoadedHeatRequirement && !LoadedMekanismBridge.acceptsHeatSearchPort(capability)) continue;
            matching.add(index);
        }
        if (direction == IOType.OUTPUT) matching.sort(Comparator.comparingInt(
                (Integer index) -> capabilities.get(index).outputPriority()).reversed());
        return matching;
    }

    private Rule rule(int index, MachineRequirement requirement, boolean partial, List<Integer> matching) {
        IOType direction = IOType.valueOf(requirement.io().name());
        boolean output = direction == IOType.OUTPUT;
        Kind kind = Kind.NOOP;
        long amount = 0L;
        double heat = 0D;
        float chance = 1F;
        float consume = 1F;
        boolean valid = true;
        Set<AsyncResourceValue> accepted = Set.of();
        OutputResource capturedOutput = outputResources.get(requirement);
        Object nativeOutput = capturedOutput == null ? null : capturedOutput.resource();
        AsyncResourceValue outputValue = capturedOutput == null ? null : capturedOutput.value();
        ResourceLocation reason = (output ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT).id();
        if (requirement instanceof ItemRequirement item) {
            kind = Kind.RESOURCE;
            amount = output ? capturedOutput == null ? 0L : capturedOutput.amount() : item.count();
            chance = item.chance();
            consume = item.consumeChance();
            valid = output ? !partial || nativeOutput != null : item.item() != null;
            if (!output && valid) accepted = matchingResources.computeIfAbsent(
                    new ItemPredicate(item.item(), item.components()), ignored -> resources.matching(item));
        } else if (requirement instanceof FluidRequirement fluid) {
            kind = Kind.RESOURCE;
            amount = output ? capturedOutput == null ? 0L : capturedOutput.amount() : fluid.amount();
            chance = fluid.chance();
            consume = fluid.consumeChance();
            valid = output ? !partial || nativeOutput != null : fluid.fluid() != null;
            if (!output && valid) accepted = matchingResources.computeIfAbsent(fluid.fluid(), ignored -> resources.matching(fluid));
        } else if (requirement instanceof LoadedChemicalRequirement chemical) {
            kind = Kind.RESOURCE;
            amount = chemical.ingredient().amount();
            chance = chemical.chance();
            consume = chemical.consumeChance();
            valid = chemicalValidity.computeIfAbsent(chemicalPredicate(chemical),
                    ignored -> LoadedMekanismBridge.validChemicalSearch(chemical));
            if (!valid) reason = MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH.id();
            else if (!output) accepted = matchingResources.computeIfAbsent(chemicalPredicate(chemical),
                    ignored -> LoadedMekanismBridge.matchingChemicalSearchResources(chemical, resources.all(requirement.type().id())));
            if (valid) reason = (output ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                    : MekanismFailureReasons.CHEMICAL_INPUT_MISSING).id();
        } else if (requirement instanceof LightningRequirement lightning) {
            kind = Kind.RESOURCE;
            amount = lightning.amount();
            chance = lightning.chance();
            if (!output) accepted = Set.of(NativeAsyncResourceValues.lightning(lightning.tier()));
            reason = (output ? LightningFailureReasons.OUTPUT_BLOCKED : LightningFailureReasons.INPUT_MISSING).id();
        } else if (requirement instanceof EnergyRequirement energy) {
            kind = Kind.SCALAR;
            amount = energy.fePerTick();
            if (!output) reason = BuiltinFailureReasons.MISSING_ENERGY.id();
        } else if (requirement instanceof LoadedHeatRequirement temperature) {
            heat = temperature.heat().value();
            kind = temperature.heat().kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE ? Kind.TEMPERATURE : Kind.HEAT;
            reason = (kind == Kind.TEMPERATURE ? MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT
                    : MekanismFailureReasons.HEAT_OUTPUT_BLOCKED).id();
            valid = heat >= 0D;
        }
        Kind capturedKind = kind;
        CapturedPorts capturedPorts = portLayouts.computeIfAbsent(new PortKey(kind, direction, matching, outputValue,
                requirement instanceof LoadedChemicalRequirement chemical ? chemicalPredicate(chemical) : null),
                ignored -> capturePorts(requirement, capturedKind, matching, nativeOutput, outputValue));
        List<Port> ports = capturedPorts.ports();
        if (requirement instanceof LoadedChemicalRequirement && valid) {
            if (matching.isEmpty()) reason = (output ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT).id();
            else if (output && capturedPorts.radioactiveRejected()) {
                boolean space = false;
                for (Port port : ports) {
                    var slot = ((AsyncCapabilitySnapshot.Resource) pools.get(port.pool())).slots().getFirst();
                    if ((slot.resource().isEmpty() || slot.resource().get().equals(outputValue)) && port.capacity() > slot.amount()) {
                        space = true;
                        break;
                    }
                }
                if (!space) reason = MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED.id();
            }
        }
        return new Rule(index, requirement.type().id(), kind, direction, Math.max(0L, amount), heat, chance, consume,
                output && partial, valid, accepted, outputValue, ports, reason);
    }

    private CapturedPorts capturePorts(MachineRequirement requirement, Kind kind, List<Integer> matching,
                                       @Nullable Object nativeOutput, @Nullable AsyncResourceValue outputValue) {
        boolean output = requirement.io() == RecipeModifier.IOType.OUTPUT;
        List<Port> ports = new ArrayList<>();
        boolean radioactiveRejected = false;
        for (int occurrence : matching) {
            MachineCapability capability = capabilities.get(occurrence);
            AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
            if (kind == Kind.RESOURCE) {
                ResourcePort port = resourcePorts[occurrence];
                if (port == null) continue;
                var policy = requirement instanceof LoadedChemicalRequirement chemical ? chemicalPolicy(chemical, occurrence) : null;
                if (policy != null) radioactiveRejected |= policy.radioactiveRejected();
                long[] capacities = !output || nativeOutput == null || policy != null ? null : outputCapacities(port, nativeOutput, outputValue);
                for (int slot = 0; slot < port.pools().length; slot++) {
                    int pool = port.pools()[slot];
                    long capacity = !output || nativeOutput == null ? 0L
                            : policy != null ? policy.outputCapacity() : capacities[slot];
                    ports.add(new Port(pool, occurrence, capacity, policy == null ? Long.MAX_VALUE : policy.inputLimit()));
                }
            } else if (kind == Kind.SCALAR && snapshot instanceof AsyncCapabilitySnapshot.Scalar) {
                Object identity = capability.facet(AsyncPlanningFacet.class).orElseThrow().planningIdentity();
                int pool = valueCells.computeIfAbsent(identity, ignored -> {
                    pools.add(snapshot);
                    return pools.size() - 1;
                });
                AsyncCapabilitySnapshot.Scalar scalar = (AsyncCapabilitySnapshot.Scalar) pools.get(pool);
                ports.add(new Port(pool, occurrence, scalar.capacity(), scalar.transferLimit()));
            } else if ((kind == Kind.HEAT || kind == Kind.TEMPERATURE) && snapshot instanceof AsyncCapabilitySnapshot.Heat) {
                Object identity = capability.facet(AsyncPlanningFacet.class).orElseThrow().planningIdentity();
                int pool = valueCells.computeIfAbsent(identity, ignored -> { pools.add(snapshot); return pools.size() - 1; });
                ports.add(new Port(pool, occurrence, 0L, 0L));
            }
        }
        return new CapturedPorts(List.copyOf(ports), radioactiveRejected);
    }

    private LoadedMekanismBridge.ChemicalSearchPolicy chemicalPolicy(LoadedChemicalRequirement requirement, int occurrence) {
        MatchingKey key = new MatchingKey(requirement.type().id(), requirement.io(), requirement.tags(), chemicalPredicate(requirement));
        return chemicalPolicies.computeIfAbsent(key, ignored -> new HashMap<>()).computeIfAbsent(occurrence,
                ignored -> LoadedMekanismBridge.chemicalSearchPolicy(requirement, capabilities.get(occurrence)));
    }

    private long[] outputCapacities(ResourcePort port, Object resource, AsyncResourceValue value) {
        return port.outputCapacities().computeIfAbsent(new OutputPolicy(value, port.largeStacks()), ignored -> {
            long[] capacities = new long[port.pools().length];
            long stackLimit = resource instanceof ItemStack item && !port.largeStacks() ? item.getMaxStackSize() : Long.MAX_VALUE;
            for (int slot = 0; slot < capacities.length; slot++) {
                long capacity;
                if (port.storage() instanceof IItemHandler handler && resource instanceof ItemStack item) {
                    if (!handler.isItemValid(slot, item)) continue;
                    capacity = handler instanceof NativeStackSync.Item sync ? sync.capacity(slot, item)
                            : handler instanceof LongItemStorage local ? local.capacity(slot) : handler.getSlotLimit(slot);
                } else if (port.storage() instanceof IFluidHandler handler && resource instanceof FluidStack fluid) {
                    if (!handler.isFluidValid(slot, fluid)) continue;
                    capacity = handler instanceof NativeStackSync.Fluid sync ? sync.capacity(slot)
                            : handler instanceof LongFluidStorage local ? local.capacity(slot) : handler.getTankCapacity(slot);
                } else if (port.storage() instanceof LightningStorage storage && resource instanceof LightningTier tier) {
                    capacity = storage.capacity(slot, tier);
                } else continue;
                capacities[slot] = Math.max(0L, Math.min(capacity, stackLimit));
            }
            return capacities;
        });
    }

    private static @Nullable Object resourceStorage(MachineCapability capability) {
        ItemHandlerFacet item = capability.facet(ItemHandlerFacet.class).orElse(null);
        if (item != null) return item.itemHandler();
        FluidHandlerFacet fluid = capability.facet(FluidHandlerFacet.class).orElse(null);
        if (fluid != null) return fluid.fluidHandler();
        if (capability instanceof LightningCapability lightning) {
            return lightning.storage();
        }
        // Multi-tank and network chemical admission uses a scoped compatibility proof.
        return capability instanceof LoadedMekanismBridge.ChemicalPort port ? port.chemicalTank() : null;
    }

    private int resourceCell(Object storage, AsyncCapabilitySnapshot.Resource snapshot, int slot) {
        Object identity = storage instanceof NativeReservationAccess access ? access.reservationIdentity() : storage;
        Object key = storage instanceof NativeReservationAccess access ? access.reservationSlot(slot) : slot;
        Map<Object, Integer> cells = resourceCells.computeIfAbsent(identity, ignored -> new HashMap<>());
        return cells.computeIfAbsent(key, ignored -> {
            var value = snapshot.slots().get(slot);
            pools.add(new AsyncCapabilitySnapshot.Resource(snapshot.capabilityId(), List.of(
                    new AsyncCapabilitySnapshot.ResourceSlot(value.resource(), value.amount(), Long.MAX_VALUE))));
            return pools.size() - 1;
        });
    }

    private static boolean nativeHandler(MachineRequirement requirement) {
        Object handler = RequirementHandlerRegistry.handlerFor(requirement.type());
        return requirement instanceof ItemRequirement && handler instanceof ItemRequirementHandler
                || requirement instanceof FluidRequirement && handler instanceof FluidRequirementHandler
                || requirement instanceof EnergyRequirement && handler instanceof EnergyRequirementHandler
                || requirement instanceof LevelRequirement && handler instanceof LevelRequirementHandler
                || requirement instanceof StageRequirement && handler instanceof StageRequirementHandler
                || requirement instanceof LightningRequirement && handler instanceof LightningRequirementHandler
                    && LightningBridge.get().available()
                || requirement instanceof LoadedChemicalRequirement && handler == requirement.type().handler()
                    && LoadedChemicalRequirement.supportsSearchSnapshot()
                || requirement instanceof LoadedHeatRequirement && handler == requirement.type().handler()
                    && LoadedHeatRequirement.supportsSearchSnapshot();
    }

    private static @Nullable OutputResource nativeOutput(MachineRequirement requirement) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return null;
        Object resource;
        long amount;
        if (requirement instanceof ItemRequirement item) {
            ItemStack stack = item.stack(null);
            if (stack.isEmpty()) return null;
            resource = stack.copyWithCount(1);
            amount = stack.getCount();
        } else if (requirement instanceof FluidRequirement fluid) {
            FluidStack stack = fluid.stack();
            if (stack.isEmpty()) return null;
            resource = stack.copyWithAmount(1);
            amount = stack.getAmount();
        } else if (requirement instanceof LightningRequirement lightning) {
            resource = lightning.tier();
            amount = lightning.amount();
        } else if (requirement instanceof LoadedChemicalRequirement chemical && LoadedChemicalRequirement.supportsSearchSnapshot()) {
            resource = LoadedMekanismBridge.chemicalSearchOutput(chemical);
            if (resource == null) return null;
            amount = chemical.ingredient().amount();
        } else return null;
        return new OutputResource(resource, value(resource), amount);
    }

    private static @Nullable AsyncResourceValue value(Object resource) {
        if (resource instanceof LightningTier tier) return NativeAsyncResourceValues.lightning(tier);
        if (resource instanceof ItemStack item) return item.isEmpty() ? null : NativeAsyncResourceValues.item(item);
        if (resource instanceof FluidStack fluid) return fluid.isEmpty() ? null : NativeAsyncResourceValues.fluid(fluid);
        return LoadedMekanismBridge.chemicalSearchValue(resource);
    }

    private record MatchingKey(ResourceLocation type, RecipeModifier.IOType direction, List<String> tags,
                               @Nullable Object chemical) { }

    /** @author howxu <dev@howxu.cn> */
    private record LiveKey(ResourceLocation type, List<Integer> occurrences) { }

    /** Capture-local slot mapping; native storage never enters the worker snapshot.
     * @author howxu <dev@howxu.cn>
     */
    private record ResourcePort(Object storage, int[] pools, boolean largeStacks,
                                Map<OutputPolicy, long[]> outputCapacities) { }
    /** @author howxu <dev@howxu.cn> */
    private record OutputPolicy(AsyncResourceValue resource, boolean largeStacks) { }
    /** @author howxu <dev@howxu.cn> */
    private record OutputResource(Object resource, AsyncResourceValue value, long amount) { }
    /** @author howxu <dev@howxu.cn> */
    private record PortKey(Kind kind, IOType direction, List<Integer> occurrences, @Nullable AsyncResourceValue output,
                           @Nullable Object chemical) { }
    /** @author howxu <dev@howxu.cn> */
    private record CapturedPorts(List<Port> ports, boolean radioactiveRejected) { }

    private static Overlap overlap(MachineRecipe recipe) {
        Set<ResourceLocation> items = new HashSet<>();
        Set<ResourceLocation> lightning = new HashSet<>();
        boolean anyItem = false;
        boolean hasItem = false;
        boolean custom = false;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (!nativeHandler(requirement)) { custom = true; continue; }
            if (requirement instanceof LightningRequirement resource) lightning.add(resource.tier().requirementId());
            if (!(requirement instanceof ItemRequirement item) || item.item() == null) continue;
            hasItem = true;
            try { for (ItemStack stack : item.item().getItems()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem())); }
            catch (UnsupportedOperationException ignored) { anyItem = true; }
        }
        return new Overlap(items, anyItem, hasItem, custom, lightning);
    }

    private static ChemicalPredicate chemicalPredicate(LoadedChemicalRequirement requirement) {
        return new ChemicalPredicate(requirement.ingredient().kind(), requirement.ingredient().id());
    }

    /** @author howxu <dev@howxu.cn> */
    private record ChemicalPredicate(ChemicalIngredient.Kind kind, ResourceLocation id) { }

    private record ItemPredicate(Object ingredient, Object components) {}
}

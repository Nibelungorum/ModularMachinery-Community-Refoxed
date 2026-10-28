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
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
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
import java.util.LinkedHashSet;
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
    private final Map<MachineRequirement, Boolean> nativeHandlers = new IdentityHashMap<>();
    private final Map<Object, Map<OutputPolicy, Long>> outputCapacities = new IdentityHashMap<>();
    private final List<AsyncCapabilitySnapshot> pools = new ArrayList<>();
    private final Map<Integer, AsyncCapabilitySnapshot> snapshots = new HashMap<>();
    private final Map<Object, Map<Object, Integer>> resourceCells = new IdentityHashMap<>();
    private final Map<Object, Integer> valueCells = new IdentityHashMap<>();
    private final Map<Object, Set<AsyncResourceValue>> matchingResources = new HashMap<>();
    private final Map<MachineRequirement, Object> outputResources = new IdentityHashMap<>();
    private final Map<ResourceLocation, Set<AsyncResourceValue>> universe = new HashMap<>();

    private RecipeSearchCapture(CapabilitySnapshot capabilities) {
        this.capabilities = capabilities.capabilities();
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
                Object output = nativeOutput(requirement);
                if (output != null) {
                    outputResources.put(requirement, output);
                    universe.computeIfAbsent(requirement.type().id(), ignored -> new LinkedHashSet<>()).add(value(output));
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
                if (snapshot instanceof AsyncCapabilitySnapshot.Resource resource && resourceStorage(capability) != null) {
                    Set<AsyncResourceValue> values = universe.computeIfAbsent(capability.view().type().id(),
                            key -> new LinkedHashSet<>());
                    for (int slot = 0; slot < resource.slots().size(); slot++) {
                        int pool = resourceCell(index, slot);
                        ((AsyncCapabilitySnapshot.Resource) pools.get(pool)).slots().getFirst().resource().ifPresent(values::add);
                    }
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
                boolean live = matching.stream().anyMatch(occurrence -> {
                    MachineCapability capability = capabilities.get(occurrence);
                    Object storage = resourceStorage(capability);
                    AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
                    boolean shapeMatches = requirement instanceof EnergyRequirement ? snapshot instanceof AsyncCapabilitySnapshot.Scalar
                            : requirement instanceof LoadedHeatRequirement ? snapshot instanceof AsyncCapabilitySnapshot.Heat
                            : snapshot instanceof AsyncCapabilitySnapshot.Resource;
                    return !shapeMatches || snapshot instanceof AsyncCapabilitySnapshot.Resource && storage == null
                            || storage instanceof NativeReservationAccess access && access.requiresServerOutputPlanning()
                            || capability.facet(RecipeEnergyPrefetchFacet.class).isPresent()
                            || capability.facet(EnergyOutputAdmissionFacet.class).isPresent();
                });
                if (live && !(requirement instanceof LevelRequirement) && !(requirement instanceof StageRequirement)) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.LIVE_CAPABILITY; proofIndex = index; }
                    continue;
                }
                rules.add(rule(index, requirement, source.allowPartialOutputs(), matching));
            }
            List<Scope> scopes = scopes(material);
            candidates.add(new Candidate(source.id(), source.priority(), source.inputRequirementCount(),
                    Math.min(maximumParallelism, recipe.parallelismLimit()), staticFailure == null ? null : staticFailure.id(),
                    rules, scopes, overlap(source), proof, proofIndex));
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
                requirement instanceof LoadedChemicalRequirement chemical ? chemical.ingredient() : null);
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
        Object nativeOutput = outputResources.get(requirement);
        AsyncResourceValue outputValue = nativeOutput == null ? null : value(nativeOutput);
        ResourceLocation reason = (output ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT).id();
        if (requirement instanceof ItemRequirement item) {
            kind = Kind.RESOURCE;
            amount = output ? item.stack(null).getCount() : item.count();
            chance = item.chance();
            consume = item.consumeChance();
            valid = output ? !partial || nativeOutput != null : item.item() != null;
            if (!output && valid) accepted = matchingResources.computeIfAbsent(
                    new ItemPredicate(item.item(), item.components()), ignored -> resources(requirement).stream().filter(value -> {
                        ItemStack resource = NativeAsyncResourceValues.item(value);
                        return item.item().test(resource) && item.components().matches(resource);
                    }).collect(Collectors.toUnmodifiableSet()));
        } else if (requirement instanceof FluidRequirement fluid) {
            kind = Kind.RESOURCE;
            amount = output ? fluid.stack().getAmount() : fluid.amount();
            chance = fluid.chance();
            consume = fluid.consumeChance();
            valid = output ? !partial || nativeOutput != null : fluid.fluid() != null;
            if (!output && valid) accepted = matchingResources.computeIfAbsent(fluid.fluid(), ignored -> resources(requirement).stream()
                    .filter(value -> fluid.fluid().test(NativeAsyncResourceValues.fluid(value)))
                    .collect(Collectors.toUnmodifiableSet()));
        } else if (requirement instanceof LoadedChemicalRequirement chemical) {
            kind = Kind.RESOURCE;
            amount = chemical.ingredient().amount();
            chance = chemical.chance();
            consume = chemical.consumeChance();
            valid = LoadedMekanismBridge.validChemicalSearch(chemical);
            if (!valid) reason = MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH.id();
            else if (!output) accepted = matchingResources.computeIfAbsent(chemical.ingredient(), ignored -> resources(requirement).stream()
                    .filter(value -> LoadedMekanismBridge.matchesChemicalSearch(chemical, value))
                    .collect(Collectors.toUnmodifiableSet()));
            if (valid) reason = (output ? MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED
                    : MekanismFailureReasons.CHEMICAL_INPUT_MISSING).id();
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
        List<Port> ports = new ArrayList<>();
        boolean radioactiveRejected = false;
        for (int occurrence : matching) {
            MachineCapability capability = capabilities.get(occurrence);
            AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
            if (kind == Kind.RESOURCE) {
                Object storage = resourceStorage(capability);
                if (storage == null || !(snapshot instanceof AsyncCapabilitySnapshot.Resource resource)) continue;
                var policy = requirement instanceof LoadedChemicalRequirement chemical ? chemicalPolicy(chemical, occurrence) : null;
                if (policy != null) radioactiveRejected |= policy.radioactiveRejected();
                for (int slot = 0; slot < resource.slots().size(); slot++) {
                    int pool = resourceCell(occurrence, slot);
                    ItemHandlerFacet items = capability.facet(ItemHandlerFacet.class).orElse(null);
                    long capacity = !output || nativeOutput == null ? 0L
                            : policy != null ? policy.outputCapacity()
                            : outputCapacity(storage, slot, nativeOutput, outputValue, items != null && items.supportsLargeStacks());
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
        if (requirement instanceof LoadedChemicalRequirement && valid) {
            if (matching.isEmpty()) reason = (output ? BuiltinFailureReasons.MISSING_OUTPUT : BuiltinFailureReasons.MISSING_INPUT).id();
            else if (output && radioactiveRejected) {
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

    private LoadedMekanismBridge.ChemicalSearchPolicy chemicalPolicy(LoadedChemicalRequirement requirement, int occurrence) {
        MatchingKey key = new MatchingKey(requirement.type().id(), requirement.io(), requirement.tags(), requirement.ingredient());
        return chemicalPolicies.computeIfAbsent(key, ignored -> new HashMap<>()).computeIfAbsent(occurrence,
                ignored -> LoadedMekanismBridge.chemicalSearchPolicy(requirement, capabilities.get(occurrence)));
    }

    private long outputCapacity(Object storage, int slot, Object resource,
                                AsyncResourceValue value, boolean largeStacks) {
        return outputCapacities.computeIfAbsent(storage, ignored -> new HashMap<>())
                .computeIfAbsent(new OutputPolicy(slot, value, largeStacks), ignored -> {
                    long capacity;
                    if (storage instanceof IItemHandler handler && resource instanceof ItemStack item) {
                        if (!handler.isItemValid(slot, item)) return 0L;
                        capacity = handler instanceof NativeStackSync.Item sync ? sync.capacity(slot, item)
                                : handler instanceof LongItemStorage local ? local.capacity(slot) : handler.getSlotLimit(slot);
                        if (!largeStacks) capacity = Math.min(capacity, item.getMaxStackSize());
                    } else if (storage instanceof IFluidHandler handler && resource instanceof FluidStack fluid) {
                        if (!handler.isFluidValid(slot, fluid)) return 0L;
                        capacity = handler instanceof NativeStackSync.Fluid sync ? sync.capacity(slot)
                                : handler instanceof LongFluidStorage local ? local.capacity(slot) : handler.getTankCapacity(slot);
                    } else return 0L;
                    return Math.max(0L, capacity);
                });
    }

    private static @Nullable Object resourceStorage(MachineCapability capability) {
        ItemHandlerFacet item = capability.facet(ItemHandlerFacet.class).orElse(null);
        if (item != null) return item.itemHandler();
        FluidHandlerFacet fluid = capability.facet(FluidHandlerFacet.class).orElse(null);
        if (fluid != null) return fluid.fluidHandler();
        // Multi-tank and network chemical admission uses a scoped compatibility proof.
        return capability instanceof LoadedMekanismBridge.ChemicalPort port ? port.chemicalTank() : null;
    }

    private int resourceCell(int occurrence, int slot) {
        MachineCapability capability = capabilities.get(occurrence);
        Object storage = resourceStorage(capability);
        Object identity = storage instanceof NativeReservationAccess access ? access.reservationIdentity() : storage;
        Object key = storage instanceof NativeReservationAccess access ? access.reservationSlot(slot) : slot;
        Map<Object, Integer> cells = resourceCells.computeIfAbsent(identity, ignored -> new HashMap<>());
        return cells.computeIfAbsent(key, ignored -> {
            var snapshot = (AsyncCapabilitySnapshot.Resource) snapshots.get(occurrence);
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
                || requirement instanceof LoadedChemicalRequirement && handler == requirement.type().handler()
                    && LoadedChemicalRequirement.supportsSearchSnapshot()
                || requirement instanceof LoadedHeatRequirement && handler == requirement.type().handler()
                    && LoadedHeatRequirement.supportsSearchSnapshot();
    }

    private static @Nullable Object nativeOutput(MachineRequirement requirement) {
        if (requirement.io() != RecipeModifier.IOType.OUTPUT) return null;
        if (requirement instanceof ItemRequirement item) return item.stack(null).isEmpty() ? null : item.stack(null).copyWithCount(1);
        if (requirement instanceof FluidRequirement fluid) return fluid.stack().isEmpty() ? null : fluid.stack().copyWithAmount(1);
        if (requirement instanceof LoadedChemicalRequirement chemical && LoadedChemicalRequirement.supportsSearchSnapshot()) {
            return LoadedMekanismBridge.chemicalSearchOutput(chemical);
        }
        return null;
    }

    private static @Nullable AsyncResourceValue value(Object resource) {
        if (resource instanceof ItemStack item) return item.isEmpty() ? null : NativeAsyncResourceValues.item(item);
        if (resource instanceof FluidStack fluid) return fluid.isEmpty() ? null : NativeAsyncResourceValues.fluid(fluid);
        return LoadedMekanismBridge.chemicalSearchValue(resource);
    }

    private Set<AsyncResourceValue> resources(MachineRequirement requirement) {
        return universe.getOrDefault(requirement.type().id(), Set.of());
    }

    private record MatchingKey(ResourceLocation type, RecipeModifier.IOType direction, List<String> tags,
                               @Nullable Object chemical) { }

    private record OutputPolicy(int slot, AsyncResourceValue resource, boolean largeStacks) { }

    private static Overlap overlap(MachineRecipe recipe) {
        Set<ResourceLocation> items = new HashSet<>();
        boolean anyItem = false;
        boolean hasItem = false;
        boolean custom = false;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            if (!nativeHandler(requirement)) { custom = true; continue; }
            if (!(requirement instanceof ItemRequirement item) || item.item() == null) continue;
            hasItem = true;
            try { for (ItemStack stack : item.item().getItems()) items.add(BuiltInRegistries.ITEM.getKey(stack.getItem())); }
            catch (UnsupportedOperationException ignored) { anyItem = true; }
        }
        return new Overlap(items, anyItem, hasItem, custom);
    }

    private record ItemPredicate(Object ingredient, Object components) {}
}

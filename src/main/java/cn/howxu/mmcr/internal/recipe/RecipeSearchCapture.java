package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyOutputAdmissionFacet;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ValueFacet;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.plan.InputRoutingIndex;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Candidate;
import cn.howxu.mmcr.api.recipe.requirement.SearchDemand;
import cn.howxu.mmcr.api.recipe.requirement.SearchDemand.Kind;
import cn.howxu.mmcr.api.recipe.requirement.SearchOverlap;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Overlap;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Port;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Scope;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
    private final Map<Boolean, List<Scope>> scopeCache = new HashMap<>();
    private final Map<MatchingKey, List<Integer>> matchingCapabilities = new HashMap<>();
    private final Map<LiveKey, Boolean> liveMatches = new HashMap<>();
    private final Map<MachineRequirement, SearchDemand> demands = new IdentityHashMap<>();
    private final Map<ResourceStorage<?>, Map<OutputPolicy, long[]>> outputCapacities = new IdentityHashMap<>();
    private final List<AsyncCapabilitySnapshot> pools = new ArrayList<>();
    private final Map<Integer, AsyncCapabilitySnapshot> snapshots = new HashMap<>();
    private final Map<Object, Map<Object, Integer>> resourceCells = new IdentityHashMap<>();
    private final Map<Object, Integer> valueCells = new IdentityHashMap<>();
    private final Map<Object, Set<AsyncResourceValue>> matchingResources = new HashMap<>();
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
        Set<Identifier> needed = new HashSet<>();
        for (EffectiveRecipe recipe : recipes) {
            for (MachineRequirement requirement : recipe.requirements()) {
                SearchDemand demand = demand(requirement);
                if (demand == null) continue;
                needed.addAll(requirement.type().capabilityIds());
                if (demand.output() != null) resources.add(requirement.type().id(), demand.output().value());
            }
        }
        // Aliases may expose different slot orders or disjoint slices of the same physical storage.
        Map<AsyncPlanningFacet, AsyncCapabilitySnapshot> views = new IdentityHashMap<>();
        for (int index = 0; index < capabilities.size(); index++) {
            MachineCapability capability = capabilities.get(index);
            if (!needed.contains(capability.view().type().id())) continue;
            AsyncPlanningFacet facet = capability.facet(AsyncPlanningFacet.class).orElse(null);
            if (facet == null) continue;
            AsyncCapabilitySnapshot snapshot = views.computeIfAbsent(facet, AsyncPlanningFacet::captureSnapshot);
            snapshots.put(index, snapshot);
            ResourceFacet<?> resources = capability.facet(ResourceFacet.class).orElse(null);
            ResourceStorage<?> storage = resources == null ? null : resources.storage();
            liveCapabilities[index] = storage instanceof OutputResourceStorage<?>
                    || capability.facet(RecipeEnergyPrefetchFacet.class).isPresent()
                    || capability.facet(EnergyOutputAdmissionFacet.class).isPresent();
            if (snapshot instanceof AsyncCapabilitySnapshot.Resource captured && storage != null
                    && captured.slots().size() == storage.size()) {
                int[] slotPools = new int[captured.slots().size()];
                for (int slot = 0; slot < slotPools.length; slot++) {
                    int pool = resourceCell(storage, slot, captured);
                    slotPools[slot] = pool;
                    ((AsyncCapabilitySnapshot.Resource) pools.get(pool)).slots().getFirst().resource()
                            .ifPresent(value -> this.resources.add(capability.view().type().id(), value));
                }
                resourcePorts[index] = new ResourcePort(storage, slotPools, resources.supportsLargeStacks(),
                        outputCapacities.computeIfAbsent(storage, ignoredStorage -> new HashMap<>()));
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
            boolean dynamicOutput = source.machineOutputs().stream().anyMatch(output ->
                    !OutputRegistry.isCanonical(output) || output.outputType().conversionToken() == null);
            RecipeSearchProof.Reason proof = !dynamicOutput ? null : routingOmitted
                    ? RecipeSearchProof.Reason.INCOMPLETE_ROUTING : RecipeSearchProof.Reason.DYNAMIC_OUTPUT;
            int proofIndex = -1;
            boolean material = dynamicOutput;
            for (int index = 0; index < recipe.requirements().size(); index++) {
                MachineRequirement requirement = recipe.requirements().get(index);
                boolean materialRule = requirement.usesInputScope();
                material |= materialRule;
                if (materialRule && routingOmitted) { proof = RecipeSearchProof.Reason.INCOMPLETE_ROUTING; proofIndex = index; }
                if (hasPrefetch && requirement.prefetchEnergyPerTick().isPresent()
                        && proof == null) { proof = RecipeSearchProof.Reason.LIVE_CAPABILITY; proofIndex = index; }
                SearchDemand demand = demand(requirement);
                if (demand == null) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.OPAQUE_REQUIREMENT; proofIndex = index; }
                    continue;
                }
                List<Integer> matching = matching(requirement, demand.ports());
                if (materialRule && (routingOmitted || routing != null && matching.stream().anyMatch(occurrence ->
                        !routedSources.contains(occurrence)))) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.INCOMPLETE_ROUTING; proofIndex = index; }
                }
                boolean live = liveMatches.computeIfAbsent(new LiveKey(demand.kind(), matching),
                        ignored -> matching.stream().anyMatch(occurrence -> {
                    AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
                    return switch (demand.kind()) {
                        case NOOP -> false;
                        case RESOURCE -> !(snapshot instanceof AsyncCapabilitySnapshot.Resource)
                                || resourcePorts[occurrence] == null || liveCapabilities[occurrence];
                        case SCALAR -> !(snapshot instanceof AsyncCapabilitySnapshot.Scalar) || liveCapabilities[occurrence]
                                || !(capabilities.get(occurrence).facet(ValueFacet.class).map(ValueFacet::storage).orElse(null) instanceof LongValueStorage);
                        case HEAT, TEMPERATURE -> !(snapshot instanceof AsyncCapabilitySnapshot.Heat) || liveCapabilities[occurrence];
                    };
                }));
                if (live) {
                    if (proof == null) { proof = RecipeSearchProof.Reason.LIVE_CAPABILITY; proofIndex = index; }
                    continue;
                }
                rules.add(rule(index, requirement, demand, source.allowPartialOutputs(), matching));
            }
            List<Scope> scopes = scopes(material);
            candidates.add(new Candidate(source.id(), source.priority(), source.inputRequirementCount(),
                    Math.min(maximumParallelism, recipe.parallelismLimit()), staticFailure == null ? null : staticFailure.id(),
                    rules, scopes, overlaps.computeIfAbsent(source, this::overlap), proof, proofIndex));
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

    private SearchDemand demand(MachineRequirement requirement) {
        if (!demands.containsKey(requirement)) demands.put(requirement, RequirementHandlerRegistry.captureSearch(requirement));
        return demands.get(requirement);
    }

    private List<Integer> matching(MachineRequirement requirement, SearchDemand.Ports ports) {
        MatchingKey key = new MatchingKey(requirement.type().id(), requirement.io(), requirement.tags(), ports.key());
        return matchingCapabilities.computeIfAbsent(key, ignored -> findMatching(requirement, ports));
    }

    private List<Integer> findMatching(MachineRequirement requirement, SearchDemand.Ports ports) {
        List<Integer> matching = new ArrayList<>();
        // Keep occurrence indexes, including repeated references to the very same capability object.
        var ids = requirement.type().capabilityIds();
        IOType direction = IOType.valueOf(requirement.io().name());
        for (int index = 0; index < capabilities.size(); index++) {
            MachineCapability capability = capabilities.get(index);
            if (!ids.contains(capability.view().type().id())
                    || !capability.view().directions().supports(direction)
                    || !requirement.tags().isEmpty() && requirement.tags().stream().noneMatch(capability.view()::matchesTag)) continue;
            if (!ports.accepts(capability)) continue;
            matching.add(index);
        }
        if (direction == IOType.OUTPUT) matching.sort(Comparator.comparingInt(
                (Integer index) -> capabilities.get(index).outputPriority()).reversed());
        return matching;
    }

    private Rule rule(int index, MachineRequirement requirement, SearchDemand demand, boolean partial, List<Integer> matching) {
        IOType direction = IOType.valueOf(requirement.io().name());
        boolean output = direction == IOType.OUTPUT;
        var value = demand.output() == null ? null : demand.output().value();
        var matcher = demand.matcher();
        Set<AsyncResourceValue> accepted = matcher == null ? Set.of() : matchingResources.computeIfAbsent(
                List.of(requirement.type().id(), matcher.key()), ignored -> Set.copyOf(matcher.select().apply(resources)));
        var layout = portLayouts.computeIfAbsent(new PortKey(requirement.type().id(), demand.kind(), direction, matching,
                value, demand.ports().key()), ignored -> capturePorts(demand, output, matching));
        FailureReason rejection = layout.rejection();
        if (output && rejection != null) {
            for (Port port : layout.ports()) {
                var slot = ((AsyncCapabilitySnapshot.Resource) pools.get(port.pool())).slots().getFirst();
                if ((slot.resource().isEmpty() || slot.resource().get().equals(value)) && port.capacity() > slot.amount()) {
                    rejection = null;
                    break;
                }
            }
        }
        var reason = demand.ports().failure(demand.failure(), matching.isEmpty(), rejection);
        boolean valid = demand.valid() && !(partial && output && demand.kind() == Kind.RESOURCE && value == null);
        return new Rule(index, requirement.type().id(), demand.kind(), direction, Math.max(0L, demand.amount()),
                demand.heat(), demand.chance(), demand.consumeChance(), output && partial, valid, accepted, value,
                layout.ports(), reason, requirement.usesInputScope());
    }

    private CapturedPorts capturePorts(SearchDemand demand, boolean output, List<Integer> matching) {
        Kind kind = demand.kind();
        List<Port> ports = new ArrayList<>();
        FailureReason rejection = null;
        for (int occurrence : matching) {
            MachineCapability capability = capabilities.get(occurrence);
            AsyncCapabilitySnapshot snapshot = snapshots.get(occurrence);
            if (kind == Kind.RESOURCE) {
                ResourcePort port = resourcePorts[occurrence];
                if (port == null) continue;
                var policy = demand.ports().admission(capability);
                if (policy.rejection() != null) rejection = policy.rejection();
                var resource = demand.output();
                long[] capacities = resource == null ? null : outputCapacities(port, resource.resource(), resource.value());
                for (int slot = 0; slot < port.pools().length; slot++) {
                    int pool = port.pools()[slot];
                    long capacity = capacities == null ? 0L : capacities[slot];
                    if (output && !policy.outputAllowed()) capacity = 0L;
                    ports.add(new Port(pool, occurrence, capacity, policy.inputLimit()));
                }
            } else if (kind == Kind.SCALAR && snapshot instanceof AsyncCapabilitySnapshot.Scalar) {
                ValueFacet<?> facet = capability.facet(ValueFacet.class).orElse(null);
                if (facet == null || !(facet.storage() instanceof LongValueStorage storage)) continue;
                int pool = valueCells.computeIfAbsent(storage, ignored -> {
                    pools.add(new AsyncCapabilitySnapshot.Scalar(capability.view().type().id(), storage.amount(),
                            storage.capacity(), storage.transferLimit()));
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
        return new CapturedPorts(List.copyOf(ports), rejection);
    }

    private long[] outputCapacities(ResourcePort port, Object resource, AsyncResourceValue value) {
        return port.outputCapacities().computeIfAbsent(new OutputPolicy(value, port.largeStacks()), ignored -> {
            long[] capacities = new long[port.pools().length];
            long stackLimit = resource instanceof ItemResource item && !port.largeStacks()
                    ? item.toStack(1).getMaxStackSize() : Long.MAX_VALUE;
            for (int slot = 0; slot < capacities.length; slot++) {
                if (port.storage().isValidResource(slot, resource)) {
                    capacities[slot] = Math.max(0L, Math.min(port.storage().capacityResource(slot, resource), stackLimit));
                }
            }
            return capacities;
        });
    }

    private int resourceCell(ResourceStorage<?> storage, int slot, AsyncCapabilitySnapshot.Resource captured) {
        Map<Object, Integer> cells = resourceCells.computeIfAbsent(storage.reservationIdentity(), ignored -> new HashMap<>());
        return cells.computeIfAbsent(storage.reservationSlot(slot), ignored -> {
            pools.add(new AsyncCapabilitySnapshot.Resource(captured.capabilityId(), List.of(captured.slots().get(slot))));
            return pools.size() - 1;
        });
    }

    private record MatchingKey(Identifier type, RecipeModifier.IOType direction, List<String> tags,
                               @Nullable Object policyKey) { }

    /** @author howxu <dev@howxu.cn> */
    private record LiveKey(Kind kind, List<Integer> occurrences) { }

    /** Capture-local slot mapping; native storage never enters the worker snapshot.
     * @author howxu <dev@howxu.cn>
     */
    private record ResourcePort(ResourceStorage<?> storage, int[] pools, boolean largeStacks,
                                Map<OutputPolicy, long[]> outputCapacities) { }

    /** @author howxu <dev@howxu.cn> */
    private record OutputPolicy(AsyncResourceValue resource, boolean largeStacks) { }
    /** @author howxu <dev@howxu.cn> */
    private record PortKey(Identifier type, Kind kind, IOType direction, List<Integer> occurrences, @Nullable AsyncResourceValue output,
                           @Nullable Object chemical) { }
    /** @author howxu <dev@howxu.cn> */
    private record CapturedPorts(List<Port> ports, @Nullable FailureReason rejection) { }

    private Overlap overlap(MachineRecipe recipe) {
        List<SearchOverlap> inputs = new ArrayList<>();
        boolean opaque = false;
        for (MachineRequirement requirement : recipe.requirements()) {
            if (requirement.io() != RecipeModifier.IOType.INPUT) continue;
            var evidence = RequirementHandlerRegistry.captureOverlap(requirement);
            if (evidence == null) opaque = true;
            else inputs.addAll(evidence);
        }
        return new Overlap(inputs, opaque);
    }

}

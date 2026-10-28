package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.util.SaturatingLong;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReport;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport.Sampling;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.ae2lt.LightningFailureReasons;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plans prepared requirement requests without retaining live capability state.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AsyncRequirementPlanner {
    /** Captures immutable recipe planning inputs on the server thread for a later worker search. */
    public static RecipeSearchRequest captureRecipeSearch(ControllerRuntimeSnapshot snapshot,
                                                           List<MachineRecipe> candidates, long maxParallelism,
                                                           List<MachineCapability> capabilities,
                                                           List<MachineModifier> modifiers,
                                                           long catalogVersion,
                                                           EffectiveRecipeSet.Cache effectiveRecipeCache) {
        return captureRecipeSearch(snapshot, candidates, maxParallelism,
                new CapabilitySnapshot(capabilities == null ? List.of() : capabilities), modifiers,
                catalogVersion, effectiveRecipeCache, true);
    }

    /** Captures capabilities together with controller-specific input routing at the main-thread boundary. */
    public static RecipeSearchRequest captureRecipeSearch(ControllerRuntimeSnapshot snapshot,
                                                           List<MachineRecipe> candidates, long maxParallelism,
                                                           CapabilitySnapshot capabilities,
                                                           List<MachineModifier> modifiers,
                                                           long catalogVersion,
                                                           EffectiveRecipeSet.Cache effectiveRecipeCache) {
        return captureRecipeSearch(snapshot, candidates, maxParallelism, capabilities, modifiers,
                catalogVersion, effectiveRecipeCache, false);
    }

    private static RecipeSearchRequest captureRecipeSearch(ControllerRuntimeSnapshot snapshot,
                                                            List<MachineRecipe> candidates, long maxParallelism,
                                                            CapabilitySnapshot capabilities,
                                                            List<MachineModifier> modifiers,
                                                            long catalogVersion,
                                                            EffectiveRecipeSet.Cache effectiveRecipeCache,
                                                            boolean routingOmitted) {
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        if (machine == null) throw new IllegalStateException("Recipe search has no machine");
        List<MachineRecipe> orderedCandidates = (candidates == null ? List.<MachineRecipe>of() : candidates).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
        EffectiveRecipeSet effectiveRecipes = effectiveRecipeCache.resolve(snapshot, catalogVersion,
                orderedCandidates, modifiers);
        RecipeSearchSnapshot values = RecipeSearchCapture.capture(snapshot, catalogVersion, Math.max(1L, maxParallelism),
                effectiveRecipes.recipes(), capabilities, routingOmitted);
        return new RecipeSearchRequest(snapshot, machine.registryName(), snapshot.structure().version(), maxParallelism,
                orderedCandidates, capabilities, effectiveRecipes, values, MachineModifier.recipeModifiers(modifiers));
    }

    public PlanResult plan(List<Requirement> requirements, List<Capability> capabilities) {
        return plan(requirements, capabilities, null, InputScope.legacy());
    }

    public PlanResult plan(List<Requirement> requirements, List<Capability> capabilities,
                           @Nullable InputRoutingFacet routing, InputScope scope) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(capabilities, "capabilities");
        List<AsyncCapabilitySnapshot> snapshots = capabilities.stream().map(Capability::snapshot).toList();
        List<PlannedOperation> operations = new ArrayList<>();
        List<Integer> mainThreadRequirements = new ArrayList<>();

        for (Requirement requirement : requirements) {
            boolean material = requirement.requests().stream().anyMatch(request ->
                    RequirementPlanner.isMaterialInput(IOType.INPUT, request.capabilityId()));
            boolean constrained = scope.kind() != InputScope.Kind.LEGACY;
            if (material && constrained && (requirement.direction() == null
                    || requirement.direction() == IOType.INPUT && (routing == null
                    || capabilities.stream().anyMatch(capability -> capability.directions().contains(IOType.INPUT)
                    && requirement.requests().stream().anyMatch(request ->
                    request.capabilityId().equals(capability.capabilityId()))
                    && (capability.sourceCapabilityIndex() < 0 || routing.sources().stream().noneMatch(source ->
                    source.capabilityIndex() == capability.sourceCapabilityIndex())))))) {
                mainThreadRequirements.add(requirement.index());
                continue;
            }
            List<AsyncCapabilitySnapshot> plannedSnapshots = new ArrayList<>(snapshots);
            List<PlannedOperation> plannedOperations = new ArrayList<>();
            long remaining = requirement.amount();
            for (AsyncCapabilityRequest request : requirement.requests()) {
                for (int capabilityIndex = 0; capabilityIndex < capabilities.size() && remaining > 0L; capabilityIndex++) {
                    Capability capability = capabilities.get(capabilityIndex);
                    if (constrained && RequirementPlanner.isMaterialInput(requirement.direction(), request.capabilityId())
                            && (routing == null || !routing.allows(scope, capability.sourceCapabilityIndex()))) continue;
                    if (requirement.direction() != null && !capability.directions().contains(requirement.direction())) {
                        continue;
                    }
                    AsyncCapabilityRequest remainingRequest = forAmount(request, remaining,
                            plannedSnapshots.get(capabilityIndex));
                    if (remainingRequest == null) continue;
                    var operation = capability.planner().plan(plannedSnapshots.get(capabilityIndex), remainingRequest);
                    if (operation.isEmpty()) continue;
                    long plannedAmount = amount(operation.get());
                    if (plannedAmount <= 0L) continue;
                    AsyncCapabilitySnapshot previous = plannedSnapshots.get(capabilityIndex);
                    AsyncCapabilitySnapshot updated = apply(previous, operation.get());
                    for (int snapshotIndex = 0; snapshotIndex < plannedSnapshots.size(); snapshotIndex++) {
                        if (plannedSnapshots.get(snapshotIndex) == previous) plannedSnapshots.set(snapshotIndex, updated);
                    }
                    plannedOperations.add(new PlannedOperation(requirement.index(), capabilityIndex, operation.get()));
                    remaining -= plannedAmount;
                }
            }
            if (remaining == 0L) {
                snapshots = plannedSnapshots;
                operations.addAll(plannedOperations);
            } else {
                mainThreadRequirements.add(requirement.index());
            }
        }
        return new PlanResult(operations, mainThreadRequirements);
    }

    private static @Nullable AsyncCapabilityRequest forAmount(AsyncCapabilityRequest request, long amount,
                                                               AsyncCapabilitySnapshot snapshot) {
        if (request instanceof AsyncCapabilityRequest.Resource(
                Identifier id, long parallelism1, List<AsyncResourceAction> actions
        ) && actions.size() == 1) {
            AsyncResourceAction action = actions.getFirst();
            if (!(snapshot instanceof AsyncCapabilitySnapshot.Resource resource)
                    || !id.equals(resource.capabilityId())) return null;
            long available = 0L;
            for (AsyncCapabilitySnapshot.ResourceSlot slot : resource.slots()) {
                if (action.insert() && (slot.resource().isEmpty() || slot.resource().get().equals(action.resource()))) {
                    available = SaturatingLong.add(available, slot.capacity() - slot.amount());
                } else if (!action.insert() && slot.resource().filter(action.resource()::equals).isPresent()) {
                    available = SaturatingLong.add(available, slot.amount());
                }
            }
            long requested = Math.min(Math.min(action.amount(), amount), available);
            if (requested <= 0L) return null;
            return new AsyncCapabilityRequest.Resource(id, parallelism1, List.of(
                    new AsyncResourceAction(action.resource(), requested, action.insert())));
        }
        if (request instanceof AsyncCapabilityRequest.Scalar(
                Identifier capabilityId, long parallelism, long amount1, boolean insert
        )) {
            return new AsyncCapabilityRequest.Scalar(capabilityId, parallelism,
                    Math.min(amount1, amount), insert);
        }
        return request;
    }

    private static long amount(AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Resource resource) return resource.amount();
        if (operation instanceof AsyncCapabilityOperation.Scalar scalar) return scalar.amount();
        if (operation instanceof AsyncCapabilityOperation.Heat heat) return heat.accountingAmount();
        return ((AsyncCapabilityOperation.Group) operation).operations().stream()
                .mapToLong(AsyncRequirementPlanner::amount).sum();
    }

    public static AsyncCapabilitySnapshot apply(AsyncCapabilitySnapshot snapshot, AsyncCapabilityOperation operation) {
        if (snapshot instanceof AsyncCapabilitySnapshot.Resource(
                Identifier id, List<AsyncCapabilitySnapshot.ResourceSlot> slots1
        )) {
            List<AsyncCapabilitySnapshot.ResourceSlot> slots = new ArrayList<>(slots1);
            applyResource(slots, operation);
            return new AsyncCapabilitySnapshot.Resource(id, slots);
        }
        if (snapshot instanceof AsyncCapabilitySnapshot.Scalar(
                Identifier capabilityId, long amount1, long capacity, long transferLimit
        )
                && operation instanceof AsyncCapabilityOperation.Scalar value) {
            long amount = value.insert() ? amount1 + value.amount() : amount1 - value.amount();
            return new AsyncCapabilitySnapshot.Scalar(capabilityId, amount, capacity,
                    transferLimit);
        }
        if (snapshot instanceof AsyncCapabilitySnapshot.Scalar && operation instanceof AsyncCapabilityOperation.Group(
                List<AsyncCapabilityOperation> operations
        )) {
            AsyncCapabilitySnapshot planned = snapshot;
            for (AsyncCapabilityOperation child : operations) {
                planned = apply(planned, child);
            }
            return planned;
        }
        if (snapshot instanceof AsyncCapabilitySnapshot.Heat heatSnapshot
                && operation instanceof AsyncCapabilityOperation.Heat heatOperation) {
            if (heatOperation.minimumTemperature()) return heatSnapshot;
            double heat = heatSnapshot.heat() + heatOperation.value();
            double temperature = heatSnapshot.temperature() + heatOperation.value() / heatSnapshot.capacity();
            return new AsyncCapabilitySnapshot.Heat(heatSnapshot.capabilityId(), heat, temperature,
                    heatSnapshot.capacity());
        }
        throw new IllegalArgumentException("operation does not match capability snapshot");
    }

    private static void applyResource(List<AsyncCapabilitySnapshot.ResourceSlot> slots,
                                      AsyncCapabilityOperation operation) {
        if (operation instanceof AsyncCapabilityOperation.Group(List<AsyncCapabilityOperation> operations)) {
            operations.forEach(child -> applyResource(slots, child));
            return;
        }
        if (!(operation instanceof AsyncCapabilityOperation.Resource resource)) {
            throw new IllegalArgumentException("resource snapshot requires resource operations");
        }
        AsyncCapabilitySnapshot.ResourceSlot slot = slots.get(resource.slot());
        long amount = resource.insert() ? slot.amount() + resource.amount() : slot.amount() - resource.amount();
        slots.set(resource.slot(), new AsyncCapabilitySnapshot.ResourceSlot(
                amount == 0L ? java.util.Optional.empty() : java.util.Optional.of(resource.resource()), amount,
                slot.capacity()));
    }

    /** A pure planner and immutable snapshot captured for one capability. */
    public record Capability(AsyncCapabilityPlanner planner, AsyncCapabilitySnapshot snapshot, Set<IOType> directions,
                             int sourceCapabilityIndex) {
        public Identifier capabilityId() {
            return switch (snapshot) {
                case AsyncCapabilitySnapshot.Resource resource -> resource.capabilityId();
                case AsyncCapabilitySnapshot.Scalar scalar -> scalar.capabilityId();
                case AsyncCapabilitySnapshot.Heat heat -> heat.capabilityId();
            };
        }

        public Capability {
            Objects.requireNonNull(planner, "planner");
            Objects.requireNonNull(snapshot, "snapshot");
            directions = Set.copyOf(Objects.requireNonNull(directions, "directions"));
        }

        public Capability(AsyncCapabilityPlanner planner, AsyncCapabilitySnapshot snapshot) {
            this(planner, snapshot, Set.of(IOType.INPUT, IOType.OUTPUT));
        }

        public Capability(AsyncCapabilityPlanner planner, AsyncCapabilitySnapshot snapshot, Set<IOType> directions) {
            this(planner, snapshot, directions, -1);
        }
    }

    /** A main-thread-prepared, worker-safe recipe requirement. */
    public record Requirement(int index, long amount, IOType direction, List<AsyncCapabilityRequest> requests) {
        public Requirement {
            if (index < 0 || amount <= 0L) throw new IllegalArgumentException("index must be non-negative and amount positive");
            requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
            if (requests.isEmpty()) throw new IllegalArgumentException("requests must not be empty");
        }

        public Requirement(int index, long amount, List<AsyncCapabilityRequest> requests) {
            this(index, amount, null, requests);
        }
    }

    /** A logical operation assigned to the captured capability that produced it. */
    public record PlannedOperation(int requirementIndex, int capabilityIndex, AsyncCapabilityOperation operation) {
        public PlannedOperation {
            if (requirementIndex < 0 || capabilityIndex < 0) {
                throw new IllegalArgumentException("indexes must be non-negative");
            }
            Objects.requireNonNull(operation, "operation");
        }
    }

    /** Worker planning result with ordered fallback requirement indexes. */
    public record PlanResult(List<PlannedOperation> operations, List<Integer> mainThreadRequirements) {
        public PlanResult {
            operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
            mainThreadRequirements = List.copyOf(Objects.requireNonNull(mainThreadRequirements, "mainThreadRequirements"));
        }
    }

    /** A worker-safe planning descriptor prepared from live capabilities on the main thread. */
    public record PreparedPlan(List<Requirement> requirements, List<Capability> capabilities,
                               List<Integer> initialMainThreadRequirements,
                               @Nullable InputRoutingFacet routing, InputScope inputScope) {
        public PreparedPlan(List<Requirement> requirements, List<Capability> capabilities,
                            List<Integer> initialMainThreadRequirements) {
            this(requirements, capabilities, initialMainThreadRequirements, null, InputScope.legacy());
        }

        public PreparedPlan {
            requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
            capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            initialMainThreadRequirements = List.copyOf(Objects.requireNonNull(initialMainThreadRequirements,
                    "initialMainThreadRequirements"));
            Objects.requireNonNull(inputScope, "inputScope");
        }

        public PlanResult plan() {
            PlanResult result = new AsyncRequirementPlanner().plan(requirements, capabilities, routing, inputScope);
            TreeSet<Integer> fallback = new TreeSet<>(initialMainThreadRequirements);
            fallback.addAll(result.mainThreadRequirements());
            return new PlanResult(result.operations(), List.copyOf(fallback));
        }
    }

    /** Immutable outcome of a worker-side recipe candidate search. */
    public record RecipeSearchResult(@Nullable cn.howxu.mmcr.api.recipe.RecipeSearchResult result,
                                     @Nullable RuntimeException failure, boolean requiresMainThreadReplan) {
    }

    /** Server-owned source mapping and proof context; only snapshotValues is submitted to the planner. */
    public static final class RecipeSearchRequest {
        private final ControllerRuntimeSnapshot snapshot;
        private final Identifier machineId;
        private final long structureVersion;
        private final long maxParallelism;
        private final List<MachineRecipe> candidates;
        private final CapabilitySnapshot capabilities;
        private final EffectiveRecipeSet effectiveRecipes;
        private final RecipeSearchSnapshot values;
        private final List<RecipeModifier> modifiers;
        private final Map<Identifier, FailureReason> reasons = new LinkedHashMap<>();
        private final Map<RecipeSearchPlanner.Failure, ExecutionStatus> proofFailures = new LinkedHashMap<>();

        private RecipeSearchRequest(ControllerRuntimeSnapshot snapshot, Identifier machineId, long structureVersion,
                                     long maxParallelism, List<MachineRecipe> candidates, CapabilitySnapshot capabilities,
                                     EffectiveRecipeSet effectiveRecipes, RecipeSearchSnapshot values,
                                     List<RecipeModifier> modifiers) {
            this.snapshot = snapshot;
            this.machineId = machineId;
            this.structureVersion = structureVersion;
            this.maxParallelism = Math.max(1L, maxParallelism);
            this.candidates = List.copyOf(candidates);
            this.capabilities = capabilities;
            this.effectiveRecipes = effectiveRecipes;
            this.values = values;
            this.modifiers = List.copyOf(modifiers);
            for (FailureReason reason : List.of(BuiltinFailureReasons.MISSING_INPUT, BuiltinFailureReasons.MISSING_OUTPUT,
                    BuiltinFailureReasons.MISSING_ENERGY, BuiltinFailureReasons.LEVEL_INSUFFICIENT,
                    BuiltinFailureReasons.STAGE_INSUFFICIENT, BuiltinFailureReasons.MODULE_CONNECTION,
                    BuiltinFailureReasons.RECIPE_SEARCH, MekanismFailureReasons.CHEMICAL_TYPE_MISMATCH,
                    MekanismFailureReasons.CHEMICAL_INPUT_MISSING, MekanismFailureReasons.CHEMICAL_OUTPUT_BLOCKED,
                    MekanismFailureReasons.CHEMICAL_RADIOACTIVITY_REJECTED,
                    MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT, MekanismFailureReasons.HEAT_OUTPUT_BLOCKED,
                    LightningFailureReasons.INPUT_MISSING, LightningFailureReasons.OUTPUT_BLOCKED)) {
                reasons.put(reason.id(), reason);
            }
            // Value-captured native rules use only the reasons above. Opaque extensions return
            // their original ExecutionStatus through proofFailures, not through this native table.
        }

        public ControllerRuntimeSnapshot snapshot() { return snapshot; }
        public long structureVersion() { return structureVersion; }
        public long maxParallelism() { return maxParallelism; }
        public List<MachineRecipe> candidates() { return candidates; }
        public @Nullable InputRoutingFacet inputRouting() {
            return capabilities.facets(InputRoutingFacet.class).stream().findFirst().orElse(null);
        }
        public EffectiveRecipeSet effectiveRecipes() { return effectiveRecipes; }
        public RecipeSearchSnapshot snapshotValues() { return values; }
        public RecipeSearchPlanner newPlanner() { return new RecipeSearchPlanner(values); }

        /** Compatibility value search. An unresolved proof is not an optimistic candidate success. */
        public RecipeSearchResult search() { return result(newPlanner().advance()); }

        /** Converts completed values to the existing execution handle, without invoking a pool search. */
        public RecipeSearchResult result(RecipeSearchPlanner.Outcome outcome) {
            if (outcome instanceof RecipeSearchPlanner.NeedsProof) return new RecipeSearchResult(null, null, true);
            if (outcome instanceof RecipeSearchPlanner.Failed failed) {
                RuntimeException cause = failed.cause() instanceof RuntimeException exception ? exception
                        : new IllegalStateException("Async recipe search failed", failed.cause());
                return new RecipeSearchResult(null, cause, false);
            }
            if (outcome instanceof RecipeSearchPlanner.Match match) {
                PlanningResult plan = new PlanningResult(new CraftingPlan(List.of(), match.parallelism(), Map.of(),
                        match.scope(), values.stamp().routing()), null);
                return new RecipeSearchResult(cn.howxu.mmcr.api.recipe.RecipeSearchResult.success(
                        candidates.get(match.candidateIndex()), machineId, structureVersion, snapshot.capabilityVersion(),
                        snapshot.modifierVersion(), plan, match.conflictProne()), null, false);
            }
            List<FailureReport.Candidate> failures = new ArrayList<>();
            float validity = 0F;
            for (RecipeSearchPlanner.Failure failure : ((RecipeSearchPlanner.NoMatch) outcome).failures()) {
                RecipeSearchSnapshot.Candidate candidate = values.candidates().get(failure.candidateIndex());
                Identifier type = requirementType(candidate, failure.requirementIndex());
                ExecutionStatus status = proofFailures.get(failure);
                FailurePhase phase = failure.requirementIndex() >= 0 ? FailurePhase.REQUIREMENT_PLAN
                        : failure.reasonId().equals(BuiltinFailureReasons.LEVEL_INSUFFICIENT.id())
                        ? FailurePhase.LEVEL_CHECK : FailurePhase.RECIPE_SEARCH;
                if (status == null) {
                    FailureOccurrence occurrence = FailureOccurrence.at(reasons.getOrDefault(failure.reasonId(),
                            BuiltinFailureReasons.RECIPE_SEARCH), type, phase, candidate.id(),
                            failure.requirementIndex() < 0 ? null : failure.requirementIndex(), Map.of());
                    status = ExecutionStatus.blocked(type, type, occurrence);
                }
                if (status.failure() != null && phase != FailurePhase.RECIPE_SEARCH) {
                    status = new ExecutionStatus(status.id(), status.severity(), status.source(), status.failure()
                            .append(MMCR.id("crafting_runtime"), FailurePhase.RECIPE_SEARCH, candidate.id(),
                                    failure.requirementIndex() < 0 ? null : failure.requirementIndex()));
                }
                float progress = phase == FailurePhase.LEVEL_CHECK ? 1F : failure.completedRequirements()
                        + (status.severity() == StatusSeverity.BLOCKED ? 0.5F : 0.1F);
                failures.add(new FailureReport.Candidate(status, progress));
            }
            FailureReport report = new FailureReport(failures, FailureReport.Selection.REQUIREMENT_PROGRESS);
            ExecutionStatus primary = report.primary();
            for (FailureReport.Candidate failure : report.candidates()) {
                if (failure.status() == primary) validity = failure.validity();
            }
            return new RecipeSearchResult(cn.howxu.mmcr.api.recipe.RecipeSearchResult.failure(machineId,
                    structureVersion, snapshot.capabilityVersion(), snapshot.modifierVersion(), report, validity), null, false);
        }

        private static Identifier requirementType(RecipeSearchSnapshot.Candidate candidate, int index) {
            List<RecipeSearchSnapshot.Rule> rules = candidate.rules();
            if (index >= 0 && index < rules.size() && rules.get(index).index() == index) return rules.get(index).type();
            // Opaque requirements can leave gaps in the captured rule indexes.
            for (RecipeSearchSnapshot.Rule rule : rules) if (rule.index() == index) return rule.type();
            return MMCR.id("crafting_runtime");
        }

        /** Main-thread-only compatibility boundary, limited to one candidate or one overlap pair. */
        public RecipeSearchProof.Result prove(RecipeSearchProof.Request request) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || !server.isSameThread()) throw new IllegalStateException("Search proof requires the server thread");
            try {
                if (request.kind() == RecipeSearchProof.Kind.OVERLAP) {
                    return new RecipeSearchProof.OverlapResult(request.sequence(), request.candidateIndex(),
                            request.relatedCandidateIndex(), candidates.get(request.relatedCandidateIndex())
                            .hasOverlappingInputs(candidates.get(request.candidateIndex())));
                }
                if (request.reason() == RecipeSearchProof.Reason.INCOMPLETE_ROUTING && inputRouting() == null) {
                    return new RecipeSearchProof.Failure(request.sequence(),
                            new IllegalStateException("Cannot prove material inputs after routing metadata was omitted"));
                }
                MachineRecipe candidate = candidates.get(request.candidateIndex());
                Sampling sampling = new Sampling();
                List<RecipeSearchPlanner.Failure> failures = new ArrayList<>();
                for (InputScope scope : request.scopes()) {
                    CraftingContext context = new CraftingContext(capabilities, modifiers, scope, sampling);
                    PlanningResult result = context.planStartResult(candidate, request.maximumParallelism());
                    Identifier staticFailure = values.candidates().get(request.candidateIndex()).staticFailure();
                    if (result.successful() && staticFailure == null) return new RecipeSearchProof.CandidateResult(request.sequence(),
                            request.candidateIndex(), true, result.plan().parallelism(), result.plan().inputScope(), List.of());
                    boolean retainedPresent = true;
                    if (scope.kind() != InputScope.Kind.LEGACY) {
                        List<MachineRequirement> retained = candidate.runtimeRequirements(modifiers).stream()
                                .filter(requirement -> requirement.io() == RecipeModifier.IOType.INPUT
                                        && (requirement instanceof ItemRequirement item && item.consumeChance() == 0F
                                        || requirement instanceof FluidRequirement fluid
                                        && fluid.consumeChance() == 0F
                                        || requirement instanceof LoadedChemicalRequirement chemical
                                        && chemical.consumeChance() == 0F)).toList();
                        retainedPresent = retained.isEmpty() || context.planInputs(retained, 1L, Set.of(), Set.of()).successful();
                    }
                    boolean inputWaiting = retainedPresent && !context.planInputs(candidate, request.maximumParallelism()).successful()
                            && context.planOutputs(candidate, request.maximumParallelism()).successful();
                    Identifier reason = result.failure() == null
                            ? staticFailure == null ? BuiltinFailureReasons.RECIPE_SEARCH.id() : staticFailure
                            : result.failure().reason().id();
                    int index = result.failureRequirementIndex() == null ? -1 : result.failureRequirementIndex();
                    RecipeSearchPlanner.Failure failure = new RecipeSearchPlanner.Failure(request.candidateIndex(), index, reason,
                            Math.max(0, index), inputWaiting, scope);
                    if (result.failure() != null) proofFailures.put(failure, result.failure());
                    failures.add(failure);
                }
                return new RecipeSearchProof.CandidateResult(request.sequence(), request.candidateIndex(), false, 0L, null, failures);
            } catch (RuntimeException exception) {
                return new RecipeSearchProof.Failure(request.sequence(), exception);
            }
        }
    }

    public static @Nullable FailureReason capturedRequirementFailure(ControllerRuntimeSnapshot snapshot,
                                                                        MachineRecipe recipe) {
        return capturedRequirementFailure(snapshot, recipe.requirements());
    }

    /** Static conditions also apply to the effective requirements produced by beforeStart. */
    public static @Nullable FailureReason capturedRequirementFailure(ControllerRuntimeSnapshot snapshot,
                                                                      List<MachineRequirement> requirements) {
        for (MachineRequirement candidate : requirements) {
            if (!(candidate instanceof LevelRequirement requirement)) continue;
            MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
            MachineLevel actual = snapshot.foundLevels().get(requirement.typeId());
            if (required == null || actual == null || actual.priority() < required.priority()) {
                return BuiltinFailureReasons.LEVEL_INSUFFICIENT;
            }
        }
        int actualStage = Math.max(1, snapshot.structure().matchedStage());
        for (MachineRequirement candidate : requirements) {
            if (!(candidate instanceof StageRequirement requirement)) continue;
            if (actualStage < requirement.minStage()) return BuiltinFailureReasons.STAGE_INSUFFICIENT;
        }
        return null;
    }
}

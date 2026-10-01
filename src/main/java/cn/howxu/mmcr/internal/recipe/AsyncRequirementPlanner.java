package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        Machine machine = snapshot.structure().machine() == null
                ? snapshot.structure().configuredMachine() : snapshot.structure().machine();
        if (machine == null) throw new IllegalStateException("Recipe search has no machine");
        List<MachineRecipe> orderedCandidates = (candidates == null ? List.<MachineRecipe>of() : candidates).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
        Set<ResourceLocation> capabilityIds = new java.util.LinkedHashSet<>();
        for (MachineRecipe candidate : orderedCandidates) {
            for (MachineRequirement requirement : candidate.requirements()) {
                capabilityIds.addAll(requirement.type().capabilityIds());
                if (requirement instanceof cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement) {
                    capabilityIds.add(cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes.HEAT);
                }
            }
            candidate.outputsWithoutDerivedRequirements().forEach(output -> capabilityIds.add(output.outputType().id()));
        }
        List<Capability> asyncCapabilities = new CraftingContext(new CapabilitySnapshot(capabilities), List.of())
                .captureAsyncCapabilities(capabilityIds);
        return new RecipeSearchRequest(snapshot, machine.registryName(), snapshot.structure().version(), maxParallelism,
                orderedCandidates, modifiers, asyncCapabilities, catalogVersion,
                effectiveRecipeCache);
    }

    public PlanResult plan(List<Requirement> requirements, List<Capability> capabilities) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(capabilities, "capabilities");
        List<AsyncCapabilitySnapshot> snapshots = capabilities.stream().map(Capability::snapshot).toList();
        List<PlannedOperation> operations = new ArrayList<>();
        List<Integer> mainThreadRequirements = new ArrayList<>();

        for (Requirement requirement : requirements) {
            List<AsyncCapabilitySnapshot> plannedSnapshots = new ArrayList<>(snapshots);
            List<PlannedOperation> plannedOperations = new ArrayList<>();
            long remaining = requirement.amount();
            for (AsyncCapabilityRequest request : requirement.requests()) {
                for (int capabilityIndex = 0; capabilityIndex < capabilities.size() && remaining > 0L; capabilityIndex++) {
                    Capability capability = capabilities.get(capabilityIndex);
                    if (requirement.direction() != null && !capability.directions().contains(requirement.direction())) {
                        continue;
                    }
                    AsyncCapabilityRequest remainingRequest = forAmount(request, remaining);
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

    private static AsyncCapabilityRequest forAmount(AsyncCapabilityRequest request, long amount) {
        if (request instanceof AsyncCapabilityRequest.Resource(
                ResourceLocation id, long parallelism1, List<AsyncResourceAction> actions
        ) && actions.size() == 1) {
            AsyncResourceAction action = actions.getFirst();
            return new AsyncCapabilityRequest.Resource(id, parallelism1, List.of(
                    new AsyncResourceAction(action.resource(), Math.min(action.amount(), amount), action.insert())));
        }
        if (request instanceof AsyncCapabilityRequest.Scalar(
                ResourceLocation capabilityId, long parallelism, long amount1, boolean insert
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
                ResourceLocation id, List<AsyncCapabilitySnapshot.ResourceSlot> slots1
        )) {
            List<AsyncCapabilitySnapshot.ResourceSlot> slots = new ArrayList<>(slots1);
            applyResource(slots, operation);
            return new AsyncCapabilitySnapshot.Resource(id, slots);
        }
        if (snapshot instanceof AsyncCapabilitySnapshot.Scalar(
                ResourceLocation capabilityId, long amount1, long capacity, long transferLimit
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
    public record Capability(AsyncCapabilityPlanner planner, AsyncCapabilitySnapshot snapshot, Set<IOType> directions) {
        public Capability {
            Objects.requireNonNull(planner, "planner");
            Objects.requireNonNull(snapshot, "snapshot");
            directions = Set.copyOf(Objects.requireNonNull(directions, "directions"));
        }

        public Capability(AsyncCapabilityPlanner planner, AsyncCapabilitySnapshot snapshot) {
            this(planner, snapshot, Set.of(IOType.INPUT, IOType.OUTPUT));
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
                               List<Integer> initialMainThreadRequirements) {
        public PreparedPlan {
            requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
            capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            initialMainThreadRequirements = List.copyOf(Objects.requireNonNull(initialMainThreadRequirements,
                    "initialMainThreadRequirements"));
        }

        public PlanResult plan() {
            PlanResult result = new AsyncRequirementPlanner().plan(requirements, capabilities);
            TreeSet<Integer> fallback = new TreeSet<>(initialMainThreadRequirements);
            fallback.addAll(result.mainThreadRequirements());
            return new PlanResult(result.operations(), List.copyOf(fallback));
        }
    }

    /** Immutable outcome of a worker-side recipe candidate search. */
    public record RecipeSearchResult(@Nullable cn.howxu.mmcr.api.recipe.RecipeSearchResult result,
                                     @Nullable RuntimeException failure, boolean requiresMainThreadReplan) {
    }

    /** Immutable main-thread capture whose worker method performs only pure planning. */
    public static final class RecipeSearchRequest {
        private final ControllerRuntimeSnapshot snapshot;
        private final ResourceLocation machineId;
        private final long structureVersion;
        private final long maxParallelism;
        private final List<MachineRecipe> candidates;
        private final List<MachineModifier> modifiers;
        private final List<Capability> capabilities;
        private final long catalogVersion;
        private final EffectiveRecipeSet.Cache effectiveRecipeCache;
        private volatile @Nullable EffectiveRecipeSet effectiveRecipes;

        private RecipeSearchRequest(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId, long structureVersion,
                                     long maxParallelism, List<MachineRecipe> candidates,
                                     List<MachineModifier> modifiers, List<Capability> capabilities,
                                     long catalogVersion, EffectiveRecipeSet.Cache effectiveRecipeCache) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.machineId = Objects.requireNonNull(machineId, "machineId");
            this.structureVersion = structureVersion;
            this.maxParallelism = Math.max(1L, maxParallelism);
            this.candidates = List.copyOf(candidates);
            this.modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
            this.capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            this.catalogVersion = catalogVersion;
            this.effectiveRecipeCache = Objects.requireNonNull(effectiveRecipeCache, "effectiveRecipeCache");
        }

        public ControllerRuntimeSnapshot snapshot() { return snapshot; }
        public long structureVersion() { return structureVersion; }
        public long maxParallelism() { return maxParallelism; }
        public List<MachineRecipe> candidates() { return candidates; }
        public @Nullable EffectiveRecipeSet effectiveRecipes() { return effectiveRecipes; }

        /** Runs without live capabilities, block entities, chunks, or recipe callbacks. */
        public RecipeSearchResult search() {
            try {
                effectiveRecipes = effectiveRecipeCache.resolve(snapshot, catalogVersion, candidates, modifiers);
                List<RecipeSearchTask.PlanningValue> planningValues = new ArrayList<>(effectiveRecipes.recipes().size());
                for (EffectiveRecipe candidate : effectiveRecipes.recipes()) {
                    FailureReason capturedFailure = capturedRequirementFailure(snapshot, candidate.source());
                    if (capturedFailure != null) {
                        planningValues.add(RecipeSearchTask.PlanningValue.failure(candidate.source().id(),
                                capturedFailure, 0, false));
                        continue;
                    }
                    long parallelism = Math.min(maxParallelism, candidate.parallelismLimit());
                    PreparedPlan prepared = CraftingContext.prepareAsyncPlan(candidate.requirements(), parallelism,
                            capabilities);
                    PlanResult plan = prepared.plan();
                    planningValues.add(plan.mainThreadRequirements().isEmpty()
                            ? RecipeSearchTask.PlanningValue.success(candidate.source().id())
                            : RecipeSearchTask.PlanningValue.mainThread(candidate.source().id()));
                }
                cn.howxu.mmcr.api.recipe.RecipeSearchResult result = RecipeSearchTask.forPlanningValues(snapshot, machineId,
                        structureVersion, maxParallelism, candidates, planningValues).compute();
                return new RecipeSearchResult(result, null, requiresMainThreadReplan(result, planningValues));
            } catch (RuntimeException exception) {
                return new RecipeSearchResult(null, exception, false);
            }
        }

        private boolean requiresMainThreadReplan(cn.howxu.mmcr.api.recipe.RecipeSearchResult result,
                                                 List<RecipeSearchTask.PlanningValue> planningValues) {
            if (!result.success()) return false;
            MachineRecipe selected = result.recipe();
            RecipeSearchTask.PlanningValue selectedValue = planningValues.stream()
                    .filter(value -> selected.id().equals(value.recipeId())).findFirst().orElse(null);
            if (selectedValue == null || selectedValue.requiresMainThread()) return true;
            for (MachineRecipe earlier : candidates) {
                if (earlier == selected) break;
                if (earlier.priority() != selected.priority()
                        || earlier.inputRequirementCount() <= selected.inputRequirementCount()
                        || !earlier.hasOverlappingInputs(selected)
                        || !snapshot.moduleConnectionStatus().canRunRecipe(earlier.requiredHostIds())) continue;
                if (planningValues.stream().filter(value -> earlier.id().equals(value.recipeId()))
                        .anyMatch(RecipeSearchTask.PlanningValue::requiresMainThread)) return true;
            }
            return false;
        }
    }

    public static @Nullable FailureReason capturedRequirementFailure(ControllerRuntimeSnapshot snapshot,
                                                                       MachineRecipe recipe) {
        for (LevelRequirement requirement : recipe.levelRequirements()) {
            MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
            MachineLevel actual = snapshot.foundLevels().get(requirement.typeId());
            if (required == null || actual == null || actual.priority() < required.priority()) {
                return BuiltinFailureReasons.LEVEL_INSUFFICIENT;
            }
        }
        int actualStage = Math.max(1, snapshot.structure().matchedStage());
        for (StageRequirement requirement : recipe.stageRequirements()) {
            if (actualStage < requirement.minStage()) return BuiltinFailureReasons.STAGE_INSUFFICIENT;
        }
        return null;
    }
}

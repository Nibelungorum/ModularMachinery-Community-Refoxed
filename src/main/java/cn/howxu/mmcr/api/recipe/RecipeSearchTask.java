package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReport;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Searches published controller state with the current execution capabilities and returns a recipe handle.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchTask {
    private final ControllerRuntimeSnapshot snapshot;
    private final ResourceLocation machineId;
    private final long structureVersion;
    private final long maxParallelism;
    private final List<MachineRecipe> candidates;
    private final List<MachineCapability> capabilities;
    private final List<RecipeModifier> modifiers;
    private final @Nullable List<PlanningValue> planningValues;

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId, long structureVersion,
                            long maxParallelism, List<MachineRecipe> candidates, List<MachineCapability> capabilities) {
        this(snapshot, machineId, MachineRegistry.recipePoolForMachine(machineId), structureVersion,
                maxParallelism, orderedCandidates(candidates),
                capabilities, flattenModifiers(snapshot));
    }

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId, ResourceLocation recipePoolId,
                            long structureVersion, long maxParallelism, List<MachineRecipe> candidates, List<MachineCapability> capabilities) {
        this(snapshot, machineId, recipePoolId, structureVersion, maxParallelism, orderedCandidates(candidates),
                capabilities, flattenModifiers(snapshot));
    }

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId, long structureVersion,
                            long maxParallelism, List<MachineRecipe> orderedCandidates, List<MachineCapability> capabilities,
                            List<RecipeModifier> modifiers) {
        this(snapshot, machineId, MachineRegistry.recipePoolForMachine(machineId), structureVersion,
                maxParallelism, orderedCandidates,
                capabilities, modifiers, null);
    }

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId, ResourceLocation recipePoolId,
                            long structureVersion, long maxParallelism, List<MachineRecipe> orderedCandidates, List<MachineCapability> capabilities,
                            List<RecipeModifier> modifiers) {
        this(snapshot, machineId, recipePoolId, structureVersion, maxParallelism, orderedCandidates,
                capabilities, modifiers, null);
    }

    private RecipeSearchTask(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId,
                             @Nullable ResourceLocation recipePoolId, long structureVersion,
                             long maxParallelism, List<MachineRecipe> orderedCandidates, List<MachineCapability> capabilities,
                             List<RecipeModifier> modifiers, @Nullable List<PlanningValue> planningValues) {
        if (snapshot == null || machineId == null) throw new IllegalArgumentException("snapshot and machineId are required");
        this.snapshot = snapshot;
        this.machineId = machineId;
        this.structureVersion = structureVersion;
        this.maxParallelism = Math.max(1L, maxParallelism);
        this.candidates = planningValues == null ? poolCandidates(recipePoolId, orderedCandidates)
                : List.copyOf(orderedCandidates == null ? List.of() : orderedCandidates);
        this.capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        this.modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        this.planningValues = planningValues == null ? null : List.copyOf(planningValues);
    }

    /** Creates a worker-safe search task from immutable candidate planning values. */
    public static RecipeSearchTask forPlanningValues(ControllerRuntimeSnapshot snapshot, ResourceLocation machineId,
                                                       long structureVersion, long maxParallelism,
                                                       List<MachineRecipe> candidates,
                                                       List<PlanningValue> planningValues) {
        return new RecipeSearchTask(snapshot, machineId, null, structureVersion, maxParallelism, candidates,
                List.of(), List.of(), planningValues);
    }

    public RecipeSearchResult compute() {
        FailureReport failureReport = FailureReport.forRecipeSearch();
        List<MachineRecipe> ordered = candidates;

        for (int recipeIndex = 0; recipeIndex < ordered.size(); recipeIndex++) {
            MachineRecipe recipe = ordered.get(recipeIndex);
            if (!snapshot.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
                ExecutionStatus moduleFailure = moduleFailure(recipe);
                failureReport = failureReport.plus(moduleFailure, validity(moduleFailure));
                continue;
            }
            PlanningResult result = planStart(recipe);
            if (!result.successful()) {
                ExecutionStatus failure = withSearchTrace(recipe, result);
                if (failure != null) failureReport = failureReport.plus(failure, validity(result));
                continue;
            }
            ExecutionStatus levelFailure = planningValues == null ? levelFailure(recipe) : null;
            if (levelFailure == null) {
                boolean conflictProne = hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered);
                return RecipeSearchResult.success(recipe, machineId, structureVersion,
                        snapshot.capabilityVersion(), snapshot.modifierVersion(), result, conflictProne);
            }
            failureReport = failureReport.plus(levelFailure, 1.0F);
        }
        return RecipeSearchResult.failure(machineId, structureVersion,
                snapshot.capabilityVersion(), snapshot.modifierVersion(), failureReport,
                primaryValidity(failureReport));
    }

    private PlanningResult planStart(MachineRecipe recipe) {
        if (planningValues != null) {
            PlanningValue value = planningValues.stream()
                    .filter(candidate -> candidate.recipeId().equals(recipe.id())).findFirst().orElse(null);
            if (value == null || !value.successful()) {
                FailureReason reason = value == null ? BuiltinFailureReasons.RECIPE_SEARCH : value.failureReason();
                Integer failureIndex = value == null ? null : value.failureRequirementIndex();
                FailureOccurrence occurrence = FailureOccurrence.at(reason, MMCR.id("crafting_runtime"),
                        FailurePhase.RECIPE_SEARCH, recipe.id(), failureIndex, Map.of());
                return new PlanningResult(null, ExecutionStatus.blocked(MMCR.id("crafting_runtime"),
                        MMCR.id("crafting_runtime"), occurrence), List.of(), failureIndex);
            }
            return new PlanningResult(new CraftingPlan(List.of(), maxParallelism, Map.of()), null);
        }
        CraftingContext context = borrowContext(recipe);
        try {
            return context.planStartResult(recipe, maxParallelism);
        } finally {
            CraftingContextPool.global().returnContext(recipe.id(), context);
        }
    }

    private CraftingContext borrowContext(MachineRecipe recipe) {
        return CraftingContextPool.global().borrow(recipe.id(), new CapabilitySnapshot(capabilities), modifiers);
    }

    private static ExecutionStatus moduleFailure(MachineRecipe recipe) {
        FailureOccurrence occurrence = FailureOccurrence.at(BuiltinFailureReasons.MODULE_CONNECTION,
                MMCR.id("crafting_runtime"), FailurePhase.RECIPE_SEARCH, recipe.id(), null, Map.of());
        return ExecutionStatus.blocked(MMCR.id("crafting_runtime"), MMCR.id("crafting_runtime"), occurrence);
    }

    private static @Nullable ExecutionStatus withSearchTrace(MachineRecipe recipe, PlanningResult result) {
        ExecutionStatus failure = result.failure();
        if (failure == null) return null;
        FailureOccurrence occurrence = failure.failure();
        if (occurrence == null) {
            occurrence = FailureOccurrence.at(failure.reason(), failure.source(), FailurePhase.UNKNOWN,
                    recipe.id(), result.failureRequirementIndex(), failure.details());
        }
        occurrence = occurrence.append(MMCR.id("crafting_runtime"), FailurePhase.RECIPE_SEARCH,
                recipe.id(), result.failureRequirementIndex());
        return new ExecutionStatus(failure.id(), failure.severity(), failure.source(), occurrence);
    }

    private @Nullable ExecutionStatus levelFailure(MachineRecipe recipe) {
        for (LevelRequirement requirement : recipe.levelRequirements()) {
            MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
            MachineLevel actual = snapshot.foundLevels().get(requirement.typeId());
            if (required == null || actual == null || actual.priority() < required.priority()) {
                Map<String, String> details = actual == null
                        ? Map.of("level_type", requirement.typeId().toString(),
                        "required_level", requirement.levelId().toString())
                        : Map.of("level_type", requirement.typeId().toString(),
                        "required_level", requirement.levelId().toString(),
                        "actual_level", actual.id().toString());
                FailureOccurrence occurrence = FailureOccurrence.at(BuiltinFailureReasons.LEVEL_INSUFFICIENT,
                        MMCR.id("crafting_runtime"), FailurePhase.LEVEL_CHECK, recipe.id(), null, details);
                return ExecutionStatus.blocked(MMCR.id("crafting_runtime"), MMCR.id("crafting_runtime"), occurrence);
            }
        }
        return null;
    }

    private static List<MachineRecipe> orderedCandidates(List<MachineRecipe> values) {
        return (values == null ? List.<MachineRecipe>of() : values).stream()
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
    }

    private static List<MachineRecipe> poolCandidates(@Nullable ResourceLocation recipePoolId,
                                                       List<MachineRecipe> candidates) {
        if (recipePoolId == null || candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream().filter(recipe -> recipe != null
                && recipePoolId.equals(recipe.recipePoolId())).toList();
    }

    private static List<RecipeModifier> flattenModifiers(ControllerRuntimeSnapshot snapshot) {
        return snapshot == null ? List.of()
                : MachineModifier.recipeModifiers(snapshot.foundModifiers().values().stream()
                        .flatMap(List::stream).toList());
    }

    private boolean hasMoreSpecificPendingInputCandidate(MachineRecipe selectedRecipe, int selectedIndex,
                                                          List<MachineRecipe> ordered) {
        for (int index = 0; index < selectedIndex; index++) {
            MachineRecipe earlier = ordered.get(index);
            if (earlier.priority() != selectedRecipe.priority()
                    || earlier.inputRequirementCount() <= selectedRecipe.inputRequirementCount()
                    || !earlier.hasOverlappingInputs(selectedRecipe)) continue;
            if (!snapshot.moduleConnectionStatus().canRunRecipe(earlier.requiredHostIds())) continue;
            if (planningValues != null) {
                PlanningValue value = planningValues.stream()
                        .filter(candidate -> candidate.recipeId().equals(earlier.id())).findFirst().orElse(null);
                if (value != null && !value.successful() && value.inputInsufficientWithFeasibleOutputs()) return true;
                continue;
            }
            CraftingContext context = borrowContext(earlier);
            PlanningResult inputs;
            PlanningResult outputs;
            try {
                inputs = context.planInputs(earlier, maxParallelism);
                outputs = context.planOutputs(earlier, maxParallelism);
            } finally {
                CraftingContextPool.global().returnContext(earlier.id(), context);
            }
            if (!inputs.successful() && outputs.successful()) return true;
        }
        return false;
    }

    private static float validity(@Nullable ExecutionStatus failure) {
        if (failure == null) return 0.0F;
        return failure.severity() == StatusSeverity.BLOCKED ? 0.5F : 0.1F;
    }

    private static float validity(PlanningResult result) {
        if (result == null) return 0.0F;
        int completedRequirements = result.failureRequirementIndex() == null ? 0 : result.failureRequirementIndex();
        return completedRequirements + validity(result.failure());
    }

    private static float primaryValidity(FailureReport report) {
        ExecutionStatus primary = report.primary();
        if (primary == null) return 0.0F;
        for (FailureReport.Candidate candidate : report.candidates()) {
            if (candidate.status() == primary) return candidate.validity();
        }
        return 0.0F;
    }

    /** Immutable worker result for one candidate's captured capability planning. */
    public record PlanningValue(ResourceLocation recipeId, boolean successful, boolean requiresMainThread,
                                @Nullable FailureReason failureReason, @Nullable Integer failureRequirementIndex,
                                boolean inputInsufficientWithFeasibleOutputs) {
        public PlanningValue {
            if (recipeId == null) throw new IllegalArgumentException("recipeId must not be null");
            if (successful && failureReason != null) {
                throw new IllegalArgumentException("successful planning values must not have a failure reason");
            }
            if (!successful && failureReason == null) {
                throw new IllegalArgumentException("failed planning values require a failure reason");
            }
            if (successful && inputInsufficientWithFeasibleOutputs) {
                throw new IllegalArgumentException("successful planning values cannot have pending input conflicts");
            }
        }

        public static PlanningValue success(ResourceLocation recipeId) {
            return new PlanningValue(recipeId, true, false, null, null, false);
        }

        public static PlanningValue mainThread(ResourceLocation recipeId) {
            return new PlanningValue(recipeId, true, true, null, null, false);
        }

        public static PlanningValue failure(ResourceLocation recipeId, FailureReason reason, int requirementIndex,
                                            boolean inputInsufficientWithFeasibleOutputs) {
            return new PlanningValue(recipeId, false, false, reason, requirementIndex,
                    inputInsufficientWithFeasibleOutputs);
        }
    }
}

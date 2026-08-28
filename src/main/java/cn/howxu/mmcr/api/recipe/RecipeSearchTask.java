package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import net.minecraft.resources.Identifier;
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
    private final Identifier machineId;
    private final long structureVersion;
    private final int maxParallelism;
    private final List<MachineRecipe> candidates;
    private final @Nullable Identifier lockedRecipeId;
    private final List<MachineCapability> capabilities;
    private final List<RecipeModifier> modifiers;

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, Identifier machineId, long structureVersion,
                            int maxParallelism, List<MachineRecipe> candidates,
                            @Nullable Identifier lockedRecipeId, List<MachineCapability> capabilities) {
        this(snapshot, machineId, structureVersion, maxParallelism, orderedCandidates(candidates),
                lockedRecipeId, capabilities, flattenModifiers(snapshot));
    }

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, Identifier machineId, long structureVersion,
                            int maxParallelism, List<MachineRecipe> orderedCandidates,
                            @Nullable Identifier lockedRecipeId, List<MachineCapability> capabilities,
                            List<RecipeModifier> modifiers) {
        if (snapshot == null || machineId == null) throw new IllegalArgumentException("snapshot and machineId are required");
        this.snapshot = snapshot;
        this.machineId = machineId;
        this.structureVersion = structureVersion;
        this.maxParallelism = Math.max(1, maxParallelism);
        this.candidates = List.copyOf(orderedCandidates == null ? List.of() : orderedCandidates);
        this.lockedRecipeId = lockedRecipeId;
        this.capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        this.modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
    }

    public RecipeSearchResult compute() {
        @Nullable String bestFailureUnloc = null;
        @Nullable ExecutionStatus bestFailure = null;
        @Nullable PlanningResult bestPlanningResult = null;
        float bestValidity = 0.0F;
        List<MachineRecipe> ordered = searchCandidates();

        for (int recipeIndex = 0; recipeIndex < ordered.size(); recipeIndex++) {
            MachineRecipe recipe = ordered.get(recipeIndex);
            if (!snapshot.moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds())) {
                ExecutionStatus moduleFailure = new ExecutionStatus(MMCR.id("crafting_runtime"),
                        StatusSeverity.BLOCKED, MMCR.id("crafting_runtime"), Map.of("reason", "module_connection"));
                float validity = validity(moduleFailure);
                if (validity > bestValidity) {
                    bestValidity = validity;
                    bestFailure = moduleFailure;
                    bestFailureUnloc = failureUnloc(moduleFailure);
                }
                continue;
            }
            LevelInsufficientFailure levelFailure = levelFailure(recipe);
            if (levelFailure != null) {
                return RecipeSearchResult.levelFailure(machineId, structureVersion,
                        snapshot.capabilityVersion(), snapshot.modifierVersion(), levelFailure);
            }
            PlanningResult result = planStart(recipe);
            if (result.successful()) {
                boolean conflictProne = lockedRecipeId == null
                        && hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered);
                return RecipeSearchResult.success(recipe, machineId, structureVersion,
                        snapshot.capabilityVersion(), snapshot.modifierVersion(), result, conflictProne);
            }
            float validity = validity(result.failure());
            if (validity > bestValidity) {
                bestValidity = validity;
                bestFailure = result.failure();
                bestFailureUnloc = failureUnloc(result.failure());
                bestPlanningResult = result;
            }
        }
        return RecipeSearchResult.failure(machineId, structureVersion,
                snapshot.capabilityVersion(), snapshot.modifierVersion(), bestPlanningResult,
                bestFailureUnloc, bestFailure, bestValidity);
    }

    private PlanningResult planStart(MachineRecipe recipe) {
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

    private @Nullable LevelInsufficientFailure levelFailure(MachineRecipe recipe) {
        for (LevelRequirement requirement : recipe.levelRequirements()) {
            MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
            MachineLevel actual = snapshot.foundLevels().get(requirement.typeId());
            if (required == null || actual == null || actual.priority() < required.priority()) {
                return new LevelInsufficientFailure(requirement.typeId(), requirement.levelId(),
                        actual == null ? null : actual.id());
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

    private static List<RecipeModifier> flattenModifiers(ControllerRuntimeSnapshot snapshot) {
        return snapshot == null ? List.of()
                : snapshot.foundModifiers().values().stream().flatMap(List::stream).toList();
    }

    private List<MachineRecipe> searchCandidates() {
        if (lockedRecipeId == null) return candidates;
        return candidates.stream().filter(recipe -> lockedRecipeId.equals(recipe.id())).findFirst().map(List::of)
                .orElseGet(List::of);
    }

    private boolean hasMoreSpecificPendingInputCandidate(MachineRecipe selectedRecipe, int selectedIndex,
                                                          List<MachineRecipe> ordered) {
        for (int index = 0; index < selectedIndex; index++) {
            MachineRecipe earlier = ordered.get(index);
            if (earlier.priority() != selectedRecipe.priority()
                    || earlier.inputRequirementCount() <= selectedRecipe.inputRequirementCount()
                    || !earlier.hasOverlappingInputs(selectedRecipe)) continue;
            if (!snapshot.moduleConnectionStatus().canRunRecipe(earlier.requiredHostIds())) continue;
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
        return failure.severity() == cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED ? 0.5F : 0.1F;
    }

    private static @Nullable String failureUnloc(@Nullable ExecutionStatus failure) {
        if (failure == null) return null;
        return switch (failure.details().getOrDefault("reason", "")) {
            case "insufficient_resource" -> "gui.mmcr.controller.failure.missing_input";
            case "insufficient_energy" -> "gui.mmcr.controller.failure.missing_energy";
            case "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }
}

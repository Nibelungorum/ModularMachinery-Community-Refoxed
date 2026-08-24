package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
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
 * Searches immutable controller snapshots and returns a recipe handle for a runtime.
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

    public RecipeSearchTask(ControllerRuntimeSnapshot snapshot, Identifier machineId, long structureVersion,
                            int maxParallelism, List<MachineRecipe> candidates,
                            @Nullable Identifier lockedRecipeId) {
        if (snapshot == null || machineId == null) throw new IllegalArgumentException("snapshot and machineId are required");
        this.snapshot = snapshot;
        this.machineId = machineId;
        this.structureVersion = structureVersion;
        this.maxParallelism = Math.max(1, maxParallelism);
        this.candidates = List.copyOf(candidates == null ? List.of() : candidates);
        this.lockedRecipeId = lockedRecipeId;
    }

    public RecipeSearchResult compute() {
        @Nullable String bestFailureUnloc = null;
        @Nullable ExecutionStatus bestFailure = null;
        float bestValidity = 0.0F;
        List<MachineRecipe> ordered = orderedCandidates(searchCandidates());
        CraftingContext context = context();

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
            PlanningResult result = context.planStartResult(recipe, maxParallelism);
            if (result.successful()) {
                boolean conflictProne = lockedRecipeId == null
                        && hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered);
                return RecipeSearchResult.success(recipe, machineId, structureVersion,
                        snapshot.capabilityVersion(), snapshot.modifierVersion(), conflictProne);
            }
            float validity = validity(result.failure());
            if (validity > bestValidity) {
                bestValidity = validity;
                bestFailure = result.failure();
                bestFailureUnloc = failureUnloc(result.failure());
            }
        }
        return RecipeSearchResult.failure(machineId, structureVersion,
                snapshot.capabilityVersion(), snapshot.modifierVersion(), bestFailureUnloc, bestFailure, bestValidity);
    }

    private CraftingContext context() {
        return new CraftingContext(new CapabilitySnapshot(snapshot.capabilities()), modifiers());
    }

    private List<RecipeModifier> modifiers() {
        return snapshot.foundModifiers().values().stream().flatMap(List::stream).toList();
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

    private List<MachineRecipe> orderedCandidates(List<MachineRecipe> values) {
        return values.stream()
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
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
            CraftingContext context = context();
            PlanningResult inputs = context.planInputs(earlier, maxParallelism);
            PlanningResult outputs = context.planOutputs(earlier, maxParallelism);
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
            case "insufficient_resource", "insufficient_energy" -> "gui.mmcr.controller.failure.missing_input";
            case "no_output_capacity" -> "gui.mmcr.controller.failure.missing_output";
            case "module_connection" -> "gui.mmcr.controller.failure.module_connection";
            default -> "gui.mmcr.controller.failure.missing_input";
        };
    }
}

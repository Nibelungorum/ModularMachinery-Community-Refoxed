package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable recipe-search handle. Execution is installed only by a CraftingRuntime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeSearchResult(
        boolean success,
        Identifier machineId,
        long structureVersion,
        long capabilityVersion,
        long modifierVersion,
        @Nullable MachineRecipe recipe,
        @Nullable PlanningResult planningResult,
        @Nullable String failureUnloc,
        @Nullable ExecutionStatus failure,
        @Nullable LevelInsufficientFailure levelFailure,
        float validity,
        boolean hasMoreSpecificPendingInputCandidate) {

    public RecipeSearchResult {
        Objects.requireNonNull(machineId, "machineId");
        if (success) {
            Objects.requireNonNull(recipe, "recipe");
            if (planningResult == null || !planningResult.successful()
                    || failureUnloc != null || failure != null || levelFailure != null) {
                throw new IllegalArgumentException("Successful recipe search results must not carry a failure");
            }
        } else if (recipe != null) {
            throw new IllegalArgumentException("Failed recipe search results must not carry a recipe");
        } else if (planningResult != null && planningResult.successful()) {
            throw new IllegalArgumentException("Failed recipe search results must not carry a successful plan");
        }
    }

    public static RecipeSearchResult success(MachineRecipe recipe, Identifier machineId, long structureVersion,
                                              long capabilityVersion, long modifierVersion,
                                              PlanningResult planningResult,
                                              boolean hasMoreSpecificPendingInputCandidate) {
        return new RecipeSearchResult(true, machineId, structureVersion, capabilityVersion, modifierVersion,
                recipe, planningResult, null, null, null, 1.0F,
                hasMoreSpecificPendingInputCandidate);
    }

    public static RecipeSearchResult failure(Identifier machineId, long structureVersion,
                                              long capabilityVersion, long modifierVersion,
                                              @Nullable PlanningResult planningResult,
                                              @Nullable String failureUnloc, @Nullable ExecutionStatus failure,
                                              float validity) {
        return new RecipeSearchResult(false, machineId, structureVersion, capabilityVersion, modifierVersion,
                null, planningResult, failureUnloc, failure, null, validity, false);
    }

    public static RecipeSearchResult levelFailure(Identifier machineId, long structureVersion,
                                                   long capabilityVersion, long modifierVersion,
                                                   LevelInsufficientFailure levelFailure) {
        return new RecipeSearchResult(false, machineId, structureVersion, capabilityVersion, modifierVersion, null,
                null, "gui.mmcr.controller.failure.level_insufficient",
                new ExecutionStatus(Identifier.fromNamespaceAndPath("mmcr", "crafting_runtime"),
                        StatusSeverity.BLOCKED, Identifier.fromNamespaceAndPath("mmcr", "crafting_runtime"),
                        Map.of("reason", "level_insufficient")),
                levelFailure, 1.0F, false);
    }
}

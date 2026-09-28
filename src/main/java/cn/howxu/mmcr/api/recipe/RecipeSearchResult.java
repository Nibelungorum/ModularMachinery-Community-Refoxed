package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.plan.PlanningResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailureReport;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable recipe-search handle. Execution is installed only by a CraftingRuntime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeSearchResult(
        boolean success,
        ResourceLocation machineId,
        long structureVersion,
        long capabilityVersion,
        long modifierVersion,
        @Nullable MachineRecipe recipe,
        @Nullable PlanningResult planningResult,
        @Nullable FailureReport failureReport,
        float validity,
        boolean hasMoreSpecificPendingInputCandidate) {

    public RecipeSearchResult {
        Objects.requireNonNull(machineId, "machineId");
        if (success) {
            Objects.requireNonNull(recipe, "recipe");
            if (planningResult == null || !planningResult.successful()
                    || failureReport != null && !failureReport.candidates().isEmpty()) {
                throw new IllegalArgumentException("Successful recipe search results must not carry a failure");
            }
        } else if (recipe != null) {
            throw new IllegalArgumentException("Failed recipe search results must not carry a recipe");
        } else {
            if (planningResult != null) {
                throw new IllegalArgumentException("Failed recipe search results must not carry a plan");
            }
            Objects.requireNonNull(failureReport, "failureReport");
        }
    }

    public static RecipeSearchResult success(MachineRecipe recipe, ResourceLocation machineId, long structureVersion,
                                              long capabilityVersion, long modifierVersion,
                                              PlanningResult planningResult,
                                              boolean hasMoreSpecificPendingInputCandidate) {
        return new RecipeSearchResult(true, machineId, structureVersion, capabilityVersion, modifierVersion,
                recipe, planningResult, null, 1.0F,
                hasMoreSpecificPendingInputCandidate);
    }

    public static RecipeSearchResult failure(ResourceLocation machineId, long structureVersion,
                                               long capabilityVersion, long modifierVersion,
                                               FailureReport failureReport,
                                               float validity) {
        return new RecipeSearchResult(false, machineId, structureVersion, capabilityVersion, modifierVersion,
                null, null, failureReport, validity, false);
    }

    public @Nullable ExecutionStatus failure() {
        return failureReport == null ? null : failureReport.primary();
    }

    public @Nullable FailureOccurrence primaryFailure() {
        ExecutionStatus status = failure();
        return status == null ? null : status.failure();
    }
}

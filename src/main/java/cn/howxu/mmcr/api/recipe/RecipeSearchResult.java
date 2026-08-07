package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author howxu <dev@howxu.cn>
 */
public record RecipeSearchResult(
        boolean success,
        Identifier machineId,
        long structureVersion,
        @Nullable ActiveMachineRecipe activeRecipe,
        @Nullable RecipeCraftingContext context,
        @Nullable String failureUnloc,
        @Nullable RequirementFailure requirementFailure,
        float validity,
        boolean hasMoreSpecificPendingInputCandidate) {

    public RecipeSearchResult {
        Objects.requireNonNull(machineId, "machineId");
        if (success) {
            Objects.requireNonNull(activeRecipe, "activeRecipe");
            Objects.requireNonNull(context, "context");
            if (failureUnloc != null || requirementFailure != null) {
                throw new IllegalArgumentException("Successful recipe search results must not carry a failure");
            }
        } else if (activeRecipe != null || context != null) {
            throw new IllegalArgumentException("Failed recipe search results must not carry active recipe state");
        }
    }

    public static RecipeSearchResult success(ActiveMachineRecipe activeRecipe,
                                             RecipeCraftingContext context,
                                             Identifier machineId,
                                             long structureVersion,
                                             boolean hasMoreSpecificPendingInputCandidate) {
        return new RecipeSearchResult(true, machineId, structureVersion, activeRecipe, context, null, null, 1.0F, hasMoreSpecificPendingInputCandidate);
    }

    public static RecipeSearchResult success(ActiveMachineRecipe activeRecipe, RecipeCraftingContext context, Identifier machineId, long structureVersion) {
        return success(activeRecipe, context, machineId, structureVersion, false);
    }

    public static RecipeSearchResult failure(Identifier machineId,
                                             long structureVersion,
                                             @Nullable String failureUnloc,
                                             @Nullable RequirementFailure requirementFailure,
                                             float validity) {
        return new RecipeSearchResult(false, machineId, structureVersion, null, null, failureUnloc, requirementFailure, validity, false);
    }
}

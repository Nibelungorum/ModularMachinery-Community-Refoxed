package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchTask {

    private final MachineControllerBlockEntity controller;
    private final Identifier machineId;
    private final long structureVersion;
    private final int maxParallelism;
    private final List<MachineRecipe> candidates;
    private final RecipeCraftingContextPool contextPool;

    public RecipeSearchTask(MachineControllerBlockEntity controller,
                            Identifier machineId,
                            long structureVersion,
                            int maxParallelism,
                            List<MachineRecipe> candidates,
                            RecipeCraftingContextPool contextPool) {
        this.controller = controller;
        this.machineId = machineId;
        this.structureVersion = structureVersion;
        this.maxParallelism = Math.max(1, maxParallelism);
        this.candidates = List.copyOf(candidates);
        this.contextPool = contextPool;
    }

    public RecipeSearchResult compute() {
        @Nullable String bestFailureUnloc = null;
        @Nullable RequirementFailure bestFailure = null;
        float bestValidity = 0.0F;

        for (MachineRecipe recipe : candidates) {
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, maxParallelism);
            RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
            if (context.simulateInputs(recipe) && context.simulateOutputs(recipe)) {
                return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion);
            }

            float validity = validity(context.getLastFailureUnloc(), context.getLastRequirementFailure());
            if (validity > bestValidity) {
                bestValidity = validity;
                bestFailureUnloc = context.getLastFailureUnloc();
                bestFailure = context.getLastRequirementFailure();
            }
            contextPool.returnContext(context);
        }

        return RecipeSearchResult.failure(machineId, structureVersion, bestFailureUnloc, bestFailure, bestValidity);
    }

    public static float validity(@Nullable String failureUnloc, @Nullable RequirementFailure failure) {
        if (failure == null) return failureUnloc == null ? 0.0F : 0.01F;
        float componentScore = failure.searchedComponents().isEmpty() ? 0.1F : 0.3F;
        float matchScore = failure.matchedComponents().isEmpty() ? 0.0F : 0.2F;
        float amountScore = 0.0F;
        if (failure.required() > 0) {
            amountScore = Math.min(0.49F, (float) failure.available() / (float) failure.required() * 0.49F);
        }
        float indexScore = Math.min(0.01F, failure.requirementIndex() * 0.001F);
        return componentScore + matchScore + amountScore + indexScore;
    }
}

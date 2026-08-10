package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Comparator;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchTask {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeSearchTask.class);

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
        List<MachineRecipe> ordered = orderedCandidates();
        for (int recipeIndex = 0; recipeIndex < ordered.size(); recipeIndex++) {
            MachineRecipe recipe = ordered.get(recipeIndex);
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, maxParallelism);
            RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
            if (activeRecipe.canStartCrafting(context)) {
                LOG.info("[ParallelSearch] machine={} recipe={} recipeParallelized={} searchMaxParallelism={} selectedParallelism={} structureVersion={}",
                        machineId, recipe.id(), recipe.isParallelized(), maxParallelism, activeRecipe.getParallelism(), structureVersion);
                return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion,
                        hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered));
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

    private List<MachineRecipe> orderedCandidates() {
        return candidates.stream()
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
    }

    private boolean hasMoreSpecificPendingInputCandidate(MachineRecipe selectedRecipe, int selectedRecipeIndex,
                                                         List<MachineRecipe> ordered) {
        for (int i = 0; i < selectedRecipeIndex; i++) {
            MachineRecipe earlier = ordered.get(i);
            if (earlier.priority() != selectedRecipe.priority()
                    || earlier.inputRequirementCount() <= selectedRecipe.inputRequirementCount()
                    || !earlier.hasOverlappingInputs(selectedRecipe)) continue;
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(earlier, maxParallelism);
            RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
            boolean missingInputs = !context.simulateInputs(earlier);
            String failureUnloc = context.getLastFailureUnloc();
            boolean outputAvailable = context.simulateOutputs(earlier);
            contextPool.returnContext(context);
            if (missingInputs && outputAvailable && RecipeCraftingContext.FAILURE_MISSING_INPUT.equals(failureUnloc)) return true;
        }
        return false;
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

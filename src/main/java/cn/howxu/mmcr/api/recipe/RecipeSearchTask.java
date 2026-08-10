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
        List<MachineRecipe> ordered = orderedCandidates();
        for (MachineRecipe recipe : ordered) {
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, maxParallelism);
            RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
            LOG.info("[ParallelSearch] machine={} recipe={} recipeParallelized={} searchMaxParallelism={} structureVersion={}",
                    machineId, recipe.id(), recipe.isParallelized(), maxParallelism, structureVersion);
            return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion, false);
        }
        return RecipeSearchResult.failure(machineId, structureVersion, null, null, 0.0F);
    }

    private List<MachineRecipe> orderedCandidates() {
        return candidates.stream()
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
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

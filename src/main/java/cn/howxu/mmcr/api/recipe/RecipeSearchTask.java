package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

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
    private final @Nullable RecipeCandidateIndex candidateIndex;

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
        this.candidateIndex = null;
    }

    public RecipeSearchTask(MachineControllerBlockEntity controller,
                            Identifier machineId,
                            long structureVersion,
                            int maxParallelism,
                            List<MachineRecipe> candidates,
                            RecipeCraftingContextPool contextPool,
                            RecipeCandidateIndex candidateIndex) {
        this.controller = controller;
        this.machineId = machineId;
        this.structureVersion = structureVersion;
        this.maxParallelism = Math.max(1, maxParallelism);
        this.candidates = List.copyOf(candidates);
        this.contextPool = contextPool;
        this.candidateIndex = candidateIndex;
    }

    public RecipeSearchResult compute() {
        @Nullable String bestFailureUnloc = null;
        @Nullable RequirementFailure bestFailure = null;
        @Nullable MachineRecipe bestFailureRecipe = null;
        float bestValidity = 0.0F;
        Map<RecipeCraftingContext.ItemMatchKey, Boolean> itemMatchCache = new HashMap<>();
        List<MachineRecipe> ordered = orderedCandidates(searchCandidates());

        for (int recipeIndex = 0; recipeIndex < ordered.size(); recipeIndex++) {
            MachineRecipe recipe = ordered.get(recipeIndex);
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(recipe, maxParallelism);
            RecipeCraftingContext context = null;
            context = contextPool.borrow(activeRecipe, controller);
            context.setItemMatchCache(itemMatchCache);
            if (context.simulateInputs(recipe)) {
                LevelInsufficientFailure levelFailure = levelFailure(recipe);
                if (levelFailure != null) {
                    context.clearItemMatchCache();
                    contextPool.returnContext(context);
                    return RecipeSearchResult.levelFailure(machineId, structureVersion, levelFailure);
                }
            }
            if (activeRecipe.canStartCrafting(context)) {
                boolean conflictProne = hasMoreSpecificPendingInputCandidate(recipe, recipeIndex, ordered, itemMatchCache);
                context.clearItemMatchCache();
                return RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion, conflictProne);
            }

            float validity = validity(context.getLastFailureUnloc(), context.getLastRequirementFailure());
            if (validity > bestValidity) {
                bestValidity = validity;
                bestFailureUnloc = context.getLastFailureUnloc();
                bestFailure = context.getLastRequirementFailure();
                bestFailureRecipe = recipe;
            }
            context.clearItemMatchCache();
            contextPool.returnContext(context);
        }

        return RecipeSearchResult.failure(machineId, structureVersion, bestFailureUnloc, bestFailure, bestValidity);
    }

    private @Nullable LevelInsufficientFailure levelFailure(MachineRecipe recipe) {
        for (LevelRequirement requirement : recipe.levelRequirements()) {
            MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
            MachineLevel actual = controller.getFoundLevels().get(requirement.typeId());
            if (required == null || actual == null || actual.priority() < required.priority()) {
                return new LevelInsufficientFailure(requirement.typeId(), requirement.levelId(),
                        actual == null ? null : actual.id());
            }
        }
        return null;
    }

    private List<MachineRecipe> orderedCandidates(List<MachineRecipe> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(MachineRecipe::priority)
                        .thenComparing(Comparator.comparingInt(MachineRecipe::inputRequirementCount).reversed())
                        .thenComparing(MachineRecipe::id))
                .toList();
    }

    private List<MachineRecipe> searchCandidates() {
        if (candidateIndex == null) return candidates;
        LinkedHashSet<Item> inputItems = new LinkedHashSet<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!(component.getContainer() instanceof ItemInputBusBlockEntity bus)) continue;
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) inputItems.add(stack.getItem());
            }
        }
        if (inputItems.isEmpty()) return candidates;
        return candidateIndex.candidates(inputItems);
    }

    private boolean hasMoreSpecificPendingInputCandidate(MachineRecipe selectedRecipe,
                                                         int selectedRecipeIndex,
                                                         List<MachineRecipe> ordered,
                                                         Map<RecipeCraftingContext.ItemMatchKey, Boolean> itemMatchCache) {
        for (int i = 0; i < selectedRecipeIndex; i++) {
            MachineRecipe earlier = ordered.get(i);
            if (earlier.priority() != selectedRecipe.priority()) continue;
            if (earlier.inputRequirementCount() <= selectedRecipe.inputRequirementCount()) continue;
            if (!earlier.hasOverlappingInputs(selectedRecipe)) continue;
            ActiveMachineRecipe activeRecipe = new ActiveMachineRecipe(earlier, maxParallelism);
            RecipeCraftingContext context = contextPool.borrow(activeRecipe, controller);
            context.setItemMatchCache(itemMatchCache);
            boolean missingInputs = !context.simulateInputs(earlier);
            String failureUnloc = context.getLastFailureUnloc();
            boolean outputAvailable = context.simulateOutputs(earlier);
            context.clearItemMatchCache();
            contextPool.returnContext(context);
            if (missingInputs && outputAvailable && RecipeCraftingContext.FAILURE_MISSING_INPUT.equals(failureUnloc)) {
                return true;
            }
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

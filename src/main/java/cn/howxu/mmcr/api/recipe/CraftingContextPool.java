package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class CraftingContextPool {

    private static final CraftingContextPool GLOBAL = new CraftingContextPool();

    private final Map<Identifier, ArrayDeque<PooledContext>> contexts = new HashMap<>();
    private final Map<Identifier, ArrayDeque<PlanningContext>> planningContexts = new HashMap<>();
    private long reloadCounter;

    public static CraftingContextPool global() {
        return GLOBAL;
    }

    public static void onGlobalReload() {
        GLOBAL.onReload();
    }

    public RecipeCraftingContext borrow(ActiveMachineRecipe activeRecipe, MachineControllerBlockEntity controller) {
        Identifier recipeId = activeRecipe.getRecipe().id();
        ArrayDeque<PooledContext> bucket = contexts.get(recipeId);
        while (bucket != null && !bucket.isEmpty()) {
            PooledContext pooled = bucket.removeFirst();
            if (pooled.reloadCounter == reloadCounter) {
                pooled.context.resetFor(controller);
                pooled.context.setStructureModifiers(controller.foundModifierList());
                return pooled.context;
            }
        }
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        context.setPoolRecipeId(recipeId);
        context.setStructureModifiers(controller.foundModifierList());
        return context;
    }

    public CraftingContext borrow(Identifier recipeId, CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (recipeId == null || snapshot == null) throw new IllegalArgumentException("recipeId and snapshot are required");
        ArrayDeque<PlanningContext> bucket = planningContexts.get(recipeId);
        while (bucket != null && !bucket.isEmpty()) {
            PlanningContext pooled = bucket.removeFirst();
            if (pooled.reloadCounter == reloadCounter) {
                pooled.context.resetFor(snapshot, modifiers);
                return pooled.context;
            }
        }
        return new CraftingContext(snapshot, modifiers);
    }

    public void returnContext(RecipeCraftingContext context) {
        if (context == null) return;
        Identifier recipeId = context.poolRecipeId();
        if (recipeId == null) return;
        context.resetTransientState();
        contexts.computeIfAbsent(recipeId, ignored -> new ArrayDeque<>())
                .addFirst(new PooledContext(context, reloadCounter));
    }

    public void returnContext(Identifier recipeId, CraftingContext context) {
        if (recipeId == null || context == null) return;
        planningContexts.computeIfAbsent(recipeId, ignored -> new ArrayDeque<>())
                .addFirst(new PlanningContext(context, reloadCounter));
    }

    public void onReload() {
        contexts.clear();
        planningContexts.clear();
        reloadCounter++;
    }

    private record PooledContext(RecipeCraftingContext context, long reloadCounter) { }

    private record PlanningContext(CraftingContext context, long reloadCounter) { }
}

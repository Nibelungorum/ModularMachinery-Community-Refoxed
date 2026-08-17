package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeCraftingContextPool {

    private static final RecipeCraftingContextPool GLOBAL = new RecipeCraftingContextPool();

    private final Map<Identifier, ArrayDeque<PooledContext>> contexts = new HashMap<>();
    private long reloadCounter;

    public static RecipeCraftingContextPool global() {
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

    public void returnContext(RecipeCraftingContext context) {
        if (context == null) return;
        Identifier recipeId = context.poolRecipeId();
        if (recipeId == null) return;
        context.resetTransientState();
        contexts.computeIfAbsent(recipeId, ignored -> new ArrayDeque<>())
                .addFirst(new PooledContext(context, reloadCounter));
    }

    public void onReload() {
        contexts.clear();
        reloadCounter++;
    }

    private record PooledContext(RecipeCraftingContext context, long reloadCounter) { }
}

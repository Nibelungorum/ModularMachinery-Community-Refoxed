package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
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

    private final Map<Identifier, ArrayDeque<CraftingContext>> planningContexts = new HashMap<>();

    public static CraftingContextPool global() {
        return GLOBAL;
    }

    public static void onGlobalReload() {
        GLOBAL.onReload();
    }

    public CraftingContext borrow(Identifier recipeId, CapabilitySnapshot snapshot, List<RecipeModifier> modifiers) {
        if (recipeId == null || snapshot == null) throw new IllegalArgumentException("recipeId and snapshot are required");
        ArrayDeque<CraftingContext> bucket = planningContexts.get(recipeId);
        while (bucket != null && !bucket.isEmpty()) {
            CraftingContext context = bucket.removeFirst();
            context.resetFor(snapshot, modifiers);
            return context;
        }
        return new CraftingContext(snapshot, modifiers);
    }

    public void returnContext(Identifier recipeId, CraftingContext context) {
        if (recipeId == null || context == null) return;
        planningContexts.computeIfAbsent(recipeId, ignored -> new ArrayDeque<>())
                .addFirst(context);
    }

    public void onReload() {
        planningContexts.clear();
    }
}

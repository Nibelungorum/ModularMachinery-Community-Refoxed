package cn.howxu.mmcr.internal.recipe;

import net.minecraft.resources.Identifier;

/**
 * Tracks short start delays for recipes that may block a more specific pending input match.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeStartDelay {

    public static final int DELAY_TICKS = 20;

    private Identifier pendingRecipeId;
    private long pendingStartTick = Long.MIN_VALUE;

    public boolean shouldDelay(Identifier recipeId, boolean hasMoreSpecificPendingInputCandidate, long gameTime) {
        if (!hasMoreSpecificPendingInputCandidate || recipeId == null) {
            clear();
            return false;
        }
        if (!recipeId.equals(pendingRecipeId)) {
            pendingRecipeId = recipeId;
            pendingStartTick = gameTime;
            return true;
        }
        return gameTime - pendingStartTick < DELAY_TICKS;
    }

    public void clear() {
        pendingRecipeId = null;
        pendingStartTick = Long.MIN_VALUE;
    }
}

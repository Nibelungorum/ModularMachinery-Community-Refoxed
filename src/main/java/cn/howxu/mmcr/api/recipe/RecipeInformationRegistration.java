package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.RecipeInformation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Collector for localized information on MMCR JEI recipe pages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeInformationRegistration {
    private final List<RecipeInformation> entries = new ArrayList<>();
    private boolean frozen;

    public void registerRecipePool(ResourceLocation poolId, String translationKey, Object... arguments) {
        requireOpen();
        entries.add(RecipeInformation.pool(poolId, translationKey, arguments));
    }

    public void registerRecipe(ResourceLocation recipeId, String translationKey, Object... arguments) {
        requireOpen();
        entries.add(RecipeInformation.recipe(recipeId, translationKey, arguments));
    }

    public void freeze() {
        frozen = true;
    }

    public List<RecipeInformation> entries() {
        return List.copyOf(entries);
    }

    private void requireOpen() {
        if (frozen) throw new IllegalStateException("JEI recipe information is frozen");
    }
}

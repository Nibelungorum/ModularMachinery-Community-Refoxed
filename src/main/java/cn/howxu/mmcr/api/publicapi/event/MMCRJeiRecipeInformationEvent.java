package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.recipe.RecipeInformation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Client event for registering localized information on MMCR JEI recipe pages.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRJeiRecipeInformationEvent extends Event {
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

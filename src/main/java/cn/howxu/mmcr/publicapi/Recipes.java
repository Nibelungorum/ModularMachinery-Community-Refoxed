package cn.howxu.mmcr.publicapi;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import net.minecraft.resources.ResourceLocation;
/** Recipe declaration entry point. @author howxu <dev@howxu.cn> */
public final class Recipes {
    private Recipes() {}
    /** Returns whether recipe declaration registration is open. */
    public static boolean isRegistrationOpen() { return RecipeIoValidation.isRegistrationOpen(); }
    public static RecipeDraft recipe(ResourceLocation id) { return RecipeAdapters.recipe(id); }
}

package cn.howxu.mmcr.api.publicapi.recipe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Localized client information associated with a recipe pool or recipe.
 *
 * @author howxu <dev@howxu.cn>
 */
public record RecipeInformation(Target target, ResourceLocation targetId, String translationKey, List<Object> arguments) {

    public RecipeInformation {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetId, "targetId");
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("translationKey must not be null or blank");
        }
        if (arguments == null) throw new IllegalArgumentException("arguments must not be null");
        arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public static RecipeInformation pool(ResourceLocation poolId, String translationKey, Object... arguments) {
        if (arguments == null) throw new IllegalArgumentException("arguments must not be null");
        return new RecipeInformation(Target.RECIPE_POOL, poolId, translationKey, Arrays.asList(arguments));
    }

    public static RecipeInformation recipe(ResourceLocation recipeId, String translationKey, Object... arguments) {
        if (arguments == null) throw new IllegalArgumentException("arguments must not be null");
        return new RecipeInformation(Target.RECIPE, recipeId, translationKey, Arrays.asList(arguments));
    }

    public Component component() {
        return Component.translatable(translationKey, arguments.toArray());
    }

    public enum Target {
        RECIPE_POOL,
        RECIPE
    }
}

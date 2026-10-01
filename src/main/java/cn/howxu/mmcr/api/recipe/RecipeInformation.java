package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
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
        arguments = copyArguments(arguments);
    }

    public static RecipeInformation pool(ResourceLocation poolId, String translationKey, Object... arguments) {
        if (arguments == null) throw new IllegalArgumentException("arguments must not be null");
        return new RecipeInformation(Target.RECIPE_POOL, poolId, translationKey, Arrays.asList(arguments));
    }

    public static RecipeInformation recipe(ResourceLocation recipeId, String translationKey, Object... arguments) {
        if (arguments == null) throw new IllegalArgumentException("arguments must not be null");
        return new RecipeInformation(Target.RECIPE, recipeId, translationKey, Arrays.asList(arguments));
    }

    /** Returns an immutable list with caller-owned snapshots of Component arguments. */
    @Override
    public List<Object> arguments() {
        return copyArguments(arguments);
    }

    /** Returns a caller-owned component, including nested translation arguments. */
    public Component component() {
        return Component.translatable(translationKey, arguments().toArray());
    }

    private static List<Object> copyArguments(List<Object> arguments) {
        List<Object> copy = new ArrayList<>(arguments.size());
        for (Object argument : arguments) {
            copy.add(argument instanceof Component component ? ComponentSnapshots.copy(component) : argument);
        }
        return Collections.unmodifiableList(copy);
    }

    public enum Target {
        RECIPE_POOL,
        RECIPE
    }
}

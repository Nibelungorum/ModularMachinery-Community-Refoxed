package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.api.publicapi.recipe.RecipeInformation;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeInformation.Target;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable client snapshots for public and KubeJS recipe information.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeInformationRegistry {
    private static volatile List<RecipeInformation> publicEntries = List.of();
    private static volatile List<RecipeInformation> kubeJSEntries = List.of();

    private RecipeInformationRegistry() {
    }

    public static void replacePublic(List<RecipeInformation> entries) {
        publicEntries = List.copyOf(entries);
    }

    public static void replaceKubeJS(List<RecipeInformation> entries) {
        kubeJSEntries = List.copyOf(entries);
    }

    public static List<Component> componentsFor(ResourceLocation poolId, ResourceLocation recipeId) {
        List<RecipeInformation> publicSnapshot = publicEntries;
        List<RecipeInformation> kubeJSSnapshot = kubeJSEntries;
        List<Component> components = new ArrayList<>();
        append(components, publicSnapshot, Target.RECIPE_POOL, poolId);
        append(components, kubeJSSnapshot, Target.RECIPE_POOL, poolId);
        append(components, publicSnapshot, Target.RECIPE, recipeId);
        append(components, kubeJSSnapshot, Target.RECIPE, recipeId);
        return List.copyOf(components);
    }

    private static void append(List<Component> components, List<RecipeInformation> entries,
                               Target target, ResourceLocation targetId) {
        entries.stream()
                .filter(entry -> entry.target() == target && entry.targetId().equals(targetId))
                .map(RecipeInformation::component)
                .forEach(components::add);
    }

    static void clearForTesting() {
        publicEntries = List.of();
        kubeJSEntries = List.of();
    }
}

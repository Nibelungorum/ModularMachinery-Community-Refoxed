package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.api.recipe.RecipeInformation;
import cn.howxu.mmcr.api.recipe.RecipeInformation.Target;
import cn.howxu.mmcr.api.jei.JeiWorkstationRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Immutable client snapshots for public and KubeJS recipe information.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeInformationRegistry {
    private static volatile PublicSnapshot publicSnapshot = new PublicSnapshot(List.of(), List.of(), null);
    private static volatile List<RecipeInformation> kubeJSEntries = List.of();
    private static final ThreadLocal<InformationSources> collecting = new ThreadLocal<>();

    record PublicSnapshot(List<RecipeInformation> information, List<JeiWorkstationRegistration> workstations,
                          RecipeViewerSnapshot viewer) { }
    private record InformationSources(List<RecipeInformation> publicEntries, List<RecipeInformation> kubeJSEntries) { }

    private RecipeInformationRegistry() {
    }

    public static void replacePublic(List<RecipeInformation> entries) {
        PublicSnapshot previous = publicSnapshot;
        publicSnapshot = new PublicSnapshot(List.copyOf(entries), previous.workstations(), previous.viewer());
    }

    public static void replaceKubeJS(List<RecipeInformation> entries) {
        kubeJSEntries = List.copyOf(entries);
    }

    public static List<Component> componentsFor(ResourceLocation poolId, ResourceLocation recipeId) {
        InformationSources candidate = collecting.get();
        List<RecipeInformation> publicEntries = candidate == null ? publicSnapshot.information() : candidate.publicEntries();
        List<RecipeInformation> kubeJSSnapshot = candidate == null ? kubeJSEntries : candidate.kubeJSEntries();
        List<Component> components = new ArrayList<>();
        append(components, publicEntries, Target.RECIPE_POOL, poolId);
        append(components, kubeJSSnapshot, Target.RECIPE_POOL, poolId);
        append(components, publicEntries, Target.RECIPE, recipeId);
        append(components, kubeJSSnapshot, Target.RECIPE, recipeId);
        return List.copyOf(components);
    }

    static List<RecipeInformation> kubeJSEntries() { return kubeJSEntries; }
    static PublicSnapshot publicSnapshot() { return publicSnapshot; }

    static void commit(List<RecipeInformation> information, RecipeViewerSnapshot viewer) {
        publicSnapshot = new PublicSnapshot(List.copyOf(information), viewer.workstations(), viewer);
    }

    static <T> T collecting(List<RecipeInformation> information, List<RecipeInformation> kubeJS, Supplier<T> action) {
        InformationSources previous = collecting.get();
        collecting.set(new InformationSources(information, kubeJS));
        try {
            return action.get();
        } finally {
            if (previous == null) collecting.remove();
            else collecting.set(previous);
        }
    }

    static void resetPublic() {
        publicSnapshot = new PublicSnapshot(List.of(), List.of(), null);
    }

    private static void append(List<Component> components, List<RecipeInformation> entries,
                               Target target, ResourceLocation targetId) {
        entries.stream()
                .filter(entry -> entry.target() == target && entry.targetId().equals(targetId))
                .map(RecipeInformation::component)
                .forEach(components::add);
    }

    static void clearForTesting() {
        resetPublic();
        kubeJSEntries = List.of();
    }
}

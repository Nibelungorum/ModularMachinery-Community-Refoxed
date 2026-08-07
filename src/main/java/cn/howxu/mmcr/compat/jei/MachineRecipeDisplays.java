package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;

import java.util.Comparator;
import java.util.List;

/**
 * Collects and orders JEI machine recipe displays.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeDisplays {

    private static final Comparator<MachineRecipeDisplay> ORDER = Comparator
            .comparing(MachineRecipeDisplay::machineId)
            .thenComparing(Comparator.comparingInt((MachineRecipeDisplay display) -> display.recipe().priority()).reversed())
            .thenComparing(MachineRecipeDisplay::recipeId);

    private MachineRecipeDisplays() {
    }

    public static MachineRecipeDisplay forRecipe(MachineRecipe recipe) {
        return MachineRecipeDisplay.from(recipe);
    }

    public static List<MachineRecipeDisplay> all() {
        return RecipeRegistry.recipes().stream()
                .map(MachineRecipeDisplay::from)
                .sorted(ORDER)
                .toList();
    }
}

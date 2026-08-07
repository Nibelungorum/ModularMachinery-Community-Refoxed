package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static Map<Identifier, List<MachineRecipeDisplay>> byMachine() {
        return all().stream().collect(Collectors.groupingBy(
                MachineRecipeDisplay::machineId,
                LinkedHashMap::new,
                Collectors.toList()));
    }
}

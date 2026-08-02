package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeRegistry {

    private static final Map<Identifier, MachineRecipe> RECIPES = new LinkedHashMap<>();

    private RecipeRegistry() {
    }

    public static void register(MachineRecipe recipe) {
        if (recipe.id() == null) {
            throw new IllegalArgumentException("Recipe id null");
        }
        RECIPES.put(recipe.id(), recipe);
    }

    public static List<MachineRecipe> byMachine(Machine machine) {
        return RECIPES.values().stream()
                .filter(recipe -> recipe.machineId().equals(machine.registryName()))
                .toList();
    }

    public static void clearForTesting() {
        RECIPES.clear();
    }
}

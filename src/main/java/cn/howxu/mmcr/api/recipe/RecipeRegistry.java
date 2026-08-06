package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RecipeRegistry {

    private static final Map<Identifier, MachineRecipe> RECIPES = new LinkedHashMap<>();
    private static final Map<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>> BY_MACHINE = new LinkedHashMap<>();
    private static long reloadVersion;

    private RecipeRegistry() {
    }

    public static void register(MachineRecipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe must not be null");
        }
        if (recipe.id() == null) {
            throw new IllegalArgumentException("Recipe id null");
        }
        RECIPES.put(recipe.id(), recipe);
        TreeMap<Integer, TreeSet<MachineRecipe>> priorityMap = BY_MACHINE.computeIfAbsent(
                recipe.machineId(), k -> new TreeMap<>());
        TreeSet<MachineRecipe> set = priorityMap.computeIfAbsent(
                recipe.priority(), p -> new TreeSet<>(Comparator.comparing(MachineRecipe::id)));
        set.add(recipe);
    }

    public static MachineRecipe getRecipe(Identifier id) {
        if (id == null) return null;
        return RECIPES.get(id);
    }

    public static List<MachineRecipe> byMachine(Machine machine) {
        if (machine == null || machine.registryName() == null) return Collections.emptyList();
        return byMachineId(machine.registryName());
    }

    public static List<MachineRecipe> byMachineId(Identifier machineId) {
        if (machineId == null) return Collections.emptyList();
        TreeMap<Integer, TreeSet<MachineRecipe>> priorityMap = BY_MACHINE.get(machineId);
        if (priorityMap == null) return Collections.emptyList();
        return priorityMap.values().stream().flatMap(TreeSet::stream).toList();
    }

    public static int registeredRecipeCount() {
        return RECIPES.size();
    }

    public static long reloadVersion() {
        return reloadVersion;
    }

    public static void clearAll() {
        RECIPES.clear();
        BY_MACHINE.clear();
        reloadVersion++;
    }

    public static void clearForTesting() {
        clearAll();
    }
}

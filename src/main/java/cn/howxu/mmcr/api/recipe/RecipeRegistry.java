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

    private static final Map<Identifier, MachineRecipe> STATIC_RECIPES = new LinkedHashMap<>();
    private static final Map<Identifier, MachineRecipe> DYNAMIC_RECIPES = new LinkedHashMap<>();
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
        if (STATIC_RECIPES.containsKey(recipe.id())) {
            throw new IllegalStateException("Recipe already registered: " + recipe.id());
        }
        STATIC_RECIPES.put(recipe.id(), recipe);
        rebuildIndex();
    }

    public static MachineRecipe getRecipe(Identifier id) {
        if (id == null) return null;
        MachineRecipe recipe = STATIC_RECIPES.get(id);
        return recipe != null ? recipe : DYNAMIC_RECIPES.get(id);
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

    public static List<MachineRecipe> recipes() {
        return List.copyOf(mergedRecipes().values());
    }

    public static int registeredRecipeCount() {
        return STATIC_RECIPES.size() + DYNAMIC_RECIPES.size();
    }

    public static long reloadVersion() {
        return reloadVersion;
    }

    public static boolean containsStatic(Identifier id) {
        return STATIC_RECIPES.containsKey(id);
    }

    public static void replaceDynamic(Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            if (STATIC_RECIPES.containsKey(entry.getKey())) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + entry.getKey());
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        DYNAMIC_RECIPES.clear();
        DYNAMIC_RECIPES.putAll(replacement);
        rebuildIndex();
        reloadVersion++;
    }

    public static Map<Identifier, MachineRecipe> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_RECIPES);
    }

    private static Map<Identifier, MachineRecipe> mergedRecipes() {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>(STATIC_RECIPES);
        recipes.putAll(DYNAMIC_RECIPES);
        return recipes;
    }

    private static void rebuildIndex() {
        BY_MACHINE.clear();
        for (MachineRecipe recipe : mergedRecipes().values()) {
            TreeMap<Integer, TreeSet<MachineRecipe>> priorities = BY_MACHINE.computeIfAbsent(
                    recipe.machineId(), ignored -> new TreeMap<>());
            priorities.computeIfAbsent(recipe.priority(), ignored ->
                    new TreeSet<>(Comparator.comparing(MachineRecipe::id))).add(recipe);
        }
    }

    public static void clearAll() {
        STATIC_RECIPES.clear();
        DYNAMIC_RECIPES.clear();
        BY_MACHINE.clear();
        reloadVersion++;
    }

    public static void clearForTesting() {
        clearAll();
    }
}

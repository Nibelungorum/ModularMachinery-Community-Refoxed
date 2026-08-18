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
import cn.howxu.mmcr.MMCR;

public final class RecipeRegistry {

    private static final Map<Identifier, MachineRecipe> STATIC_RECIPES = new LinkedHashMap<>();
    private static volatile Map<Identifier, MachineRecipe> DATA_PACK_RECIPES = Map.of();
    private static volatile Map<Identifier, MachineRecipe> DYNAMIC_RECIPES = Map.of();
    private static volatile Map<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>> BY_MACHINE = Map.of();
    private static long reloadVersion;
    private static long registryVersion;

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
        registryVersion++;
    }

    public static MachineRecipe getRecipe(Identifier id) {
        if (id == null) return null;
        MachineRecipe recipe = DATA_PACK_RECIPES.get(id);
        if (recipe != null) return recipe;
        recipe = DYNAMIC_RECIPES.get(id);
        return recipe != null ? recipe : STATIC_RECIPES.get(id);
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
        return mergedRecipes().size();
    }

    public static long reloadVersion() {
        return reloadVersion;
    }

    public static long registryVersion() {
        return registryVersion;
    }

    public static boolean containsStatic(Identifier id) {
        return STATIC_RECIPES.containsKey(id);
    }

    public static void replaceDynamic(Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            Identifier id = entry.getKey();
            if (STATIC_RECIPES.containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + id);
            }
            if (DATA_PACK_RECIPES.containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with data-pack recipe: " + id);
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        DYNAMIC_RECIPES = Map.copyOf(replacement);
        rebuildIndex();
        reloadVersion++;
        registryVersion++;
    }

    public static Map<Identifier, MachineRecipe> dynamicSnapshot() {
        return Map.copyOf(DYNAMIC_RECIPES);
    }

    public static Map<Identifier, MachineRecipe> dataPackSnapshot() {
        return Map.copyOf(DATA_PACK_RECIPES);
    }

    public static Map<Identifier, MachineRecipe> staticSnapshot() {
        return Map.copyOf(STATIC_RECIPES);
    }

    public static void replaceDataPack(Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRecipe> replacement = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            if (STATIC_RECIPES.containsKey(entry.getKey())) {
                MMCR.LOG.warn("Data-pack recipe {} overrides static recipe {}", entry.getKey(), entry.getKey());
            }
            replacement.put(entry.getKey(), entry.getValue());
        }
        DATA_PACK_RECIPES = Map.copyOf(replacement);
        rebuildIndex();
        reloadVersion++;
        registryVersion++;
    }

    private static Map<Identifier, MachineRecipe> mergedRecipes() {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>(STATIC_RECIPES);
        recipes.putAll(DATA_PACK_RECIPES);
        for (Map.Entry<Identifier, MachineRecipe> entry : DYNAMIC_RECIPES.entrySet()) {
            recipes.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return recipes;
    }

    private static void rebuildIndex() {
        Map<Identifier, TreeMap<Integer, TreeSet<MachineRecipe>>> byMachine = new LinkedHashMap<>();
        for (MachineRecipe recipe : mergedRecipes().values()) {
            TreeMap<Integer, TreeSet<MachineRecipe>> priorities = byMachine.computeIfAbsent(
                    recipe.machineId(), ignored -> new TreeMap<>());
            priorities.computeIfAbsent(recipe.priority(), ignored ->
                    new TreeSet<>(Comparator.comparing(MachineRecipe::id))).add(recipe);
        }
        BY_MACHINE = Map.copyOf(byMachine);
    }

    public static void clearAll() {
        STATIC_RECIPES.clear();
        DATA_PACK_RECIPES = Map.of();
        DYNAMIC_RECIPES = Map.of();
        BY_MACHINE = Map.of();
        reloadVersion++;
        registryVersion++;
    }

    public static void clearForTesting() {
        clearAll();
    }
}

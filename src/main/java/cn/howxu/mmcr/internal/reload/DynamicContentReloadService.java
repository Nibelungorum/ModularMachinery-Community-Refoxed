package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicContentReloadService {

    private DynamicContentReloadService() {
    }

    public static Candidate begin() {
        return new Candidate();
    }

    public static ReloadResult reload(Consumer<Candidate> producer) {
        Candidate candidate = begin();
        producer.accept(candidate);
        Map<Identifier, Machine> oldMachines = MachineRegistry.dynamicSnapshot();
        Map<Identifier, MachineRecipe> oldRecipes = RecipeRegistry.dynamicSnapshot();
        MachineRegistry.PreparedDynamic preparedMachines = MachineRegistry.prepareDynamic(candidate.machines);
        MachineDefinitions.replaceDynamic(candidate.machines);
        MachineRegistry.installDynamic(preparedMachines);
        RecipeRegistry.replaceDynamic(candidate.recipes);
        RecipeCraftingContextPool.onGlobalReload();
        return ReloadResult.from(oldMachines, candidate.machines, oldRecipes, candidate.recipes);
    }

    public static final class Candidate {

        private final Map<Identifier, Machine> machines = new LinkedHashMap<>();
        private final Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();

        public void registerMachine(Machine machine) {
            Identifier id = machine.registryName();
            if (machines.containsKey(id)) {
                throw new IllegalStateException("Dynamic machine already registered: " + id);
            }
            if (MachineDefinitions.containsStatic(id) || MachineRegistry.containsStatic(id)) {
                throw new IllegalStateException("Dynamic machine conflicts with static machine: " + id);
            }
            machines.put(id, machine);
        }

        public void registerRecipe(MachineRecipe recipe) {
            Identifier id = recipe.id();
            if (recipes.containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe already registered: " + id);
            }
            if (RecipeRegistry.containsStatic(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + id);
            }
            if (!machines.containsKey(recipe.machineId()) && !MachineRegistry.containsStatic(recipe.machineId())) {
                throw new IllegalStateException("Machine not found for dynamic recipe: " + recipe.machineId());
            }
            recipes.put(id, recipe);
        }

        public Machine getMachine(Identifier id) {
            Machine machine = machines.get(id);
            return machine != null ? machine : MachineRegistry.containsStatic(id) ? MachineRegistry.getMachine(id) : null;
        }
    }

    public record ReloadResult(
            Set<Identifier> addedMachines,
            Set<Identifier> updatedMachines,
            Set<Identifier> removedMachines,
            int addedRecipes,
            int updatedRecipes,
            int removedRecipes) {

        private static ReloadResult from(Map<Identifier, Machine> oldMachines,
                                         Map<Identifier, Machine> newMachines,
                                         Map<Identifier, MachineRecipe> oldRecipes,
                                         Map<Identifier, MachineRecipe> newRecipes) {
            return new ReloadResult(
                    addedIds(oldMachines, newMachines),
                    updatedIds(oldMachines, newMachines),
                    removedIds(oldMachines, newMachines),
                    addedIds(oldRecipes, newRecipes).size(),
                    updatedIds(oldRecipes, newRecipes).size(),
                    removedIds(oldRecipes, newRecipes).size());
        }

        private static <T> Set<Identifier> addedIds(Map<Identifier, T> oldValues, Map<Identifier, T> newValues) {
            Set<Identifier> ids = new LinkedHashSet<>(newValues.keySet());
            ids.removeAll(oldValues.keySet());
            return Set.copyOf(ids);
        }

        private static <T> Set<Identifier> updatedIds(Map<Identifier, T> oldValues, Map<Identifier, T> newValues) {
            Set<Identifier> ids = new LinkedHashSet<>();
            for (Identifier id : newValues.keySet()) {
                if (oldValues.containsKey(id) && oldValues.get(id) != newValues.get(id)) {
                    ids.add(id);
                }
            }
            return Set.copyOf(ids);
        }

        private static <T> Set<Identifier> removedIds(Map<Identifier, T> oldValues, Map<Identifier, T> newValues) {
            Set<Identifier> ids = new LinkedHashSet<>(oldValues.keySet());
            ids.removeAll(newValues.keySet());
            return Set.copyOf(ids);
        }
    }
}

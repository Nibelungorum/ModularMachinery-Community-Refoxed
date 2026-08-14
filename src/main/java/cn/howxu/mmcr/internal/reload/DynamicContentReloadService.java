package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRoleValidator;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
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
        Map<Identifier, MachineStructureDefinition> oldStructures = MachineStructureRegistry.dynamicSnapshot();
        Map<Identifier, MachineRecipe> oldRecipes = RecipeRegistry.dynamicSnapshot();
        validateCandidate(candidate);
        MachineStructureRegistry.replaceDynamic(candidate.structures);
        RecipeRegistry.replaceDynamic(candidate.recipes);
        RecipeCraftingContextPool.onGlobalReload();
        return ReloadResult.from(oldStructures, candidate.structures, oldRecipes, candidate.recipes);
    }

    private static void validateCandidate(Candidate candidate) {
        Map<Identifier, MachineRegistration> registrations = new LinkedHashMap<>();
        for (Map.Entry<Identifier, MachineStructureDefinition> entry : candidate.structures.entrySet()) {
            Identifier id = entry.getKey();
            MachineRegistration registration = MachineDefinitions.getRegistration(id);
            if (registration == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            registrations.put(id, registration.withPattern(entry.getValue().pattern()));
        }
        MachineRoleValidator.validate(registrations.values(), null);
    }

    public static final class Candidate {

        private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        private final Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();

        public void registerStructure(MachineStructureDefinition structure) {
            Identifier id = structure.machineId();
            if (structures.containsKey(id)) {
                throw new IllegalStateException("Dynamic structure already registered: " + id);
            }
            if (MachineDefinitions.getRegistration(id) == null) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            structures.put(id, structure);
        }

        public void registerRecipe(MachineRecipe recipe) {
            Identifier id = recipe.id();
            if (recipes.containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe already registered: " + id);
            }
            if (RecipeRegistry.containsStatic(id)) {
                throw new IllegalStateException("Dynamic recipe conflicts with static recipe: " + id);
            }
            if (!structures.containsKey(recipe.machineId()) && MachineRegistry.getMachine(recipe.machineId()) == null) {
                throw new IllegalStateException("Machine not found for dynamic recipe: " + recipe.machineId());
            }
            recipes.put(id, recipe);
        }
    }

    public record ReloadResult(
            Set<Identifier> addedStructures,
            Set<Identifier> updatedStructures,
            Set<Identifier> removedStructures,
            int addedRecipes,
            int updatedRecipes,
            int removedRecipes) {

        private static ReloadResult from(Map<Identifier, MachineStructureDefinition> oldStructures,
                                         Map<Identifier, MachineStructureDefinition> newStructures,
                                         Map<Identifier, MachineRecipe> oldRecipes,
                                         Map<Identifier, MachineRecipe> newRecipes) {
            return new ReloadResult(
                    addedIds(oldStructures, newStructures),
                    updatedIds(oldStructures, newStructures),
                    removedIds(oldStructures, newStructures),
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

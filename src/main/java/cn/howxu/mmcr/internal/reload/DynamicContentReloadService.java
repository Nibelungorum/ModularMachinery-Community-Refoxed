package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
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
        return RuntimeContentCoordinator.commitDynamic(candidate.structures, candidate.recipes);
    }

    public static RuntimeContentCoordinator.CommitResult reloadWithSnapshot(Consumer<Candidate> producer) {
        Candidate candidate = begin();
        producer.accept(candidate);
        return RuntimeContentCoordinator.commitDynamicAndSnapshot(candidate.structures, candidate.recipes);
    }

    public static RuntimeContentCoordinator.CommitResult reloadCurrentWithSnapshot() {
        return RuntimeContentCoordinator.commitCurrentDynamicAndSnapshot();
    }

    public static final class Candidate {

        private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        private final Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();

        public void registerStructure(MachineStructureDefinition structure) {
            Identifier id = structure.machineId();
            if (!MachineDefinitions.containsStatic(id)) {
                throw new IllegalStateException("No startup machine registration for structure: " + id);
            }
            if (structures.containsKey(id)) {
                throw new IllegalStateException("Dynamic structure already registered: " + id);
            }
            structures.put(id, structure);
        }

        public void registerRecipe(MachineRecipe recipe) {
            Identifier id = recipe.id();
            if (recipes.containsKey(id)) {
                throw new IllegalStateException("Dynamic recipe already registered: " + id);
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

        public static ReloadResult fromSnapshots(Map<Identifier, MachineStructureDefinition> oldStructures,
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

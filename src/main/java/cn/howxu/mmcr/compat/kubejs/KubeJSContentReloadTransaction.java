package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects server-script content until it can replace both dynamic snapshots together.
 *
 * @author howxu <dev@howxu.cn>
 */
final class KubeJSContentReloadTransaction {
    private static final ThreadLocal<KubeJSContentReloadTransaction> ACTIVE = new ThreadLocal<>();
    private static Map<Identifier, MachineStructureDefinition> publishedStructures = Map.of();
    private static Map<Identifier, MachineRecipe> publishedRecipes = Map.of();

    private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
    private final Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();

    static KubeJSContentReloadTransaction active() {
        return ACTIVE.get();
    }

    static boolean ownsRecipe(Identifier id) {
        KubeJSContentReloadTransaction active = ACTIVE.get();
        return active != null && active.recipes.containsKey(id);
    }

    static boolean ownsRecipe(MachineRecipe recipe) {
        KubeJSContentReloadTransaction active = ACTIVE.get();
        if (active == null || recipe == null) return false;
        return active.recipes.values().stream()
                .anyMatch(owned -> owned.equals(recipe.withId(owned.id())));
    }

    static void activate(KubeJSContentReloadTransaction transaction) {
        ACTIVE.set(transaction);
    }

    static void deactivate() {
        ACTIVE.remove();
    }

    static void clearPublishedForTesting() {
        publishedStructures = Map.of();
        publishedRecipes = Map.of();
    }

    void registerStructure(MachineStructureDefinition structure) {
        Identifier id = structure.machineId();
        if (structures.putIfAbsent(id, structure) != null) {
            throw new IllegalStateException("Dynamic structure already registered: " + id);
        }
    }

    void registerRecipe(MachineRecipe recipe) {
        Identifier id = recipe.id();
        if (recipes.putIfAbsent(id, recipe) != null) {
            throw new IllegalStateException("Dynamic recipe already registered: " + id);
        }
        MMCR.LOG.debug("[MMCR-DIAG] KubeJS queued recipe {} for {}: requirements={}, levels={}",
                id, recipe.machineId(), recipe.runtimeRequirements().size(), recipe.levelRequirements());
    }

    boolean isEmpty() {
        return structures.isEmpty() && recipes.isEmpty();
    }

    RuntimeContentCoordinator.CommitResult commit() {
        MMCR.LOG.debug("[MMCR-DIAG] KubeJS committing {} recipes and {} structures; replacing {} previously published recipes",
                recipes.size(), structures.size(), publishedRecipes.size());
        Map<Identifier, MachineStructureDefinition> mergedStructures = new LinkedHashMap<>(
                MachineStructureRegistry.dynamicSnapshot());
        removePublishedStructures(mergedStructures);
        mergedStructures.putAll(structures);
        Map<Identifier, MachineRecipe> mergedRecipes = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
        removePublishedRecipes(mergedRecipes);
        mergedRecipes.putAll(recipes);
        RuntimeContentCoordinator.CommitResult committed =
                RuntimeContentCoordinator.commitDynamicAndSnapshot(mergedStructures, mergedRecipes);
        publishedStructures = Map.copyOf(structures);
        publishedRecipes = Map.copyOf(recipes);
        MMCR.LOG.debug("[MMCR-DIAG] KubeJS committed {} recipes and {} structures", publishedRecipes.size(),
                publishedStructures.size());
        return committed;
    }

    private static void removePublishedStructures(Map<Identifier, MachineStructureDefinition> mergedStructures) {
        publishedStructures.forEach((id, structure) -> mergedStructures.computeIfPresent(id,
                (ignored, current) -> current.equals(structure) ? null : current));
    }

    private static void removePublishedRecipes(Map<Identifier, MachineRecipe> mergedRecipes) {
        publishedRecipes.forEach((id, recipe) -> mergedRecipes.computeIfPresent(id,
                (ignored, current) -> current.equals(recipe) ? null : current));
    }

}

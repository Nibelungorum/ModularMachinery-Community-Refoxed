package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects server-script content until it can replace both dynamic snapshots together.
 *
 * @author howxu <dev@howxu.cn>
 */
final class KubeJSContentReloadTransaction {
    private static final ThreadLocal<KubeJSContentReloadTransaction> ACTIVE = new ThreadLocal<>();
    private static Map<ResourceLocation, MachineStructureDefinition> publishedStructures = Map.of();
    private static Map<ResourceLocation, MachineRecipe> publishedRecipes = Map.of();

    private final Map<ResourceLocation, MachineStructureDefinition> structures = new LinkedHashMap<>();
    private final Map<ResourceLocation, MachineRecipe> recipes = new LinkedHashMap<>();

    static KubeJSContentReloadTransaction active() {
        return ACTIVE.get();
    }

    static boolean ownsRecipe(ResourceLocation id) {
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
        ResourceLocation id = structure.machineId();
        if (structures.putIfAbsent(id, structure) != null) {
            throw new IllegalStateException("Dynamic structure already registered: " + id);
        }
    }

    void registerRecipe(MachineRecipe recipe) {
        ResourceLocation id = recipe.id();
        if (recipes.putIfAbsent(id, recipe) != null) {
            throw new IllegalStateException("Dynamic recipe already registered: " + id);
        }
    }

    boolean hasPublishableContent() {
        PreparedContent content = prepareContent();
        return RuntimeContentCoordinator.hasPublishableDynamicContent(content.structures(), content.recipes(),
                structures.keySet(), content.transactionRecipes().keySet());
    }

    RuntimeContentCoordinator.CommitResult commit() {
        PreparedContent content = prepareContent();
        RuntimeContentCoordinator.CommitResult committed = RuntimeContentCoordinator.commitDynamicAndSnapshot(
                content.structures(), content.recipes());
        if (!content.errors().isEmpty()) {
            DynamicContentReloadService.ReloadResult result = committed.result();
            List<MachineRecipeJson.RecipeJsonException> errors = new ArrayList<>(result.errors());
            errors.addAll(content.errors());
            committed = new RuntimeContentCoordinator.CommitResult(
                    new DynamicContentReloadService.ReloadResult(result.addedStructures(), result.updatedStructures(),
                            result.removedStructures(), result.addedRecipes(), result.updatedRecipes(),
                            result.removedRecipes(), errors), committed.snapshot());
        }
        Map<ResourceLocation, MachineRecipe> validRecipes = new LinkedHashMap<>(content.transactionRecipes());
        committed.result().errors().forEach(error -> validRecipes.remove(error.recipeId()));
        publishedStructures = Map.copyOf(structures);
        publishedRecipes = Map.copyOf(validRecipes);
        return committed;
    }

    private PreparedContent prepareContent() {
        Map<ResourceLocation, MachineStructureDefinition> previousStructures = MachineStructureRegistry.dynamicSnapshot();
        Map<ResourceLocation, MachineStructureDefinition> mergedStructures = new LinkedHashMap<>(previousStructures);
        removePublishedStructures(mergedStructures);
        mergedStructures.putAll(structures);
        Map<ResourceLocation, MachineRecipe> previousRecipes = RecipeRegistry.dynamicSnapshot();
        Map<ResourceLocation, MachineRecipe> mergedRecipes = new LinkedHashMap<>(previousRecipes);
        removePublishedRecipes(mergedRecipes);
        List<MachineRecipeJson.RecipeJsonException> transactionErrors = new ArrayList<>();
        Map<ResourceLocation, MachineRecipe> transactionRecipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            ResourceLocation id = entry.getKey();
            MachineRecipe recipe = entry.getValue();
            MachineRecipe existing = mergedRecipes.get(id);
            if (existing != null && !existing.recipePoolId().equals(recipe.recipePoolId())) {
                transactionErrors.add(new MachineRecipeJson.RecipeJsonException(id, "recipe_pool",
                        "recipe already belongs to pool " + existing.recipePoolId(), null));
                MMCR.LOG.warn("Skipping KubeJS recipe {} from pool {}: recipe already belongs to pool {}",
                        id, recipe.recipePoolId(), existing.recipePoolId());
                continue;
            }
            mergedRecipes.put(id, recipe);
            transactionRecipes.put(id, recipe);
        }
        return new PreparedContent(mergedStructures, mergedRecipes, transactionRecipes, transactionErrors);
    }

    private record PreparedContent(Map<ResourceLocation, MachineStructureDefinition> structures,
                                   Map<ResourceLocation, MachineRecipe> recipes,
                                   Map<ResourceLocation, MachineRecipe> transactionRecipes,
                                   List<MachineRecipeJson.RecipeJsonException> errors) {
    }

    private static void removePublishedStructures(Map<ResourceLocation, MachineStructureDefinition> mergedStructures) {
        publishedStructures.forEach((id, structure) -> mergedStructures.computeIfPresent(id,
                (ignored, current) -> current.equals(structure) ? null : current));
    }

    private static void removePublishedRecipes(Map<ResourceLocation, MachineRecipe> mergedRecipes) {
        publishedRecipes.forEach((id, recipe) -> mergedRecipes.computeIfPresent(id,
                (ignored, current) -> current.equals(recipe) ? null : current));
    }

}

package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.resource.ContextAwareReloadListener;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Loads machine recipes supplied by server data packs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeDataReloadListener extends ContextAwareReloadListener {
    private volatile Map<ResourceLocation, MachineRecipe> snapshot = Map.of();
    private volatile List<MachineRecipeJson.RecipeJsonException> errors = List.of();

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(MMCR.id("machine_recipes"), new MachineRecipeDataReloadListener());
    }

    public Map<ResourceLocation, MachineRecipe> snapshot() {
        return snapshot;
    }

    public List<MachineRecipeJson.RecipeJsonException> errors() {
        return errors;
    }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor prepareExecutor,
                                          PreparationBarrier barrier, Executor applyExecutor) {
        ResourceManager resourceManager = sharedState.resourceManager();
        return CompletableFuture.supplyAsync(() -> loadCandidate(resourceManager, getRegistryLookup()), prepareExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(candidate -> apply(candidate, resourceManager), applyExecutor);
    }

    void apply(PreparedRecipes candidate, ResourceManager resourceManager) {
        logErrors(candidate.errors());
        try {
            RuntimeContentCoordinator.replaceDataPackRecipesAndSnapshot(candidate.recipes());
            snapshot = RecipeRegistry.dataPackSnapshot();
            errors = candidate.errors();
        } catch (MachineRecipeJson.RecipeJsonException exception) {
            errors = List.of(exception);
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        } catch (RuntimeException exception) {
            errors = List.of(validationError(exception));
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        }
    }

    static Map<ResourceLocation, MachineRecipe> load(ResourceManager resourceManager, HolderLookup.Provider registries) {
        return loadCandidate(resourceManager, registries).recipes();
    }

    static PreparedRecipes loadCandidate(ResourceManager resourceManager, HolderLookup.Provider registries) {
        Map<ResourceLocation, MachineRecipe> recipes = new LinkedHashMap<>();
        List<MachineRecipeJson.RecipeJsonException> errors = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources("recipes", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceLocation recipeId = recipeIdFromResource(resourceLocation);
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("type") || !object.get("type").isJsonPrimitive()
                        || !object.get("type").getAsJsonPrimitive().isString()
                        || !MachineRecipeJson.TYPE.toString().equals(object.get("type").getAsString())) continue;
                recipes.put(recipeId, MachineRecipeJson.parse(recipeId, object, registries, ignored -> true));
            } catch (MachineRecipeJson.RecipeJsonException exception) {
                errors.add(exception);
            } catch (Exception exception) {
                errors.add(new MachineRecipeJson.RecipeJsonException(recipeId, "$",
                        exception.getMessage() == null ? "invalid recipe" : exception.getMessage(), exception));
            }
        }
        Map<ResourceLocation, MachineRecipe> validRecipes = validateAndFilter(recipes, errors);
        return new PreparedRecipes(Map.copyOf(validRecipes), List.copyOf(errors));
    }

    static ResourceLocation recipeIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        if (!path.startsWith("recipes/") || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Expected recipe resource under recipes/ ending in .json: " + resourceId);
        }
        return resourceId.withPath(path.substring("recipes/".length(), path.length() - ".json".length()));
    }

    void applySnapshot(Map<ResourceLocation, MachineRecipe> recipes) {
        PreparedRecipes candidate = prepareCandidate(recipes);
        logErrors(candidate.errors());
        try {
            publishSnapshot(candidate.recipes());
            errors = candidate.errors();
        } catch (MachineRecipeJson.RecipeJsonException exception) {
            errors = List.of(exception);
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        } catch (RuntimeException exception) {
            errors = List.of(validationError(exception));
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        }
    }

    /**
     * Applies the data-pack layer for a reload hook that can close over MinecraftServer and run runtime sync.
     */
    void applySnapshotFromServerReloadHook(Map<ResourceLocation, MachineRecipe> recipes, Runnable sync) {
        applySnapshotFromServerReloadHook(recipes, snapshot -> sync.run());
    }

    void applySnapshotFromServerReloadHook(Map<ResourceLocation, MachineRecipe> recipes,
                                           Consumer<RuntimeContentSnapshot> sync) {
        PreparedRecipes candidate = prepareCandidate(recipes);
        logErrors(candidate.errors());
        Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();
        List<MachineRecipeJson.RecipeJsonException> previousErrors = errors;
        boolean published = false;
        try {
            var committed = publishSnapshot(candidate.recipes());
            errors = candidate.errors();
            published = true;
            sync.accept(committed);
        } catch (RuntimeException | Error failure) {
            if (published) {
                boolean rolledBack = false;
                try {
                    RecipeRegistry.replaceDataPack(previous);
                    rolledBack = true;
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                snapshot = rolledBack ? previous : RecipeRegistry.dataPackSnapshot();
            } else {
                snapshot = previous;
            }
            errors = previousErrors;
            throw failure;
        }
    }

    private RuntimeContentSnapshot publishSnapshot(Map<ResourceLocation, MachineRecipe> recipes) {
        Map<ResourceLocation, MachineRecipe> replacement = Map.copyOf(recipes);
        RecipeRegistry.validateDataPackCandidate(replacement);
        RuntimeContentSnapshot committed = RuntimeContentCoordinator.replaceDataPackRecipesAndSnapshot(replacement);
        snapshot = RecipeRegistry.dataPackSnapshot();
        return committed;
    }

    private static PreparedRecipes prepareCandidate(Map<ResourceLocation, MachineRecipe> recipes) {
        if (recipes == null) throw new IllegalArgumentException("Machine recipe snapshot must not be null");
        List<MachineRecipeJson.RecipeJsonException> errors = new ArrayList<>();
        Map<ResourceLocation, MachineRecipe> validRecipes = validateAndFilter(recipes, errors);
        return new PreparedRecipes(validRecipes, errors);
    }

    private static Map<ResourceLocation, MachineRecipe> validateAndFilter(
            Map<ResourceLocation, MachineRecipe> recipes,
            List<MachineRecipeJson.RecipeJsonException> errors) {
        Map<ResourceLocation, MachineRecipe> validRecipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MachineRecipe> entry : recipes.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            try {
                MachineRecipe recipe = entry.getValue();
                if (recipeId == null || recipe == null || recipe.id() == null) {
                    throw new MachineRecipeJson.RecipeJsonException(
                            recipeId == null ? MMCR.id("machine_recipe_reload") : recipeId, "$",
                            "Recipe key or recipe id must not be null", null);
                }
                if (!MachineRegistry.containsRecipePool(recipe.recipePoolId())) {
                    throw new MachineRecipeJson.RecipeJsonException(recipeId, "recipe_pool",
                            "unknown recipe pool " + recipe.recipePoolId(), null);
                }
                MachineRecipe normalizedRecipe = recipe.id().equals(recipeId) ? recipe : recipe.withId(recipeId);
                RecipeRegistry.validateDataPackCandidate(Map.of(recipeId, normalizedRecipe));
                MachineRecipe conflicting = conflictingLayerRecipe(normalizedRecipe);
                if (conflicting != null) {
                    throw new MachineRecipeJson.RecipeJsonException(recipeId, "recipe_pool",
                            "recipe already belongs to pool " + conflicting.recipePoolId(), null);
                }
                validRecipes.put(recipeId, normalizedRecipe);
            } catch (MachineRecipeJson.RecipeJsonException exception) {
                errors.add(exception);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null ? "candidate validation failed" : exception.getMessage();
                errors.add(new MachineRecipeJson.RecipeJsonException(
                        recipeId == null ? MMCR.id("machine_recipe_reload") : recipeId, "$", message, exception));
            }
        }
        return validRecipes;
    }

    private static MachineRecipe conflictingLayerRecipe(MachineRecipe recipe) {
        for (Map<ResourceLocation, MachineRecipe> layer : List.of(RecipeRegistry.staticSnapshot(),
                RecipeRegistry.kubeJSSnapshot(), RecipeRegistry.dynamicSnapshot())) {
            MachineRecipe existing = layer.get(recipe.id());
            if (existing != null && !existing.recipePoolId().equals(recipe.recipePoolId())) {
                return existing;
            }
        }
        return null;
    }

    private static void logErrors(List<MachineRecipeJson.RecipeJsonException> errors) {
        errors.forEach(error -> MMCR.LOG.error("Failed to load machine recipe {}", error.recipeId(), error));
    }

    private static MachineRecipeJson.RecipeJsonException validationError(RuntimeException exception) {
        String message = exception.getMessage() == null ? "candidate validation failed" : exception.getMessage();
        return new MachineRecipeJson.RecipeJsonException(MMCR.id("machine_recipe_reload"), "$", message, exception);
    }

    record PreparedRecipes(Map<ResourceLocation, MachineRecipe> recipes,
                           List<MachineRecipeJson.RecipeJsonException> errors) {
        PreparedRecipes {
            recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
            errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }
}

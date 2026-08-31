package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads machine recipes supplied by server data packs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeDataReloadListener extends SimplePreparableReloadListener<MachineRecipeDataReloadListener.PreparedRecipes> {
    private final HolderLookup.Provider registries;
    private volatile Map<Identifier, MachineRecipe> snapshot = Map.of();
    private volatile List<MachineRecipeJson.RecipeJsonException> errors = List.of();

    public MachineRecipeDataReloadListener(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(MMCR.id("machine_recipes"), new MachineRecipeDataReloadListener(event.getRegistryAccess()));
    }

    public Map<Identifier, MachineRecipe> snapshot() {
        return snapshot;
    }

    public List<MachineRecipeJson.RecipeJsonException> errors() {
        return errors;
    }

    @Override
    protected PreparedRecipes prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return loadCandidate(resourceManager, registries);
    }

    @Override
    protected void apply(PreparedRecipes candidate, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!candidate.errors().isEmpty()) {
            errors = candidate.errors();
            candidate.errors().forEach(error -> MMCR.LOG.error("Failed to load machine recipe {}", error.recipeId(), error));
            return;
        }
        try {
            RuntimeContentCoordinator.replaceDataPackRecipesAndSnapshot(candidate.recipes());
            snapshot = candidate.recipes();
            errors = List.of();
        } catch (MachineRecipeJson.RecipeJsonException exception) {
            errors = List.of(exception);
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        } catch (RuntimeException exception) {
            errors = List.of(validationError(exception));
            MMCR.LOG.error("Failed to publish machine recipe data-pack snapshot", exception);
        }
    }

    static Map<Identifier, MachineRecipe> load(ResourceManager resourceManager, HolderLookup.Provider registries) {
        return loadCandidate(resourceManager, registries).recipes();
    }

    static PreparedRecipes loadCandidate(ResourceManager resourceManager, HolderLookup.Provider registries) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
        List<MachineRecipeJson.RecipeJsonException> errors = new java.util.ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager.listResources("recipes", path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier resourceLocation = entry.getKey();
            Identifier recipeId = Identifier.fromNamespaceAndPath(resourceLocation.getNamespace(),
                    resourceLocation.getPath().substring("recipes/".length(), resourceLocation.getPath().length() - ".json".length()));
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("type") || !object.get("type").isJsonPrimitive()
                        || !object.get("type").getAsJsonPrimitive().isString()
                        || !MachineRecipeJson.TYPE.toString().equals(object.get("type").getAsString())) continue;
                recipes.put(recipeId, MachineRecipeJson.parse(recipeId, object, registries));
            } catch (MachineRecipeJson.RecipeJsonException exception) {
                errors.add(exception);
            } catch (Exception exception) {
                errors.add(new MachineRecipeJson.RecipeJsonException(recipeId, "$",
                        exception.getMessage() == null ? "invalid recipe" : exception.getMessage(), exception));
            }
        }
        if (errors.isEmpty()) {
            try {
                validateCandidate(recipes);
                RecipeRegistry.validateDataPackCandidate(recipes);
            } catch (MachineRecipeJson.RecipeJsonException exception) {
                errors.add(exception);
            } catch (RuntimeException exception) {
                errors.add(validationError(exception));
            }
        }
        return new PreparedRecipes(Map.copyOf(recipes), List.copyOf(errors));
    }

    void applySnapshot(Map<Identifier, MachineRecipe> recipes) {
        publishSnapshot(recipes);
    }

    /**
     * Applies the data-pack layer for a reload hook that can close over MinecraftServer and run runtime sync.
     */
    void applySnapshotFromServerReloadHook(Map<Identifier, MachineRecipe> recipes, Runnable sync) {
        applySnapshotFromServerReloadHook(recipes, snapshot -> sync.run());
    }

    void applySnapshotFromServerReloadHook(Map<Identifier, MachineRecipe> recipes,
                                           Consumer<RuntimeContentSnapshot> sync) {
        Map<Identifier, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();
        boolean published = false;
        try {
            var committed = publishSnapshot(recipes);
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
            throw failure;
        }
    }

    private RuntimeContentSnapshot publishSnapshot(Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRecipe> replacement = Map.copyOf(recipes);
        validateCandidate(replacement);
        RuntimeContentSnapshot committed = RuntimeContentCoordinator.replaceDataPackRecipesAndSnapshot(replacement);
        snapshot = replacement;
        return committed;
    }

    private static void validateCandidate(Map<Identifier, MachineRecipe> recipes) {
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            MachineRecipe recipe = entry.getValue();
            if (MachineRegistry.getMachine(recipe.machineId()) == null
                    && !MachineDefinitions.containsStatic(recipe.machineId())) {
                throw new MachineRecipeJson.RecipeJsonException(entry.getKey(), "machine",
                        "unknown machine " + recipe.machineId(), null);
            }
        }
    }

    private static MachineRecipeJson.RecipeJsonException validationError(RuntimeException exception) {
        String message = exception.getMessage() == null ? "candidate validation failed" : exception.getMessage();
        return new MachineRecipeJson.RecipeJsonException(MMCR.id("machine_recipe_reload"), "$", message, exception);
    }

    record PreparedRecipes(Map<Identifier, MachineRecipe> recipes,
                           List<MachineRecipeJson.RecipeJsonException> errors) {
        PreparedRecipes {
            recipes = Map.copyOf(recipes == null ? Map.of() : recipes);
            errors = List.copyOf(errors == null ? List.of() : errors);
        }
    }
}

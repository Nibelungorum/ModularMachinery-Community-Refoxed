package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
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
import java.util.Map;

/**
 * Loads machine recipes supplied by server data packs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeDataReloadListener extends SimplePreparableReloadListener<Map<Identifier, MachineRecipe>> {
    private final HolderLookup.Provider registries;
    private Map<Identifier, MachineRecipe> snapshot = Map.of();

    public MachineRecipeDataReloadListener(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public static void register(AddServerReloadListenersEvent event) {
        event.addListener(MMCR.id("machine_recipes"), new MachineRecipeDataReloadListener(event.getRegistryAccess()));
    }

    public Map<Identifier, MachineRecipe> snapshot() {
        return snapshot;
    }

    @Override
    protected Map<Identifier, MachineRecipe> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return load(resourceManager, registries);
    }

    @Override
    protected void apply(Map<Identifier, MachineRecipe> recipes, ResourceManager resourceManager, ProfilerFiller profiler) {
        // Vanilla data-pack reload does not expose MinecraftServer to this listener apply hook.
        // Call applySnapshotFromServerReloadHook when a server-aware reload integration is available.
        applySnapshot(recipes);
    }

    static Map<Identifier, MachineRecipe> load(ResourceManager resourceManager, HolderLookup.Provider registries) {
        Map<Identifier, MachineRecipe> recipes = new LinkedHashMap<>();
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
            } catch (Exception exception) {
                MMCR.LOG.error("Failed to load machine recipe {} from {}", recipeId, resourceLocation, exception);
            }
        }
        return Map.copyOf(recipes);
    }

    void applySnapshot(Map<Identifier, MachineRecipe> recipes) {
        publishSnapshot(recipes);
    }

    /**
     * Applies the data-pack layer for a reload hook that can close over MinecraftServer and run runtime sync.
     */
    void applySnapshotFromServerReloadHook(Map<Identifier, MachineRecipe> recipes, Runnable sync) {
        publishSnapshot(recipes);
        sync.run();
    }

    private void publishSnapshot(Map<Identifier, MachineRecipe> recipes) {
        Map<Identifier, MachineRecipe> replacement = Map.copyOf(recipes);
        RecipeRegistry.replaceDataPack(replacement);
        snapshot = replacement;
    }
}

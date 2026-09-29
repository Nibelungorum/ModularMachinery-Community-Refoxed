package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.RecipeCandidateIndex;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.MapCodec;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.IOException;
import java.util.List;

import java.nio.charset.StandardCharsets;
import net.minecraft.core.HolderLookup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeDataReloadListenerTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        registries = VanillaRegistries.createLookup();
    }

    @BeforeEach
    void clearRecipeLayers() {
        RecipeRegistry.clearForTesting();
        TestBootstrap.registerRuntimeBuiltins();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void derivesRecipeIdFromResourcePath() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                ResourceLocation.parse("mmcr_test:recipes/nested/custom_recipe.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(ResourceLocation.parse("mmcr_test:nested/custom_recipe"));
    }

    @Test
    void deletedRecipeIsAbsentAfterSecondReload() {
        var listener = new MachineRecipeDataReloadListener();
        listener.applySnapshot(Map.of(ResourceLocation.parse("mmcr_test:old_recipe"), recipe()));
        listener.applySnapshot(Map.of(ResourceLocation.parse("mmcr_test:new_recipe"), recipe()));

        assertThat(listener.snapshot()).containsOnlyKeys(ResourceLocation.parse("mmcr_test:new_recipe"));
        assertThatThrownBy(() -> listener.snapshot().put(ResourceLocation.parse("mmcr_test:other_recipe"), recipe()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyingSnapshotPublishesDataPackLayerToRecipeRegistry() {
        var id = ResourceLocation.parse("mmcr_test:published_recipe");
        var recipe = RecipeTestSupport.create(id, ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        var listener = new MachineRecipeDataReloadListener();

        listener.applySnapshot(Map.of(id, recipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(recipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, recipe);
        RecipeRegistry.replaceDataPack(Map.of());
    }

    @Test
    void applyingSnapshotExposesOnlyRecipesFromRegisteredPools() {
        var validId = ResourceLocation.parse("mmcr_test:published_valid_recipe");
        var orphanId = ResourceLocation.parse("mmcr_test:published_orphan_recipe");
        var valid = RecipeTestSupport.create(validId, ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        var orphan = RecipeTestSupport.create(orphanId, ResourceLocation.parse("mmcr:missing_recipe_pool"), 1,
                List.of(), List.of());
        var listener = new MachineRecipeDataReloadListener();

        listener.applySnapshot(Map.of(validId, valid, orphanId, orphan));

        assertThat(listener.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(orphanId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains("mmcr:missing_recipe_pool");
        });
        assertThat(listener.snapshot()).containsOnlyKeys(validId);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsOnlyKeys(validId);
    }

    @Test
    void invalid_recipe_is_reported_while_valid_recipe_continues_publishing() {
        var oldId = ResourceLocation.parse("mmcr_test:previous_recipe");
        var listener = new MachineRecipeDataReloadListener();
        listener.applySnapshot(Map.of(oldId, recipe()));

        String invalid = "{" 
                + "\"type\":\"mmcr:machine_recipe\","
                + "\"recipe_pool\":\"mmcr:test_machine_name\","
                + "\"tick_time\":20,"
                + "\"requirements\":[{\"type\":\"mmcr_test:missing\"}]}";
        var resourceManager = resources(Map.of(
                ResourceLocation.parse("mmcr_test:recipes/invalid.json"), resource(invalid),
                ResourceLocation.parse("mmcr_test:recipes/valid.json"), resource(recipeJson())));
        var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);

        listener.apply(candidate, resourceManager);

        assertThat(listener.errors()).singleElement()
                .satisfies(error -> assertThat(error.path()).isEqualTo("requirements[0]"));
        assertThat(RecipeRegistry.dataPackSnapshot()).doesNotContainKey(oldId)
                .containsKey(ResourceLocation.parse("mmcr_test:valid"));
        assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_test:valid"))).isNotNull();
    }

    @Test
    void orphan_pool_recipe_is_reported_and_valid_recipe_continues_publishing() {
        var listener = new MachineRecipeDataReloadListener();
        String orphan = recipeJson().replace("mmcr:test_machine_name", "mmcr:missing_recipe_pool");
        var resourceManager = resources(Map.of(
                ResourceLocation.parse("mmcr_test:recipes/orphan.json"), resource(orphan),
                ResourceLocation.parse("mmcr_test:recipes/valid.json"), resource(recipeJson())));

        var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);

        listener.apply(candidate, resourceManager);

        assertThat(listener.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(ResourceLocation.parse("mmcr_test:orphan"));
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains("mmcr:missing_recipe_pool");
        });
        assertThat(listener.snapshot()).containsOnlyKeys(ResourceLocation.parse("mmcr_test:valid"));
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_test:valid"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_test:orphan"))).isNull();
    }

    @Test
    void cross_pool_data_pack_recipe_is_reported_and_valid_recipe_continues_publishing() {
        var conflictingId = ResourceLocation.parse("mmcr_test:cross_pool_datapack_conflict");
        var validId = ResourceLocation.parse("mmcr_test:cross_pool_datapack_valid");
        var dynamicRecipe = RecipeTestSupport.create(conflictingId, ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        var dataPackRecipe = RecipeTestSupport.create(conflictingId, ResourceLocation.parse("mmcr:controller_tick"), 2,
                List.of(), List.of());
        var validRecipe = RecipeTestSupport.create(validId, ResourceLocation.parse("mmcr:controller_tick"), 3,
                List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(conflictingId, dynamicRecipe));
        var listener = new MachineRecipeDataReloadListener();

        listener.applySnapshot(Map.of(conflictingId, dataPackRecipe, validId, validRecipe));

        assertThat(listener.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(conflictingId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains(dynamicRecipe.recipePoolId().toString());
        });
        assertThat(listener.snapshot()).containsOnlyKeys(validId);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(validId, validRecipe)
                .doesNotContainKey(conflictingId);
        assertThat(RecipeRegistry.getRecipe(conflictingId)).isSameAs(dynamicRecipe);
    }

    @Test
    void successful_candidate_rebuilds_the_machine_catalog_once() {
        var listener = new MachineRecipeDataReloadListener();
        var machineId = ResourceLocation.parse("mmcr:test_machine_name");
        var before = RecipeRegistry.catalogForPool(machineId);
        var resourceManager = resources(Map.of(
                ResourceLocation.parse("mmcr_test:recipes/valid.json"), resource(recipeJson())));
        var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);

        RecipeCandidateIndex.resetBuildCountForTesting();
        listener.apply(candidate, resourceManager);

        assertThat(listener.errors()).isEmpty();
        assertThat(RecipeCandidateIndex.buildCountForTesting()).isEqualTo(1);
        var published = RecipeRegistry.catalogForPool(machineId);
        assertThat(published.version()).isGreaterThan(before.version());
        assertThat(published.inputIndex().allCandidates()).containsExactlyElementsOf(published.orderedRecipes());
        RecipeRegistry.replaceDataPack(Map.of());
    }

    @Test
    void candidate_validation_reports_the_offending_recipe_id_and_json_path() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(INVALID_OUTPUT_TYPE);
            var listener = new MachineRecipeDataReloadListener();
            var oldId = ResourceLocation.parse("mmcr_test:old_recipe");
            listener.applySnapshot(Map.of(oldId, recipe()));
            Map<ResourceLocation, Resource> resourceMap = new LinkedHashMap<>();
            resourceMap.put(ResourceLocation.parse("mmcr_test:recipes/valid.json"), resource(recipeJson()));
            resourceMap.put(ResourceLocation.parse("mmcr_test:recipes/invalid.json"), resource(invalidOutputRecipeJson()));

            var resourceManager = resources(resourceMap);
            var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);
            listener.apply(candidate, resourceManager);

            assertThat(listener.errors()).isEmpty();
            assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
            assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_test:valid"))).isNotNull();
            assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_test:invalid"))).isNotNull();
        }
    }

    @Test
    void sync_failure_after_publication_restores_previous_data_pack_snapshot() {
        var listener = new MachineRecipeDataReloadListener();
        var oldId = ResourceLocation.parse("mmcr_test:old_snapshot");
        var newId = ResourceLocation.parse("mmcr_test:new_snapshot");
        listener.applySnapshot(Map.of(oldId, recipe()));
        Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();

        assertThatThrownBy(() -> listener.applySnapshotFromServerReloadHook(Map.of(newId, recipe()), committed -> {
            throw new IllegalStateException("sync failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(listener.snapshot()).isEqualTo(previous);
        assertThat(RecipeRegistry.dataPackSnapshot()).isEqualTo(previous);
        assertThat(RecipeRegistry.getRecipe(oldId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(newId)).isNull();
    }

    @Test
    void sync_failure_after_publication_restores_previous_reload_errors() {
        var listener = new MachineRecipeDataReloadListener();
        var oldId = ResourceLocation.parse("mmcr_test:old_error_snapshot");
        var orphanId = ResourceLocation.parse("mmcr_test:old_error_orphan");
        MachineRecipe oldRecipe = RecipeTestSupport.create(oldId, ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        MachineRecipe orphan = RecipeTestSupport.create(orphanId, ResourceLocation.parse("mmcr:missing_error_pool"), 1,
                List.of(), List.of());
        listener.applySnapshot(Map.of(oldId, oldRecipe, orphanId, orphan));
        List<MachineRecipeJson.RecipeJsonException> previousErrors = listener.errors();

        assertThatThrownBy(() -> listener.applySnapshotFromServerReloadHook(
                Map.of(ResourceLocation.parse("mmcr_test:new_error_snapshot"), recipe()), committed -> {
                    throw new IllegalStateException("sync failed");
                })).isInstanceOf(IllegalStateException.class);

        assertThat(listener.errors()).isEqualTo(previousErrors);
    }

    @Test
    void coordinatorDataPackReplacementPreservesStaticAndDynamicLayers() {
        var staticId = ResourceLocation.parse("mmcr_test:static_layer_recipe");
        var dynamicId = ResourceLocation.parse("mmcr_test:dynamic_layer_recipe");
        var dataPackId = ResourceLocation.parse("mmcr_test:datapack_layer_recipe");
        var staticRecipe = RecipeTestSupport.create(staticId, ResourceLocation.parse("mmcr:test_machine_name"), 1, List.of(), List.of());
        var dynamicRecipe = RecipeTestSupport.create(dynamicId, ResourceLocation.parse("mmcr:test_machine_name"), 2, List.of(), List.of());
        var dataPackRecipe = RecipeTestSupport.create(dataPackId, ResourceLocation.parse("mmcr:test_machine_name"), 3, List.of(), List.of());
        RecipeRegistry.registerStatic(staticRecipe);
        RecipeRegistry.replaceDynamic(Map.of(dynamicId, dynamicRecipe));

        RuntimeContentCoordinator.replaceDataPackRecipes(Map.of(dataPackId, dataPackRecipe));

        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(staticId, staticRecipe);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(dynamicId, dynamicRecipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(dataPackId, dataPackRecipe);
        assertThat(RecipeRegistry.effectiveSnapshot()).containsKeys(staticId, dynamicId, dataPackId);
    }

    @Test
    void serverReloadHookAppliesSnapshotAndRunsSyncAfterPublishingDataPackLayer() {
        var id = ResourceLocation.parse("mmcr_test:published_sync_recipe");
        var recipe = RecipeTestSupport.create(id, ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        var listener = new MachineRecipeDataReloadListener();
        AtomicBoolean synced = new AtomicBoolean();

        listener.applySnapshotFromServerReloadHook(Map.of(id, recipe), committed -> {
            assertThat(RecipeRegistry.getRecipe(id)).isSameAs(recipe);
            assertThat(committed.recipes()).containsEntry(id, recipe);
            synced.set(true);
        });

        assertThat(synced).isTrue();
        RecipeRegistry.replaceDataPack(Map.of());
    }

    @Test
    void malformedRecipeDoesNotPreventOtherRecipesFromLoading() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                ResourceLocation.parse("mmcr_test:recipes/bad.json"), resource("{ invalid"),
                ResourceLocation.parse("mmcr_test:recipes/good.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(ResourceLocation.parse("mmcr_test:good"));
    }

    @Test
    void nonMachineRecipeFilesAreIgnoredBeforeMachineRecipeParsing() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                ResourceLocation.parse("minecraft:recipes/vanilla.json"), resource("{\"type\":\"minecraft:crafting_shaped\"}"),
                ResourceLocation.parse("mmcr_test:recipes/good.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(ResourceLocation.parse("mmcr_test:good"));
    }

    @Test
    void missingOrNonStringTypeIsIgnoredWithoutErroringAsMachineRecipe() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                ResourceLocation.parse("minecraft:recipes/missing_type.json"), resource("{}"),
                ResourceLocation.parse("minecraft:recipes/object_type.json"), resource("{\"type\":{}}"))), registries);

        assertThat(snapshot).isEmpty();
    }

    private static ResourceManager resources(Map<ResourceLocation, Resource> resources) {
        return (ResourceManager) Proxy.newProxyInstance(
                MachineRecipeDataReloadListenerTest.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, arguments) -> method.getName().equals("listResources") ? resources : null);
    }

    private static Resource resourceFromTestData() throws IOException {
        InputStream input = MachineRecipeDataReloadListenerTest.class.getClassLoader()
                .getResourceAsStream("data/mmcr_test/recipes/datapack_machine_recipe.json");
        assertThat(input).isNotNull();
        try (input) {
            return resource(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Resource resource(String json) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
                MachineRecipeDataReloadListenerTest.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, arguments) -> null);
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String recipeJson() {
        return "{\"type\":\"mmcr:machine_recipe\",\"recipe_pool\":\"mmcr:test_machine_name\",\"tick_time\":20,\"requirements\":[]}";
    }

    private static String invalidOutputRecipeJson() {
        return "{\"type\":\"mmcr:machine_recipe\",\"recipe_pool\":\"mmcr:test_machine_name\","
                + "\"tick_time\":20,\"requirements\":[],\"outputs\":[{\"type\":\"" + INVALID_OUTPUT_ID + "\"}]}";
    }

    private static MachineRecipe recipe() {
        return RecipeTestSupport.create(ResourceLocation.parse("mmcr_test:placeholder"), ResourceLocation.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
    }

    private static final ResourceLocation INVALID_OUTPUT_ID = ResourceLocation.parse("mmcr_test:invalid_output");
    private static final OutputType<InvalidOutput> INVALID_OUTPUT_TYPE = new OutputType.Definition<>(
            INVALID_OUTPUT_ID, MapCodec.unit(() -> new InvalidOutput(7, 1F)),
            (output, chance) -> new InvalidOutput(output.value(), chance),
            (output, modifiers) -> output,
            output -> new InvalidOutput(output.value(), output.chance()));

    private record InvalidOutput(int value, float chance) implements CustomOutput {
        private InvalidOutput {
            chance = MachineOutput.clampChance(chance);
        }

        @Override
        public OutputType<InvalidOutput> outputType() {
            return INVALID_OUTPUT_TYPE;
        }
    }
}

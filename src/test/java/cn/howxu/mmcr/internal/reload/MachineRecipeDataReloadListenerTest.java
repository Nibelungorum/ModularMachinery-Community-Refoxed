package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
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
import net.minecraft.resources.Identifier;
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
                Identifier.parse("mmcr_test:recipes/nested/custom_recipe.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(Identifier.parse("mmcr_test:nested/custom_recipe"));
    }

    @Test
    void deletedRecipeIsAbsentAfterSecondReload() {
        var listener = new MachineRecipeDataReloadListener();
        listener.applySnapshot(Map.of(Identifier.parse("mmcr_test:old_recipe"), recipe()));
        listener.applySnapshot(Map.of(Identifier.parse("mmcr_test:new_recipe"), recipe()));

        assertThat(listener.snapshot()).containsOnlyKeys(Identifier.parse("mmcr_test:new_recipe"));
        assertThatThrownBy(() -> listener.snapshot().put(Identifier.parse("mmcr_test:other_recipe"), recipe()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyingSnapshotPublishesDataPackLayerToRecipeRegistry() {
        var id = Identifier.parse("mmcr_test:published_recipe");
        var recipe = RecipeTestSupport.create(id, Identifier.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
        var listener = new MachineRecipeDataReloadListener();

        listener.applySnapshot(Map.of(id, recipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(recipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, recipe);
        RecipeRegistry.replaceDataPack(Map.of());
    }

    @Test
    void failed_candidate_keeps_previous_snapshot_and_reports_structured_error() {
        var oldId = Identifier.parse("mmcr_test:previous_recipe");
        var listener = new MachineRecipeDataReloadListener();
        listener.applySnapshot(Map.of(oldId, recipe()));
        var previous = RecipeRegistry.dataPackSnapshot();

        String invalid = "{"
                + "\"type\":\"mmcr:machine_recipe\","
                + "\"machine\":\"mmcr:test_machine_name\","
                + "\"tick_time\":20,"
                + "\"requirements\":[{\"type\":\"mmcr_test:missing\"}]}";
        var resourceManager = resources(Map.of(
                Identifier.parse("mmcr_test:recipes/invalid.json"), resource(invalid),
                Identifier.parse("mmcr_test:recipes/valid.json"), resource(recipeJson())));
        var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);

        listener.apply(candidate, resourceManager);

        assertThat(listener.errors()).singleElement()
                .satisfies(error -> assertThat(error.path()).isEqualTo("requirements[0]"));
        assertThat(RecipeRegistry.dataPackSnapshot()).isSameAs(previous);
        assertThat(RecipeRegistry.getRecipe(oldId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr_test:valid"))).isNull();
    }

    @Test
    void successful_candidate_rebuilds_the_machine_catalog_once() {
        var listener = new MachineRecipeDataReloadListener();
        var machineId = Identifier.parse("mmcr:test_machine_name");
        var before = RecipeRegistry.catalog(machineId);
        var resourceManager = resources(Map.of(
                Identifier.parse("mmcr_test:recipes/valid.json"), resource(recipeJson())));
        var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);

        RecipeCandidateIndex.resetBuildCountForTesting();
        listener.apply(candidate, resourceManager);

        assertThat(listener.errors()).isEmpty();
        assertThat(RecipeCandidateIndex.buildCountForTesting()).isEqualTo(1);
        var published = RecipeRegistry.catalog(machineId);
        assertThat(published.version()).isGreaterThan(before.version());
        assertThat(published.inputIndex().allCandidates()).containsExactlyElementsOf(published.orderedRecipes());
        RecipeRegistry.replaceDataPack(Map.of());
    }

    @Test
    void candidate_validation_reports_the_offending_recipe_id_and_json_path() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(INVALID_OUTPUT_TYPE);
            var listener = new MachineRecipeDataReloadListener();
            var oldId = Identifier.parse("mmcr_test:old_recipe");
            listener.applySnapshot(Map.of(oldId, recipe()));
            Map<Identifier, Resource> resourceMap = new LinkedHashMap<>();
            resourceMap.put(Identifier.parse("mmcr_test:recipes/valid.json"), resource(recipeJson()));
            resourceMap.put(Identifier.parse("mmcr_test:recipes/invalid.json"), resource(invalidOutputRecipeJson()));

            var resourceManager = resources(resourceMap);
            var candidate = MachineRecipeDataReloadListener.loadCandidate(resourceManager, registries);
            listener.apply(candidate, resourceManager);

            assertThat(listener.errors()).isEmpty();
            assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
            assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr_test:valid"))).isNotNull();
            assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr_test:invalid"))).isNotNull();
        }
    }

    @Test
    void sync_failure_after_publication_restores_previous_data_pack_snapshot() {
        var listener = new MachineRecipeDataReloadListener();
        var oldId = Identifier.parse("mmcr_test:old_snapshot");
        var newId = Identifier.parse("mmcr_test:new_snapshot");
        listener.applySnapshot(Map.of(oldId, recipe()));
        Map<Identifier, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();

        assertThatThrownBy(() -> listener.applySnapshotFromServerReloadHook(Map.of(newId, recipe()), committed -> {
            throw new IllegalStateException("sync failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(listener.snapshot()).isEqualTo(previous);
        assertThat(RecipeRegistry.dataPackSnapshot()).isEqualTo(previous);
        assertThat(RecipeRegistry.getRecipe(oldId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(newId)).isNull();
    }

    @Test
    void coordinatorDataPackReplacementPreservesStaticAndDynamicLayers() {
        var staticId = Identifier.parse("mmcr_test:static_layer_recipe");
        var dynamicId = Identifier.parse("mmcr_test:dynamic_layer_recipe");
        var dataPackId = Identifier.parse("mmcr_test:datapack_layer_recipe");
        var staticRecipe = RecipeTestSupport.create(staticId, Identifier.parse("mmcr:test_machine_name"), 1, List.of(), List.of());
        var dynamicRecipe = RecipeTestSupport.create(dynamicId, Identifier.parse("mmcr:test_machine_name"), 2, List.of(), List.of());
        var dataPackRecipe = RecipeTestSupport.create(dataPackId, Identifier.parse("mmcr:test_machine_name"), 3, List.of(), List.of());
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
        var id = Identifier.parse("mmcr_test:published_sync_recipe");
        var recipe = RecipeTestSupport.create(id, Identifier.parse("mmcr:test_machine_name"), 1,
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
                Identifier.parse("mmcr_test:recipes/bad.json"), resource("{ invalid"),
                Identifier.parse("mmcr_test:recipes/good.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(Identifier.parse("mmcr_test:good"));
    }

    @Test
    void nonMachineRecipeFilesAreIgnoredBeforeMachineRecipeParsing() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                Identifier.parse("minecraft:recipes/vanilla.json"), resource("{\"type\":\"minecraft:crafting_shaped\"}"),
                Identifier.parse("mmcr_test:recipes/good.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(Identifier.parse("mmcr_test:good"));
    }

    @Test
    void missingOrNonStringTypeIsIgnoredWithoutErroringAsMachineRecipe() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                Identifier.parse("minecraft:recipes/missing_type.json"), resource("{}"),
                Identifier.parse("minecraft:recipes/object_type.json"), resource("{\"type\":{}}"))), registries);

        assertThat(snapshot).isEmpty();
    }

    private static ResourceManager resources(Map<Identifier, Resource> resources) {
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
        return "{\"type\":\"mmcr:machine_recipe\",\"machine\":\"mmcr:test_machine_name\",\"tick_time\":20,\"requirements\":[]}";
    }

    private static String invalidOutputRecipeJson() {
        return "{\"type\":\"mmcr:machine_recipe\",\"machine\":\"mmcr:test_machine_name\","
                + "\"tick_time\":20,\"requirements\":[],\"outputs\":[{\"type\":\"" + INVALID_OUTPUT_ID + "\"}]}";
    }

    private static MachineRecipe recipe() {
        return RecipeTestSupport.create(Identifier.parse("mmcr_test:placeholder"), Identifier.parse("mmcr:test_machine_name"), 1,
                List.of(), List.of());
    }

    private static final Identifier INVALID_OUTPUT_ID = Identifier.parse("mmcr_test:invalid_output");
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

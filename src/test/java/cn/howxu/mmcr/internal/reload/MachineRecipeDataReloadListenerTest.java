package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeDataReloadListenerTest {

    private static net.minecraft.core.HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        registries = VanillaRegistries.createLookup();
    }

    @BeforeEach
    void restoreMachineDefinitions() {
        TestBootstrap.registerRuntimeBuiltins();
    }

    @Test
    void loadsMachineRecipeFilesUnderDataNamespaceRecipesPath() throws Exception {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                Identifier.parse("mmcr_test:recipes/datapack_machine_recipe.json"), resourceFromTestData())), registries);

        assertThat(snapshot).containsKey(Identifier.parse("mmcr_test:datapack_machine_recipe"));
    }

    @Test
    void derivesRecipeIdFromResourcePath() {
        var snapshot = MachineRecipeDataReloadListener.load(resources(Map.of(
                Identifier.parse("mmcr_test:recipes/nested/custom_recipe.json"), resource(recipeJson()))), registries);

        assertThat(snapshot).containsOnlyKeys(Identifier.parse("mmcr_test:nested/custom_recipe"));
    }

    @Test
    void deletedRecipeIsAbsentAfterSecondReload() {
        var listener = new MachineRecipeDataReloadListener(registries);
        listener.applySnapshot(Map.of(Identifier.parse("mmcr_test:old_recipe"), recipe()));
        listener.applySnapshot(Map.of(Identifier.parse("mmcr_test:new_recipe"), recipe()));

        assertThat(listener.snapshot()).containsOnlyKeys(Identifier.parse("mmcr_test:new_recipe"));
        assertThatThrownBy(() -> listener.snapshot().put(Identifier.parse("mmcr_test:other_recipe"), recipe()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyingSnapshotPublishesDataPackLayerToRecipeRegistry() {
        var id = Identifier.parse("mmcr_test:published_recipe");
        var recipe = new MachineRecipe(id, Identifier.parse("mmcr:alloy_furnace"), 1,
                java.util.List.of(), java.util.List.of());
        var listener = new MachineRecipeDataReloadListener(registries);

        listener.applySnapshot(Map.of(id, recipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(recipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, recipe);
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

    private static Resource resourceFromTestData() throws java.io.IOException {
        InputStream input = MachineRecipeDataReloadListenerTest.class.getClassLoader()
                .getResourceAsStream("data/mmcr_test/recipes/datapack_machine_recipe.json");
        assertThat(input).isNotNull();
        try (input) {
            return resource(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static Resource resource(String json) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
                MachineRecipeDataReloadListenerTest.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, arguments) -> null);
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String recipeJson() {
        return "{\"type\":\"mmcr:machine_recipe\",\"machine\":\"mmcr:alloy_furnace\",\"tick_time\":20}";
    }

    private static MachineRecipe recipe() {
        return new MachineRecipe(Identifier.parse("mmcr_test:placeholder"), Identifier.parse("mmcr:alloy_furnace"), 1,
                java.util.List.of(), java.util.List.of());
    }
}

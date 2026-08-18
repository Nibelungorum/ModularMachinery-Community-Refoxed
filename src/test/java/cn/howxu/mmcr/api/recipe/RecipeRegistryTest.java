package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void replacingDynamicRecipesRebuildsMergedMachineIndex() {
        var staticRecipe = recipe("mmcr:static_recipe", "mmcr:static_machine");
        var dynamicRecipe = recipe("mmcr:dynamic_recipe", "mmcr:dynamic_machine");
        RecipeRegistry.register(staticRecipe);
        long version = RecipeRegistry.reloadVersion();

        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(staticRecipe);
        assertThat(RecipeRegistry.byMachineId(dynamicRecipe.machineId())).containsExactly(dynamicRecipe);

        RecipeRegistry.replaceDynamic(Map.of());

        assertThat(RecipeRegistry.getRecipe(dynamicRecipe.id())).isNull();
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.reloadVersion()).isGreaterThan(version);
    }

    @Test
    void registeringStaticRecipeDoesNotAdvanceReloadVersion() {
        long version = RecipeRegistry.reloadVersion();
        long registryVersion = RecipeRegistry.registryVersion();

        RecipeRegistry.register(recipe("mmcr:static_reload_recipe", "mmcr:static_reload_machine"));

        assertThat(RecipeRegistry.reloadVersion()).isEqualTo(version);
        assertThat(RecipeRegistry.registryVersion()).isGreaterThan(registryVersion);
    }

    @Test
    void dataPackRecipeOverridesStaticRecipeAndKeepsStaticSnapshot() {
        var id = Identifier.parse("mmcr:layered_recipe");
        var staticRecipe = recipe("mmcr:layered_recipe", "mmcr:static_machine");
        var dataPackRecipe = recipe("mmcr:layered_recipe", "mmcr:datapack_machine");
        RecipeRegistry.register(staticRecipe);

        RecipeRegistry.replaceDataPack(Map.of(id, dataPackRecipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(dataPackRecipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, dataPackRecipe);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, staticRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).isEmpty();
        assertThat(RecipeRegistry.byMachineId(dataPackRecipe.machineId())).containsExactly(dataPackRecipe);
    }

    @Test
    void replacingDataPackSnapshotRemovesDeletedRecipes() {
        var oldId = Identifier.parse("mmcr:old_datapack_recipe");
        var newId = Identifier.parse("mmcr:new_datapack_recipe");
        RecipeRegistry.replaceDataPack(Map.of(oldId, recipe(oldId.toString(), "mmcr:alloy_furnace")));

        RecipeRegistry.replaceDataPack(Map.of(newId, recipe(newId.toString(), "mmcr:alloy_furnace")));

        assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
        assertThat(RecipeRegistry.dataPackSnapshot()).containsOnlyKeys(newId);
    }

    @Test
    void effectiveCountIndexAndPriorityFollowAllThreeLayersAndDeletion() {
        var staticRecipe = recipe("mmcr:static_layered", "mmcr:layered_machine");
        var dataPackRecipe = new MachineRecipe(staticRecipe.id(), staticRecipe.machineId(), 1,
                List.of(), List.of(), List.of(), 5, 1, false, List.of(), List.of(), false, List.of(), false, Set.of());
        var dynamicRecipe = recipe("mmcr:kjs_layered", "mmcr:layered_machine");
        RecipeRegistry.register(staticRecipe);
        RecipeRegistry.replaceDataPack(Map.of(staticRecipe.id(), dataPackRecipe));
        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.recipes()).containsExactly(dataPackRecipe, dynamicRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(dynamicRecipe, dataPackRecipe);

        RecipeRegistry.replaceDataPack(Map.of());

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(dynamicRecipe, staticRecipe);
    }

    @Test
    void staticAndDataPackSnapshotsAreImmutablePublishedLayers() {
        var id = Identifier.parse("mmcr:immutable_layers");
        var recipe = recipe(id.toString(), "mmcr:layered_machine");
        RecipeRegistry.register(recipe);
        RecipeRegistry.replaceDataPack(Map.of(id, recipe("mmcr:immutable_layers", "mmcr:layered_machine")));

        assertThatThrownBy(() -> RecipeRegistry.dataPackSnapshot().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, recipe);
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }
}

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
        RecipeRegistry.registerStatic(staticRecipe);
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
    void staticRecipeIsVisibleInEffectiveSnapshot() {
        long version = RecipeRegistry.reloadVersion();
        long registryVersion = RecipeRegistry.registryVersion();

        RecipeRegistry.registerStatic(recipe("mmcr:static_reload_recipe", "mmcr:static_reload_machine"));

        assertThat(RecipeRegistry.effectiveSnapshot()).containsEntry(
                Identifier.parse("mmcr:static_reload_recipe"),
                RecipeRegistry.getRecipe(Identifier.parse("mmcr:static_reload_recipe")));
        assertThat(RecipeRegistry.reloadVersion()).isEqualTo(version);
        assertThat(RecipeRegistry.registryVersion()).isGreaterThan(registryVersion);
    }

    @Test
    void dataPackRecipeOverridesStaticRecipeAndWarns() {
        var id = Identifier.parse("mmcr:layered_recipe");
        var staticRecipe = recipe("mmcr:layered_recipe", "mmcr:static_machine");
        var dataPackRecipe = recipe("mmcr:layered_recipe", "mmcr:datapack_machine");
        RecipeRegistry.registerStatic(staticRecipe);

        RecipeRegistry.replaceDataPack(Map.of(id, dataPackRecipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(dataPackRecipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, dataPackRecipe);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, staticRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).isEmpty();
        assertThat(RecipeRegistry.byMachineId(dataPackRecipe.machineId())).containsExactly(dataPackRecipe);
        assertThat(RecipeRegistry.lastDataPackWarnings()).containsExactly(
                "data-pack layer recipe mmcr:layered_recipe overrides static layer recipe mmcr:layered_recipe");
    }

    @Test
    void dataPackKeyIsAuthoritativeWhenRecipeValueCarriesGeneratedId() {
        Identifier holderId = Identifier.parse("mmcr:explicit_datapack_recipe");
        MachineRecipe generated = recipe("mmcr:generated_recipe", "mmcr:datapack_machine");

        RecipeRegistry.replaceDataPack(Map.of(holderId, generated));

        assertThat(RecipeRegistry.getRecipe(holderId).id()).isEqualTo(holderId);
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:generated_recipe"))).isNull();
        assertThat(RecipeRegistry.byMachineId(generated.machineId())).extracting(MachineRecipe::id)
                .containsExactly(holderId);
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
    void effectiveByMachineIndexMatchesEffectiveSnapshot() {
        var staticRecipe = recipe("mmcr:static_layered", "mmcr:layered_machine");
        var dataPackRecipe = new MachineRecipe(staticRecipe.id(), staticRecipe.machineId(), 1,
                List.of(), List.of(), List.of(), 5, 1, false, List.of(), List.of(), false, List.of(), false, Set.of());
        var dynamicRecipe = recipe("mmcr:kjs_layered", "mmcr:layered_machine");
        RecipeRegistry.registerStatic(staticRecipe);
        RecipeRegistry.replaceDataPack(Map.of(staticRecipe.id(), dataPackRecipe));
        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.effectiveSnapshot().values()).containsExactly(dataPackRecipe, dynamicRecipe);
        assertThat(RecipeRegistry.recipes()).containsExactly(dataPackRecipe, dynamicRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(dynamicRecipe, dataPackRecipe);

        RecipeRegistry.replaceDataPack(Map.of());

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.byMachineId(staticRecipe.machineId())).containsExactly(dynamicRecipe, staticRecipe);
    }

    @Test
    void dynamicRecipeCannotConflictWithStaticRecipe() {
        var id = Identifier.parse("mmcr:dynamic_static_conflict");
        RecipeRegistry.registerStatic(recipe(id.toString(), "mmcr:machine"));

        assertThatThrownBy(() -> RecipeRegistry.replaceDynamic(Map.of(id, recipe(id.toString(), "mmcr:machine"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("static recipe");
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
    }

    @Test
    void dynamicRecipeCannotConflictWithDataPackRecipe() {
        var id = Identifier.parse("mmcr:dynamic_datapack_conflict");
        RecipeRegistry.replaceDataPack(Map.of(id, recipe(id.toString(), "mmcr:machine")));

        assertThatThrownBy(() -> RecipeRegistry.replaceDynamic(Map.of(id, recipe(id.toString(), "mmcr:machine"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-pack recipe");
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
    }

    @Test
    void staticAndDataPackSnapshotsAreImmutablePublishedLayers() {
        var id = Identifier.parse("mmcr:immutable_layers");
        var recipe = recipe(id.toString(), "mmcr:layered_machine");
        RecipeRegistry.registerStatic(recipe);
        RecipeRegistry.replaceDataPack(Map.of(id, recipe("mmcr:immutable_layers", "mmcr:layered_machine")));

        assertThatThrownBy(() -> RecipeRegistry.dataPackSnapshot().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, recipe);
    }

    @Test
    void dataPackOverridePublishesObservableSourceWarning() {
        var id = Identifier.parse("mmcr:warning_recipe");
        RecipeRegistry.registerStatic(recipe(id.toString(), "mmcr:warning_machine"));

        RecipeRegistry.replaceDataPack(Map.of(id, recipe(id.toString(), "mmcr:warning_machine")));

        assertThat(RecipeRegistry.lastDataPackWarnings())
                .containsExactly("data-pack layer recipe mmcr:warning_recipe overrides static layer recipe mmcr:warning_recipe");
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }
}

package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class KubeJSRecipeSyncTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        KubeJSContentReloadTransaction.deactivate();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void sync_uses_recipe_holder_id_for_kubejs_generated_machine_recipes() {
        var holderId = ResourceKey.create(Registries.RECIPE, MMCR.id("from_recipe_event"));
        var generated = new MachineRecipe(MMCR.id("generated_recipe"), MMCR.id("machine"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, generated)));

        assertThat(RecipeRegistry.getRecipe(MMCR.id("from_recipe_event"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(MMCR.id("from_recipe_event")).id()).isEqualTo(MMCR.id("from_recipe_event"));
        assertThat(RecipeRegistry.getRecipe(MMCR.id("generated_recipe"))).isNull();
    }

    @Test
    void sync_replaces_previous_dynamic_recipe_snapshot() {
        var firstId = ResourceKey.create(Registries.RECIPE, MMCR.id("first"));
        var secondId = ResourceKey.create(Registries.RECIPE, MMCR.id("second"));
        var first = new MachineRecipe(MMCR.id("generated_recipe"), MMCR.id("machine"), 1, List.of(), List.of());
        var second = new MachineRecipe(MMCR.id("generated_recipe"), MMCR.id("machine"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(firstId, first)));
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(secondId, second)));

        assertThat(RecipeRegistry.getRecipe(MMCR.id("first"))).isNull();
        assertThat(RecipeRegistry.getRecipe(MMCR.id("second"))).isNotNull();
    }

    @Test
    void sync_does_not_publish_recipe_owned_by_active_kubejs_transaction_as_datapack_content() {
        Identifier id = MMCR.id("transaction_recipe");
        MachineRecipe recipe = new MachineRecipe(id, MMCR.id("machine"), 1, List.of(), List.of());
        KubeJSContentReloadTransaction transaction = new KubeJSContentReloadTransaction();
        transaction.registerRecipe(recipe);
        KubeJSContentReloadTransaction.activate(transaction);

        ResourceKey<Recipe<?>> holderId = ResourceKey.create(Registries.RECIPE, id);
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, recipe)));

        assertThat(RecipeRegistry.dataPackSnapshot()).doesNotContainKey(id);
    }

    @Test
    void sync_does_not_replace_explicit_kubejs_id_with_generated_holder_id() {
        Identifier explicitId = MMCR.id("explicit_recipe");
        MachineRecipe explicit = new MachineRecipe(explicitId, MMCR.id("machine"), 1, List.of(), List.of());
        KubeJSContentReloadTransaction transaction = new KubeJSContentReloadTransaction();
        transaction.registerRecipe(explicit);
        KubeJSContentReloadTransaction.activate(transaction);

        Identifier generatedId = MMCR.id("generated_recipe");
        MachineRecipe generated = explicit.withId(generatedId);
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(ResourceKey.create(
                Registries.RECIPE, generatedId), generated)));

        assertThat(RecipeRegistry.getRecipe(explicitId)).isNull();
        assertThat(RecipeRegistry.getRecipe(generatedId)).isNull();
    }
}

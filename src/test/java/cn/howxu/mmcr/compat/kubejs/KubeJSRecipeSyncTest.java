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
        RecipeRegistry.clearForTesting();
    }

    @Test
    void sync_uses_recipe_holder_id_for_kubejs_generated_machine_recipes() {
        var holderId = ResourceKey.create(Registries.RECIPE, MMCR.id("from_recipe_event"));
        var generated = new MachineRecipe(MMCR.id("generated_recipe"), MMCR.id("machine"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDynamicRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, generated)));

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

        KubeJSRecipeSync.replaceDynamicRecipes(List.of(new RecipeHolder<Recipe<?>>(firstId, first)));
        KubeJSRecipeSync.replaceDynamicRecipes(List.of(new RecipeHolder<Recipe<?>>(secondId, second)));

        assertThat(RecipeRegistry.getRecipe(MMCR.id("first"))).isNull();
        assertThat(RecipeRegistry.getRecipe(MMCR.id("second"))).isNotNull();
    }
}

package cn.howxu.mmcr.internal.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeInformation;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class RecipeInformationRegistryTest {

    @AfterEach
    void clearRegistry() {
        RecipeInformationRegistry.clearForTesting();
    }

    @Test
    void lookup_orders_sources_and_targets_and_preserves_duplicates() {
        var poolId = MMCR.id("information_pool");
        var recipeId = MMCR.id("information_recipe");
        RecipeInformation duplicate = RecipeInformation.pool(poolId, "jei.example.public_pool");
        RecipeInformationRegistry.replacePublic(List.of(
                duplicate,
                duplicate,
                RecipeInformation.recipe(recipeId, "jei.example.public_recipe"),
                RecipeInformation.pool(MMCR.id("other_pool"), "jei.example.unrelated")));
        RecipeInformationRegistry.replaceKubeJS(List.of(
                RecipeInformation.pool(poolId, "jei.example.kubejs_pool"),
                RecipeInformation.recipe(recipeId, "jei.example.kubejs_recipe")));

        assertThat(RecipeInformationRegistry.componentsFor(poolId, recipeId)).containsExactly(
                Component.translatable("jei.example.public_pool"),
                Component.translatable("jei.example.public_pool"),
                Component.translatable("jei.example.kubejs_pool"),
                Component.translatable("jei.example.public_recipe"),
                Component.translatable("jei.example.kubejs_recipe"));
    }

    @Test
    void replacing_a_source_publishes_an_immutable_new_snapshot() {
        var poolId = MMCR.id("replaceable_information_pool");
        List<RecipeInformation> source = new ArrayList<>();
        source.add(RecipeInformation.pool(poolId, "jei.example.old"));
        RecipeInformationRegistry.replaceKubeJS(source);
        source.clear();

        assertThat(RecipeInformationRegistry.componentsFor(poolId, MMCR.id("recipe")))
                .containsExactly(Component.translatable("jei.example.old"));

        RecipeInformationRegistry.replaceKubeJS(List.of(RecipeInformation.pool(poolId, "jei.example.new")));

        assertThat(RecipeInformationRegistry.componentsFor(poolId, MMCR.id("recipe")))
                .containsExactly(Component.translatable("jei.example.new"));
    }
}

package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeSerializer;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class KubeJSRecipeSyncTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void restoreRuntimeContent() {
        TestBootstrap.registerRuntimeBuiltins();
        KubeJSContentReloadTransaction.clearPublishedForTesting();
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() {
        KubeJSContentReloadTransaction.deactivate();
        KubeJSContentReloadTransaction.clearPublishedForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void sync_uses_recipe_holder_id_for_kubejs_generated_machine_recipes() {
        var holderId = MMCR.id("from_recipe_event");
        var generated = RecipeTestSupport.create(MMCR.id("generated_recipe"), MMCR.id("test_machine_name"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, generated)));

        assertThat(RecipeRegistry.getRecipe(MMCR.id("from_recipe_event"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(MMCR.id("from_recipe_event")).id()).isEqualTo(MMCR.id("from_recipe_event"));
        assertThat(RecipeRegistry.getRecipe(MMCR.id("generated_recipe"))).isNull();
    }

    @Test
    void sync_replaces_previous_dynamic_recipe_snapshot() {
        var firstId = MMCR.id("first");
        var secondId = MMCR.id("second");
        var first = RecipeTestSupport.create(MMCR.id("generated_recipe"), MMCR.id("test_machine_name"), 1, List.of(), List.of());
        var second = RecipeTestSupport.create(MMCR.id("generated_recipe"), MMCR.id("test_machine_name"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(firstId, first)));
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(secondId, second)));

        assertThat(RecipeRegistry.getRecipe(MMCR.id("first"))).isNull();
        assertThat(RecipeRegistry.getRecipe(MMCR.id("second"))).isNotNull();
    }

    @Test
    void sync_survives_subsequent_datapack_reload() {
        var id = MMCR.id("surviving_recipe");
        var recipe = RecipeTestSupport.create(MMCR.id("generated_recipe"), MMCR.id("test_machine_name"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(id, recipe)));
        RuntimeContentCoordinator.replaceDataPackRecipes(Map.of());

        assertThat(RecipeRegistry.getRecipe(MMCR.id("surviving_recipe"))).isNotNull();
    }

    @Test
    void sync_does_not_publish_recipe_owned_by_active_kubejs_transaction_as_datapack_content() {
        ResourceLocation id = MMCR.id("transaction_recipe");
        MachineRecipe recipe = RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        KubeJSContentReloadTransaction transaction = new KubeJSContentReloadTransaction();
        transaction.registerRecipe(recipe);
        KubeJSContentReloadTransaction.activate(transaction);

        ResourceLocation holderId = id;
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, recipe)));

        assertThat(RecipeRegistry.dataPackSnapshot()).doesNotContainKey(id);
    }

    @Test
    void sync_kubejs_recipe_overrides_dynamic_recipe_with_same_id_and_pool() {
        ResourceLocation id = MMCR.id("dynamic_recipe");
        MachineRecipe dynamic = RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        MachineRecipe kubeJS = RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 2, List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(id, dynamic));

        ResourceLocation holderId = id;
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, kubeJS)));

        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(id, dynamic);
        assertThat(RecipeRegistry.kubeJSSnapshot()).containsKey(id);
        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(RecipeRegistry.kubeJSSnapshot().get(id));
        assertThat(RecipeRegistry.getRecipe(id).tickTime()).isEqualTo(2);
    }

    @Test
    void sync_keeps_dynamic_recipe_when_kubejs_same_id_belongs_to_another_pool() {
        ResourceLocation id = MMCR.id("cross_pool_dynamic_recipe");
        MachineRecipe dynamic = RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        MachineRecipe kubeJS = RecipeTestSupport.create(id, MMCR.id("controller_tick"), 2, List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(id, dynamic));

        ResourceLocation holderId = id;
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(holderId, kubeJS)));

        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(id, dynamic);
        assertThat(RecipeRegistry.kubeJSSnapshot()).doesNotContainKey(id);
        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(dynamic);
    }

    @Test
    void sync_does_not_replace_explicit_kubejs_id_with_generated_holder_id() {
        ResourceLocation explicitId = MMCR.id("explicit_recipe");
        MachineRecipe explicit = RecipeTestSupport.create(explicitId, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        KubeJSContentReloadTransaction transaction = new KubeJSContentReloadTransaction();
        transaction.registerRecipe(explicit);
        KubeJSContentReloadTransaction.activate(transaction);

        ResourceLocation generatedId = MMCR.id("generated_recipe");
        MachineRecipe generated = explicit.withId(generatedId);
        KubeJSRecipeSync.replaceDataPackRecipes(List.of(new RecipeHolder<Recipe<?>>(generatedId, generated)));

        assertThat(RecipeRegistry.getRecipe(explicitId)).isNull();
        assertThat(RecipeRegistry.getRecipe(generatedId)).isNull();
    }

    @Test
    void sync_discards_orphan_pool_and_keeps_valid_pool_recipe() {
        var validId = MMCR.id("valid_pool_recipe");
        var orphanId = MMCR.id("orphan_pool_recipe");
        var valid = RecipeTestSupport.create(validId, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        var orphan = RecipeTestSupport.create(orphanId, MMCR.id("missing_recipe_pool"), 1, List.of(), List.of());

        KubeJSRecipeSync.replaceDataPackRecipes(List.of(
                new RecipeHolder<Recipe<?>>(validId, valid),
                new RecipeHolder<Recipe<?>>(orphanId, orphan)));

        assertThat(RecipeRegistry.kubeJSSnapshot()).containsKey(validId).doesNotContainKey(orphanId);
    }

    @Test
    void transaction_reports_orphan_pool_recipe_while_publishing_valid_dynamic_recipe() {
        var validId = MMCR.id("transaction_valid_pool_recipe");
        var orphanId = MMCR.id("transaction_orphan_pool_recipe");
        var transaction = new KubeJSContentReloadTransaction();
        var valid = RecipeTestSupport.create(validId, MMCR.id("test_machine_name"), 1, List.of(), List.of());
        var orphan = RecipeTestSupport.create(orphanId, MMCR.id("missing_transaction_recipe_pool"), 1,
                List.of(), List.of());
        transaction.registerRecipe(valid);
        transaction.registerRecipe(orphan);

        var committed = transaction.commit();

        assertThat(committed.result().errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(orphanId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains("missing_transaction_recipe_pool");
        });
        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(validId, valid)
                .doesNotContainKey(orphanId);
    }

    @Test
    void legacy_json_is_rejected_before_kubejs_recipe_sync_can_publish_it() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "mmcr:machine_recipe");
        json.addProperty("recipe_pool", "mmcr:machine");
        json.addProperty("machine", "mmcr:legacy_machine");
        json.addProperty("tick_time", 20);
        json.add("requirements", new JsonArray());

        var decoded = MachineRecipeSerializer.INSTANCE.codec().codec().parse(JsonOps.INSTANCE, json);
        KubeJSRecipeSync.replaceDataPackRecipes(List.of());

        assertThat(decoded.error()).isPresent();
        assertThat(RecipeRegistry.kubeJSSnapshot()).isEmpty();
    }
}

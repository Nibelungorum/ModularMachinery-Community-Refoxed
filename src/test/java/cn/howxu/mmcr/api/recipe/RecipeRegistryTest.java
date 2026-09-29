package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
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
        TestBootstrap.restoreMachineDefinitions();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void replacingDynamicRecipesRebuildsMergedMachineIndex() {
        var staticRecipe = recipe("mmcr:static_recipe", "mmcr:test_machine_name");
        var dynamicRecipe = recipe("mmcr:dynamic_recipe", "mmcr:controller_tick");
        RecipeRegistry.registerStatic(staticRecipe);
        long version = RecipeRegistry.reloadVersion();

        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.recipesForPool(staticRecipe.recipePoolId())).containsExactly(staticRecipe);
        assertThat(RecipeRegistry.recipesForPool(dynamicRecipe.recipePoolId())).containsExactly(dynamicRecipe);

        RecipeRegistry.replaceDynamic(Map.of());

        assertThat(RecipeRegistry.getRecipe(dynamicRecipe.id())).isNull();
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.reloadVersion()).isGreaterThan(version);
    }

    @Test
    void staticRecipeIsVisibleInEffectiveSnapshot() {
        long version = RecipeRegistry.reloadVersion();
        long registryVersion = RecipeRegistry.registryVersion();

        RecipeRegistry.registerStatic(recipe("mmcr:static_reload_recipe", "mmcr:test_machine_name"));

        assertThat(RecipeRegistry.effectiveSnapshot()).containsEntry(
                ResourceLocation.parse("mmcr:static_reload_recipe"),
                RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:static_reload_recipe")));
        assertThat(RecipeRegistry.reloadVersion()).isEqualTo(version);
        assertThat(RecipeRegistry.registryVersion()).isGreaterThan(registryVersion);
    }

    @Test
    void machineCatalogResolvesConfiguredRecipePoolAndDefaultsToMachineId() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:shared_pool_machine");
        ResourceLocation recipePoolId = ResourceLocation.parse("mmcr:shared_pool");
        ResourceLocation defaultMachineId = ResourceLocation.parse("mmcr:self_pool_machine");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolId(recipePoolId).build());
        MachineDefinitions.register(MachineRegistration.builder(defaultMachineId).build());
        MachineRecipe sharedPoolRecipe = recipe("mmcr:shared_pool_recipe", recipePoolId.toString());
        MachineRecipe defaultPoolRecipe = recipe("mmcr:self_pool_recipe", defaultMachineId.toString());

        RecipeRegistry.replaceDynamic(Map.of(sharedPoolRecipe.id(), sharedPoolRecipe,
                defaultPoolRecipe.id(), defaultPoolRecipe));

        assertThat(RecipeRegistry.catalogForMachine(machineId).recipes()).containsExactly(sharedPoolRecipe);
        assertThat(RecipeRegistry.catalogForMachine(defaultMachineId).recipes()).containsExactly(defaultPoolRecipe);
    }

    @Test
    void catalogForMachineUsesRegisteredPoolAndRejectsUnknownMachineIds() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:catalog_api_machine");
        ResourceLocation recipePoolId = ResourceLocation.parse("mmcr:catalog_api_pool");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolId(recipePoolId).build());
        MachineRecipe recipe = recipe("mmcr:catalog_api_recipe", recipePoolId.toString());

        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));

        assertThat(RecipeRegistry.catalogForMachine(machineId).recipes()).containsExactly(recipe);
        assertThat(RecipeRegistry.catalogForMachine(recipePoolId).recipes()).isEmpty();
    }

    @Test
    void higherPriorityLayerCannotMoveRecipeToAnotherPool() {
        ResourceLocation firstMachineId = ResourceLocation.parse("mmcr:pool_boundary_machine_a");
        ResourceLocation secondMachineId = ResourceLocation.parse("mmcr:pool_boundary_machine_b");
        ResourceLocation firstPoolId = ResourceLocation.parse("mmcr:pool_boundary_a");
        ResourceLocation secondPoolId = ResourceLocation.parse("mmcr:pool_boundary_b");
        ResourceLocation recipeId = ResourceLocation.parse("mmcr:pool_boundary_recipe");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(firstMachineId).recipePoolId(firstPoolId).build());
        MachineDefinitions.register(MachineRegistration.builder(secondMachineId).recipePoolId(secondPoolId).build());
        MachineRecipe staticRecipe = recipe(recipeId.toString(), firstPoolId.toString());
        MachineRecipe kubeJSRecipe = recipe(recipeId.toString(), secondPoolId.toString());

        RecipeRegistry.registerStatic(staticRecipe);
        RecipeRegistry.replaceKubeJS(Map.of(recipeId, kubeJSRecipe));

        assertThat(RecipeRegistry.catalogForPool(firstPoolId).recipes()).containsExactly(staticRecipe);
        assertThat(RecipeRegistry.catalogForPool(secondPoolId).recipes()).isEmpty();
        assertThat(RecipeRegistry.kubeJSSnapshot()).doesNotContainKey(recipeId);
        assertThat(RecipeRegistry.getRecipe(recipeId)).isSameAs(staticRecipe);
    }

    @Test
    void dynamicLayerOnlyFillsARecipeGapWithinItsPool() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:dynamic_gap_machine");
        ResourceLocation poolId = ResourceLocation.parse("mmcr:dynamic_gap_pool");
        ResourceLocation recipeId = ResourceLocation.parse("mmcr:dynamic_gap_recipe");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolId(poolId).build());
        MachineRecipe kubeJSRecipe = recipe(recipeId.toString(), poolId.toString(), 1);
        MachineRecipe dynamicRecipe = recipe(recipeId.toString(), poolId.toString(), 2);

        RecipeRegistry.replaceKubeJS(Map.of(recipeId, kubeJSRecipe));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, dynamicRecipe));

        assertThat(RecipeRegistry.catalogForPool(poolId).recipes()).containsExactly(kubeJSRecipe);
        assertThat(RecipeRegistry.getRecipe(recipeId)).isSameAs(kubeJSRecipe);
    }

    @Test
    void dynamicReplacementDropsOrphanPoolsWithoutDroppingValidRecipes() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:dynamic_pool_machine");
        ResourceLocation poolId = ResourceLocation.parse("mmcr:dynamic_pool");
        ResourceLocation validId = ResourceLocation.parse("mmcr:dynamic_valid_recipe");
        ResourceLocation orphanId = ResourceLocation.parse("mmcr:dynamic_orphan_recipe");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolId(poolId).build());
        MachineRecipe valid = recipe(validId.toString(), poolId.toString());
        MachineRecipe orphan = recipe(orphanId.toString(), "mmcr:missing_dynamic_pool");

        RecipeRegistry.replaceDynamic(Map.of(validId, valid, orphanId, orphan));

        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(validId, valid).doesNotContainKey(orphanId);
        assertThat(RecipeRegistry.catalogForPool(poolId).recipes()).containsExactly(valid);
    }

    @Test
    void dynamicReplacementRejectsMismatchedMapKeyBeforePublishing() {
        ResourceLocation recipeId = ResourceLocation.parse("mmcr:dynamic_key_recipe");
        ResourceLocation mismatchedKey = ResourceLocation.parse("mmcr:dynamic_wrong_key");
        MachineRecipe recipe = recipe(recipeId.toString(), "mmcr:test_machine_name");

        assertThatThrownBy(() -> RecipeRegistry.replaceDynamic(Map.of(mismatchedKey, recipe)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Recipe key does not match recipe id");
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
        assertThat(RecipeRegistry.effectiveSnapshot()).doesNotContainKey(recipeId);
    }

    @Test
    void dataPackRecipeOverridesStaticRecipeAndWarns() {
        var id = ResourceLocation.parse("mmcr:layered_recipe");
        var staticRecipe = recipe("mmcr:layered_recipe", "mmcr:test_machine_name");
        var dataPackRecipe = recipe("mmcr:layered_recipe", "mmcr:test_machine_name");
        RecipeRegistry.registerStatic(staticRecipe);

        RecipeRegistry.replaceDataPack(Map.of(id, dataPackRecipe));

        assertThat(RecipeRegistry.getRecipe(id)).isSameAs(dataPackRecipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(id, dataPackRecipe);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, staticRecipe);
        assertThat(RecipeRegistry.recipesForPool(staticRecipe.recipePoolId())).containsExactly(dataPackRecipe);
        assertThat(RecipeRegistry.lastDataPackWarnings()).containsExactly(
                "data-pack layer recipe mmcr:layered_recipe overrides static layer recipe mmcr:layered_recipe");
    }

    @Test
    void dataPackRecipeCannotMoveAnExistingRecipeToAnotherPool() {
        ResourceLocation recipeId = ResourceLocation.parse("mmcr:cross_pool_datapack_recipe");
        ResourceLocation firstPoolId = ResourceLocation.parse("mmcr:cross_pool_datapack_a");
        ResourceLocation secondPoolId = ResourceLocation.parse("mmcr:cross_pool_datapack_b");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(ResourceLocation.parse("mmcr:cross_pool_datapack_machine_a"))
                .recipePoolId(firstPoolId).build());
        MachineDefinitions.register(MachineRegistration.builder(ResourceLocation.parse("mmcr:cross_pool_datapack_machine_b"))
                .recipePoolId(secondPoolId).build());
        MachineRecipe staticRecipe = recipe(recipeId.toString(), firstPoolId.toString());
        MachineRecipe dataPackRecipe = recipe(recipeId.toString(), secondPoolId.toString());

        RecipeRegistry.registerStatic(staticRecipe);
        RecipeRegistry.replaceDataPack(Map.of(recipeId, dataPackRecipe));

        assertThat(RecipeRegistry.getRecipe(recipeId)).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.dataPackSnapshot()).doesNotContainKey(recipeId);
        assertThat(RecipeRegistry.lastDataPackWarnings()).isEmpty();
    }

    @Test
    void dataPackKeyIsAuthoritativeWhenRecipeValueCarriesGeneratedId() {
        ResourceLocation holderId = ResourceLocation.parse("mmcr:explicit_datapack_recipe");
        MachineRecipe generated = recipe("mmcr:generated_recipe", "mmcr:test_machine_name");

        RecipeRegistry.replaceDataPack(Map.of(holderId, generated));

        assertThat(RecipeRegistry.getRecipe(holderId).id()).isEqualTo(holderId);
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:generated_recipe"))).isNull();
        assertThat(RecipeRegistry.recipesForPool(generated.recipePoolId())).extracting(MachineRecipe::id)
                .containsExactly(holderId);
    }

    @Test
    void replacingDataPackSnapshotRemovesDeletedRecipes() {
        var oldId = ResourceLocation.parse("mmcr:old_datapack_recipe");
        var newId = ResourceLocation.parse("mmcr:new_datapack_recipe");
        RecipeRegistry.replaceDataPack(Map.of(oldId, recipe(oldId.toString(), "mmcr:test_machine_name")));

        RecipeRegistry.replaceDataPack(Map.of(newId, recipe(newId.toString(), "mmcr:test_machine_name")));

        assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
        assertThat(RecipeRegistry.dataPackSnapshot()).containsOnlyKeys(newId);
    }

    @Test
    void effectiveByMachineIndexMatchesEffectiveSnapshot() {
        var staticRecipe = recipe("mmcr:static_layered", "mmcr:test_machine_name");
        var dataPackRecipe = RecipeTestSupport.create(staticRecipe.id(), staticRecipe.recipePoolId(), 1,
                List.of(), List.of(), List.of(), 5, 1, false, List.of(), List.of(), false, List.of(), false, Set.of());
        var dynamicRecipe = recipe("mmcr:kjs_layered", "mmcr:test_machine_name");
        RecipeRegistry.registerStatic(staticRecipe);
        RecipeRegistry.replaceDataPack(Map.of(staticRecipe.id(), dataPackRecipe));
        RecipeRegistry.replaceDynamic(Map.of(dynamicRecipe.id(), dynamicRecipe));

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.effectiveSnapshot().values()).containsExactly(dataPackRecipe, dynamicRecipe);
        assertThat(RecipeRegistry.recipes()).containsExactly(dataPackRecipe, dynamicRecipe);
        assertThat(RecipeRegistry.recipesForPool(staticRecipe.recipePoolId())).containsExactly(dynamicRecipe, dataPackRecipe);

        RecipeRegistry.replaceDataPack(Map.of());

        assertThat(RecipeRegistry.registeredRecipeCount()).isEqualTo(2);
        assertThat(RecipeRegistry.getRecipe(staticRecipe.id())).isSameAs(staticRecipe);
        assertThat(RecipeRegistry.recipesForPool(staticRecipe.recipePoolId())).containsExactly(dynamicRecipe, staticRecipe);
    }

    @Test
    void replacingRecipeContentPublishesNewMachineCatalogVersion() {
        ResourceLocation machineA = ResourceLocation.parse("mmcr:test_machine_name");
        MachineRecipe first = recipe("mmcr:catalog_recipe_a", machineA.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(first.id(), first));
        long firstVersion = RecipeRegistry.catalogForPool(machineA).version();

        MachineRecipe changed = recipe(first.id().toString(), machineA.toString(), 40);
        RecipeRegistry.replaceDynamic(Map.of(changed.id(), changed));

        assertThat(RecipeRegistry.catalogForPool(machineA).version()).isNotEqualTo(firstVersion);
        assertThat(RecipeRegistry.catalogForPool(machineA).recipes()).containsExactly(changed);
        assertThat(RecipeRegistry.catalogForPool(machineA).recipes().getFirst().tickTime()).isEqualTo(40);
    }

    @Test
    void changingOneMachineKeepsUnchangedMachineCatalogVersion() {
        ResourceLocation machineA = ResourceLocation.parse("mmcr:test_machine_name");
        ResourceLocation machineB = ResourceLocation.parse("mmcr:controller_tick");
        MachineRecipe firstA = recipe("mmcr:catalog_tick_recipe_a", machineA.toString(), 20);
        MachineRecipe firstB = recipe("mmcr:catalog_tick_recipe_b", machineB.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(firstA.id(), firstA, firstB.id(), firstB));
        long machineAVersion = RecipeRegistry.catalogForPool(machineA).version();

        MachineRecipe changedB = recipe(firstB.id().toString(), machineB.toString(), 60);
        RecipeRegistry.replaceDynamic(Map.of(firstA.id(), firstA, changedB.id(), changedB));

        assertThat(RecipeRegistry.catalogForPool(machineA).version()).isEqualTo(machineAVersion);
        assertThat(RecipeRegistry.catalogForPool(machineA).recipes()).containsExactly(firstA);
        assertThat(RecipeRegistry.catalogForPool(machineB).recipes()).containsExactly(changedB);
    }

    @Test
    void changingOnePoolReusesTheUnchangedPoolCatalogAndCandidateIndex() {
        ResourceLocation poolA = ResourceLocation.parse("mmcr:test_machine_name");
        ResourceLocation poolB = ResourceLocation.parse("mmcr:controller_tick");
        MachineRecipe firstA = recipe("mmcr:catalog_reuse_recipe_a", poolA.toString(), 20);
        MachineRecipe firstB = recipe("mmcr:catalog_reuse_recipe_b", poolB.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(firstA.id(), firstA, firstB.id(), firstB));
        MachineRecipeCatalog beforeB = RecipeRegistry.catalogForPool(poolB);

        RecipeCandidateIndex.resetBuildCountForTesting();
        MachineRecipe changedA = recipe(firstA.id().toString(), poolA.toString(), 60);
        RecipeRegistry.replaceDynamic(Map.of(changedA.id(), changedA, firstB.id(), firstB));

        assertThat(RecipeCandidateIndex.buildCountForTesting()).isEqualTo(1);
        assertThat(RecipeRegistry.catalogForPool(poolB)).isSameAs(beforeB);
    }

    @Test
    void removingLastRecipePublishesVersionedEmptyMachineCatalog() {
        ResourceLocation machine = ResourceLocation.parse("mmcr:test_machine_name");
        MachineRecipe recipe = recipe("mmcr:catalog_empty_recipe", machine.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        long populatedVersion = RecipeRegistry.catalogForPool(machine).version();

        RecipeRegistry.replaceDynamic(Map.of());

        MachineRecipeCatalog emptyCatalog = RecipeRegistry.catalogForPool(machine);
        assertThat(emptyCatalog.version()).isGreaterThan(populatedVersion);
        assertThat(emptyCatalog.recipes()).isEmpty();
        assertThat(emptyCatalog.orderedRecipes()).isEmpty();
        assertThat(emptyCatalog.inputIndex().allCandidates()).isEmpty();
    }

    @Test
    void clearAllPublishesNewVersionedEmptyCatalogsForKnownMachines() {
        ResourceLocation machine = ResourceLocation.parse("mmcr:test_machine_name");
        MachineRecipe recipe = recipe("mmcr:catalog_clear_recipe", machine.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        long populatedVersion = RecipeRegistry.catalogForPool(machine).version();

        RecipeRegistry.clearAll();

        MachineRecipeCatalog emptyCatalog = RecipeRegistry.catalogForPool(machine);
        assertThat(emptyCatalog.version()).isGreaterThan(populatedVersion);
        assertThat(emptyCatalog.recipes()).isEmpty();
        assertThat(emptyCatalog.orderedRecipes()).isEmpty();
        assertThat(emptyCatalog.inputIndex().allCandidates()).isEmpty();
        assertThat(RecipeRegistry.catalogForPool(ResourceLocation.parse("mmcr:catalog_never_seen")).recipes()).isEmpty();
    }

    @Test
    void dynamicRecipeCannotConflictWithStaticRecipe() {
        var id = ResourceLocation.parse("mmcr:dynamic_static_conflict");
        RecipeRegistry.registerStatic(recipe(id.toString(), "mmcr:test_machine_name"));

        assertThatThrownBy(() -> RecipeRegistry.replaceDynamic(Map.of(id, recipe(id.toString(), "mmcr:test_machine_name"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("static recipe");
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
    }

    @Test
    void dynamicRecipeCannotConflictWithDataPackRecipe() {
        var id = ResourceLocation.parse("mmcr:dynamic_datapack_conflict");
        RecipeRegistry.replaceDataPack(Map.of(id, recipe(id.toString(), "mmcr:test_machine_name")));

        assertThatThrownBy(() -> RecipeRegistry.replaceDynamic(Map.of(id, recipe(id.toString(), "mmcr:test_machine_name"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-pack recipe");
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
    }

    @Test
    void staticAndDataPackSnapshotsAreImmutablePublishedLayers() {
        var id = ResourceLocation.parse("mmcr:immutable_layers");
        var recipe = recipe(id.toString(), "mmcr:test_machine_name");
        RecipeRegistry.registerStatic(recipe);
        RecipeRegistry.replaceDataPack(Map.of(id, recipe("mmcr:immutable_layers", "mmcr:test_machine_name")));

        assertThatThrownBy(() -> RecipeRegistry.dataPackSnapshot().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(RecipeRegistry.staticSnapshot()).containsEntry(id, recipe);
    }

    @Test
    void dataPackOverridePublishesObservableSourceWarning() {
        var id = ResourceLocation.parse("mmcr:warning_recipe");
        RecipeRegistry.registerStatic(recipe(id.toString(), "mmcr:test_machine_name"));

        RecipeRegistry.replaceDataPack(Map.of(id, recipe(id.toString(), "mmcr:test_machine_name")));

        assertThat(RecipeRegistry.lastDataPackWarnings())
                .containsExactly("data-pack layer recipe mmcr:warning_recipe overrides static layer recipe mmcr:warning_recipe");
    }

    @Test
    void dataPackAcceptsRegisteredOutputWithoutExecutionRequirement() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(INVALID_OUTPUT_TYPE);
            ResourceLocation previousId = ResourceLocation.parse("mmcr:valid_output_recipe");
            RecipeRegistry.replaceDataPack(Map.of(previousId, recipe(previousId.toString(), "mmcr:test_machine_name")));
            Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();
            ResourceLocation invalidId = ResourceLocation.parse("mmcr:invalid_output_recipe");
            MachineRecipe invalid = MachineRecipe.fromCanonical(invalidId, ResourceLocation.parse("mmcr:test_machine_name"),
                    20, List.of(), List.of(new InvalidOutput(7, 1F)), List.of(), 0, 1, false, false,
                    false, Set.of());

            RecipeRegistry.replaceDataPack(Map.of(invalidId, invalid));

            assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(invalidId, invalid);
            RecipeRegistry.replaceDataPack(previous);
        }
    }

    @Test
    void recipe_layer_publish_discards_pooled_planning_contexts() {
        ResourceLocation recipeId = ResourceLocation.parse("mmcr:pool_reload_recipe");
        CraftingContextPool pool = CraftingContextPool.global();
        CraftingContext context = pool.borrow(recipeId, new CapabilitySnapshot(List.of()), List.of());
        pool.returnContext(recipeId, context);

        RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe(recipeId.toString(), "mmcr:test_machine_name")));

        CraftingContext replacement = pool.borrow(recipeId, new CapabilitySnapshot(List.of()), List.of());

        assertThat(replacement).isNotSameAs(context);
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return recipe(id, machineId, 1);
    }

    private static MachineRecipe recipe(String id, String machineId, int tickTime) {
        return RecipeTestSupport.create(ResourceLocation.parse(id), ResourceLocation.parse(machineId), tickTime, List.of(), List.of());
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

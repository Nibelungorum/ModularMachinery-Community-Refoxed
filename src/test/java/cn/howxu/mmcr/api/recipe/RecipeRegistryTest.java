package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import com.mojang.serialization.MapCodec;
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
        RecipeRegistry.replaceDataPack(Map.of(oldId, recipe(oldId.toString(), "mmcr:test_machine_name")));

        RecipeRegistry.replaceDataPack(Map.of(newId, recipe(newId.toString(), "mmcr:test_machine_name")));

        assertThat(RecipeRegistry.getRecipe(oldId)).isNull();
        assertThat(RecipeRegistry.dataPackSnapshot()).containsOnlyKeys(newId);
    }

    @Test
    void effectiveByMachineIndexMatchesEffectiveSnapshot() {
        var staticRecipe = recipe("mmcr:static_layered", "mmcr:layered_machine");
        var dataPackRecipe = RecipeTestSupport.create(staticRecipe.id(), staticRecipe.machineId(), 1,
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
    void replacingRecipeContentPublishesNewMachineCatalogVersion() {
        Identifier machineA = Identifier.parse("mmcr:catalog_machine_a");
        MachineRecipe first = recipe("mmcr:catalog_recipe_a", machineA.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(first.id(), first));
        long firstVersion = RecipeRegistry.catalog(machineA).version();

        MachineRecipe changed = recipe(first.id().toString(), machineA.toString(), 40);
        RecipeRegistry.replaceDynamic(Map.of(changed.id(), changed));

        assertThat(RecipeRegistry.catalog(machineA).version()).isNotEqualTo(firstVersion);
        assertThat(RecipeRegistry.catalog(machineA).recipes()).containsExactly(changed);
        assertThat(RecipeRegistry.catalog(machineA).recipes().getFirst().tickTime()).isEqualTo(40);
    }

    @Test
    void changingOneMachineKeepsUnchangedMachineCatalogVersion() {
        Identifier machineA = Identifier.parse("mmcr:catalog_tick_machine_a");
        Identifier machineB = Identifier.parse("mmcr:catalog_tick_machine_b");
        MachineRecipe firstA = recipe("mmcr:catalog_tick_recipe_a", machineA.toString(), 20);
        MachineRecipe firstB = recipe("mmcr:catalog_tick_recipe_b", machineB.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(firstA.id(), firstA, firstB.id(), firstB));
        long machineAVersion = RecipeRegistry.catalog(machineA).version();

        MachineRecipe changedB = recipe(firstB.id().toString(), machineB.toString(), 60);
        RecipeRegistry.replaceDynamic(Map.of(firstA.id(), firstA, changedB.id(), changedB));

        assertThat(RecipeRegistry.catalog(machineA).version()).isEqualTo(machineAVersion);
        assertThat(RecipeRegistry.catalog(machineA).recipes()).containsExactly(firstA);
        assertThat(RecipeRegistry.catalog(machineB).recipes()).containsExactly(changedB);
    }

    @Test
    void removingLastRecipePublishesVersionedEmptyMachineCatalog() {
        Identifier machine = Identifier.parse("mmcr:catalog_empty_machine");
        MachineRecipe recipe = recipe("mmcr:catalog_empty_recipe", machine.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        long populatedVersion = RecipeRegistry.catalog(machine).version();

        RecipeRegistry.replaceDynamic(Map.of());

        MachineRecipeCatalog emptyCatalog = RecipeRegistry.catalog(machine);
        assertThat(emptyCatalog.version()).isGreaterThan(populatedVersion);
        assertThat(emptyCatalog.recipes()).isEmpty();
        assertThat(emptyCatalog.orderedRecipes()).isEmpty();
        assertThat(emptyCatalog.inputIndex().allCandidates()).isEmpty();
    }

    @Test
    void clearAllPublishesNewVersionedEmptyCatalogsForKnownMachines() {
        Identifier machine = Identifier.parse("mmcr:catalog_clear_machine");
        MachineRecipe recipe = recipe("mmcr:catalog_clear_recipe", machine.toString(), 20);
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        long populatedVersion = RecipeRegistry.catalog(machine).version();

        RecipeRegistry.clearAll();

        MachineRecipeCatalog emptyCatalog = RecipeRegistry.catalog(machine);
        assertThat(emptyCatalog.version()).isGreaterThan(populatedVersion);
        assertThat(emptyCatalog.recipes()).isEmpty();
        assertThat(emptyCatalog.orderedRecipes()).isEmpty();
        assertThat(emptyCatalog.inputIndex().allCandidates()).isEmpty();
        assertThat(RecipeRegistry.catalog(Identifier.parse("mmcr:catalog_never_seen")).recipes()).isEmpty();
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

    @Test
    void dataPackAcceptsRegisteredOutputWithoutExecutionRequirement() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(INVALID_OUTPUT_TYPE);
            Identifier previousId = Identifier.parse("mmcr:valid_output_recipe");
            RecipeRegistry.replaceDataPack(Map.of(previousId, recipe(previousId.toString(), "mmcr:test_machine_name")));
            Map<Identifier, MachineRecipe> previous = RecipeRegistry.dataPackSnapshot();
            Identifier invalidId = Identifier.parse("mmcr:invalid_output_recipe");
            MachineRecipe invalid = MachineRecipe.fromCanonical(invalidId, Identifier.parse("mmcr:test_machine_name"),
                    20, List.of(), List.of(new InvalidOutput(7, 1F)), List.of(), 0, 1, false, false,
                    List.of(), false, Set.of());

            RecipeRegistry.replaceDataPack(Map.of(invalidId, invalid));

            assertThat(RecipeRegistry.dataPackSnapshot()).containsEntry(invalidId, invalid);
            RecipeRegistry.replaceDataPack(previous);
        }
    }

    @Test
    void recipe_layer_publish_discards_pooled_planning_contexts() {
        Identifier recipeId = Identifier.parse("mmcr:pool_reload_recipe");
        CraftingContextPool pool = CraftingContextPool.global();
        CraftingContext context = pool.borrow(recipeId, new CapabilitySnapshot(List.of()), List.of());
        pool.returnContext(recipeId, context);

        RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe(recipeId.toString(), "mmcr:pool_reload_machine")));

        CraftingContext replacement = pool.borrow(recipeId, new CapabilitySnapshot(List.of()), List.of());

        assertThat(replacement).isNotSameAs(context);
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return recipe(id, machineId, 1);
    }

    private static MachineRecipe recipe(String id, String machineId, int tickTime) {
        return RecipeTestSupport.create(Identifier.parse(id), Identifier.parse(machineId), tickTime, List.of(), List.of());
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

package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.CustomOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import net.minecraft.core.Direction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicContentReloadServiceTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void restoreStartupDefinitions() {
        TestBootstrap.restoreMachineDefinitions();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void producerFailureRetainsPreviousDynamicSnapshot() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:test_cube"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:controller_tick"));
            throw new IllegalStateException("script failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MachineRegistry.getMachine(ResourceLocation.parse("mmcr:test_cube"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:old_recipe"))).isNotNull();
        assertThat(MachineRegistry.getMachine(ResourceLocation.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void successfulReloadReportsRemovedStructuresAndDropsTheirRecipes() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:test_cube"));
            candidate.registerStructure(structure("mmcr:controller_tick"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        var result = DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(structure("mmcr:controller_tick")));

        assertThat(result.removedStructures()).containsExactly(ResourceLocation.parse("mmcr:test_cube"));
        assertThat(MachineRegistry.getCompiled(ResourceLocation.parse("mmcr:test_cube"))).isNull();
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:old_recipe"))).isNull();
    }

    @Test
    void compilationFailureRetainsPreviousRuntimeSnapshotAndCache() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        var oldStructure = structure("mmcr:test_cube");
        DynamicContentReloadService.reload(candidate -> candidate.registerStructure(oldStructure));
        var oldMachine = MachineRegistry.getMachine(ResourceLocation.parse("mmcr:test_cube"));
        var oldCompiled = MachineRegistry.getCompiled(oldMachine.registryName());
        var oldRotated = BlockArrayCache.get(oldMachine.pattern(), Direction.NORTH);

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(failingStructure("mmcr:controller_tick"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(MachineRegistry.getMachine(oldMachine.registryName())).isNotNull();
        assertThat(MachineRegistry.getCompiled(oldMachine.registryName())).isSameAs(oldCompiled);
        assertThat(BlockArrayCache.get(oldMachine.pattern(), Direction.NORTH))
                .isSameAs(oldRotated);
        assertThat(MachineRegistry.getMachine(ResourceLocation.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void candidateRecipeCanReferenceStaticMachine() {
        var staticMachine = new DynamicMachine(ResourceLocation.parse("mmcr:static"), "mmcr:static", new BlockArray(Map.of()));
        MachineRegistry.register(staticMachine);
        DynamicContentReloadService.reload(candidate ->
                candidate.registerRecipe(recipe("mmcr:static_recipe", "mmcr:static")));
        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:static_recipe"))).isNotNull();
    }

    @Test
    void candidateRecipeCanReferenceStartupMachineDefinitionWithoutDynamicStructure() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:startup_definition_only");
        register(machineId.toString());
        MachineStructureRegistry.replaceStartup(Map.of(machineId, structure(machineId.toString())));

        DynamicContentReloadService.reload(candidate ->
                candidate.registerRecipe(recipe("mmcr:startup_definition_recipe", machineId.toString())));

        assertThat(RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr:startup_definition_recipe"))).isNotNull();
    }

    @Test
    void orphan_dynamic_recipe_is_reported_while_valid_recipe_continues_publishing() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:dynamic_pool_validation_machine");
        ResourceLocation validId = ResourceLocation.parse("mmcr:dynamic_pool_validation_valid");
        ResourceLocation orphanId = ResourceLocation.parse("mmcr:dynamic_pool_validation_orphan");
        register(machineId.toString());

        var result = DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(machineId.toString()));
            candidate.registerRecipe(recipe(validId, machineId));
            candidate.registerRecipe(recipe(orphanId, MMCR.id("missing_dynamic_recipe_pool")));
        });

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(orphanId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains("missing_dynamic_recipe_pool");
        });
        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(validId, recipe(validId, machineId))
                .doesNotContainKey(orphanId);
    }

    @Test
    void invalid_dynamic_recipe_is_reported_while_valid_recipe_continues_publishing() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:dynamic_recipe_validation_machine");
        ResourceLocation validId = ResourceLocation.parse("mmcr:dynamic_recipe_validation_valid");
        ResourceLocation invalidId = ResourceLocation.parse("mmcr:dynamic_recipe_validation_invalid");
        register(machineId.toString());
        MachineRecipe invalid;
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(INVALID_OUTPUT_TYPE);
            invalid = RecipeTestSupport.create(invalidId, machineId, 1, List.of(),
                    List.of(new InvalidOutput(1, 1F)));
        }

        var result = DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(machineId.toString()));
            candidate.registerRecipe(recipe(validId, machineId));
            candidate.registerRecipe(invalid);
        });

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(invalidId);
            assertThat(error.path()).isEqualTo("outputs[0]");
        });
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validId).doesNotContainKey(invalidId);
    }

    @Test
    void dynamic_reload_uses_registry_pool_membership_and_counts_only_published_recipes() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:dynamic_pool_alias_machine");
        ResourceLocation registeredPoolId = ResourceLocation.parse("mmcr:dynamic_pool_alias_registered");
        ResourceLocation validId = ResourceLocation.parse("mmcr:dynamic_pool_alias_valid");
        ResourceLocation invalidId = ResourceLocation.parse("mmcr:dynamic_pool_alias_invalid");
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolId(registeredPoolId).build());

        var result = DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(machineId.toString()));
            candidate.registerRecipe(recipe(validId, registeredPoolId));
            candidate.registerRecipe(recipe(invalidId, machineId));
        });

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(invalidId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains(machineId.toString());
        });
        assertThat(result.addedRecipes()).isEqualTo(1);
        assertThat(result.updatedRecipes()).isZero();
        assertThat(result.removedRecipes()).isZero();
        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(validId, recipe(validId, registeredPoolId))
                .doesNotContainKey(invalidId);
        assertThat(result.addedRecipes()).isEqualTo(RecipeRegistry.dynamicSnapshot().size());
    }

    @Test
    void dynamic_reload_reports_cross_pool_rejection_and_keeps_valid_recipe() {
        ResourceLocation firstMachineId = ResourceLocation.parse("mmcr:dynamic_cross_pool_machine_a");
        ResourceLocation secondMachineId = ResourceLocation.parse("mmcr:dynamic_cross_pool_machine_b");
        ResourceLocation firstPoolId = ResourceLocation.parse("mmcr:dynamic_cross_pool_a");
        ResourceLocation secondPoolId = ResourceLocation.parse("mmcr:dynamic_cross_pool_b");
        ResourceLocation conflictingId = ResourceLocation.parse("mmcr:dynamic_cross_pool_recipe");
        ResourceLocation validId = ResourceLocation.parse("mmcr:dynamic_cross_pool_valid");
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.register(MachineRegistration.builder(firstMachineId)
                .recipePoolId(firstPoolId).build());
        MachineDefinitions.register(MachineRegistration.builder(secondMachineId)
                .recipePoolId(secondPoolId).build());
        MachineRecipe kubeJSRecipe = recipe(conflictingId, firstPoolId);
        RecipeRegistry.replaceKubeJS(Map.of(conflictingId, kubeJSRecipe));

        var result = DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure(secondMachineId.toString()));
            candidate.registerRecipe(recipe(conflictingId, secondPoolId));
            candidate.registerRecipe(recipe(validId, secondPoolId));
        });

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(conflictingId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains(firstPoolId.toString());
        });
        assertThat(result.addedRecipes()).isEqualTo(1);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validId).doesNotContainKey(conflictingId);
        assertThat(result.addedRecipes()).isEqualTo(RecipeRegistry.dynamicSnapshot().size());
        assertThat(RecipeRegistry.getRecipe(conflictingId)).isSameAs(kubeJSRecipe);
    }

    @Test
    void dynamic_reload_uses_registered_pool_for_static_machine_and_rejects_machine_alias() {
        ResourceLocation machineId = ResourceLocation.parse("mmcr:static_pool_alias_machine");
        ResourceLocation registeredPoolId = ResourceLocation.parse("mmcr:static_pool_alias_registered");
        ResourceLocation validId = ResourceLocation.parse("mmcr:static_pool_alias_valid");
        ResourceLocation invalidId = ResourceLocation.parse("mmcr:static_pool_alias_invalid");
        MachineDefinitions.beginRegistryPhase();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolId(registeredPoolId).build());
        MachineRegistry.register(new DynamicMachine(machineId, machineId.toString(), new BlockArray(Map.of())));

        var result = DynamicContentReloadService.reload(candidate -> {
            candidate.registerRecipe(recipe(validId, registeredPoolId));
            candidate.registerRecipe(recipe(invalidId, machineId));
        });

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(invalidId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains(machineId.toString());
        });
        assertThat(result.addedRecipes()).isEqualTo(1);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validId).doesNotContainKey(invalidId);
    }

    @Test
    void reloadWithSnapshotReturnsTheCommittedEffectiveContent() {
        String machineId = "mmcr:test_machine_name";

        var commit = DynamicContentReloadService.reloadWithSnapshot(candidate ->
                candidate.registerStructure(structure(machineId)));

        assertThat(commit.result()).isNotNull();
        assertThat(commit.snapshot().structures()).containsKey(ResourceLocation.parse(machineId));
        assertThat(commit.snapshot().contentVersion()).isGreaterThan(0L);
        assertThat(commit.snapshot().structures()).isEqualTo(MachineStructureRegistry.effectiveSnapshot());
        assertThat(commit.snapshot().recipes()).isEqualTo(RecipeRegistry.effectiveSnapshot());
    }

    private static void register(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        if (MachineDefinitions.getRegistration(identifier) == null) {
            MachineDefinitions.beginRegistryPhase();
            MachineDefinitions.register(MachineRegistration.builder(identifier).build());
        }
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return RecipeTestSupport.create(ResourceLocation.parse(id), ResourceLocation.parse(machineId), 1, List.of(), List.of());
    }

    private static MachineRecipe recipe(ResourceLocation id, ResourceLocation recipePoolId) {
        return RecipeTestSupport.create(id, recipePoolId, 1, List.of(), List.of());
    }

    private static MachineStructureDefinition structure(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        return new MachineStructureDefinition(identifier, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    private static MachineStructureDefinition failingStructure(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        BlockPos outsidePattern = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("invalid", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        return new MachineStructureDefinition(identifier, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.builder().modifier('X', replacement).build());
    }

    private static final ResourceLocation INVALID_OUTPUT_ID = ResourceLocation.parse("mmcr_test:dynamic_invalid_output");
    private static final OutputType<InvalidOutput> INVALID_OUTPUT_TYPE = new OutputType.Definition<>(
            INVALID_OUTPUT_ID, com.mojang.serialization.MapCodec.unit(() -> new InvalidOutput(1, 1F)),
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

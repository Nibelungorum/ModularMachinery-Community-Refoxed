package cn.howxu.mmcr.registration;

import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.registration.RuntimeContentCoordinator;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** Verifies atomic startup content collection and commit behavior.
 * @author howxu <dev@howxu.cn>
 */
class ContentRegistrationCoordinatorTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void reset() {
        ContentRegistrationCoordinator.resetForTesting();
    }

    @AfterEach
    void cleanup() {
        ContentRegistrationCoordinator.resetForTesting();
    }

    @Test
    void commitsMachineStructureAndRecipeAsOneStartupModel() {
        Identifier machineId = id("coordinated_machine");
        MachineDefinition machine = MachineBuilder.machine(machineId).build();
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(machine);
        definitions.freeze();
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        Identifier typeId = id("coordinated_type");
        Identifier levelId = id("coordinated_level");
        Identifier modifierId = id("coordinated_modifier");
        structures.registerLevelType(new LevelType(typeId,
                Component.literal("Coil")));
        structures.registerLevel(new MachineLevel(levelId, typeId, 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()),
                ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        structures.registerModifier(modifierId, new ModifierDefinition(List.of()));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', typeId).modifier('F', modifierId))));
        structures.freeze();
        MachineRecipeRegistration recipes = new MachineRecipeRegistration();
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("coordinated_recipe")).recipePool(machineId)
                .duration(1).build();
        recipes.registerRecipe(recipe);
        recipes.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        ContentRegistrationCoordinator.collectStructures(structures);
        ContentRegistrationCoordinator.collectRecipes(recipes);
        ContentRegistrationCoordinator.commitStartup();

        assertThat(MachineDefinitions.getRegistration(machineId)).isNotNull();
        assertThat(MachineRegistry.getMachine(machineId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNotNull();
        assertThat(MachineLevelRegistry.getType(typeId)).isNotNull();
        assertThat(MachineLevelRegistry.getLevel(levelId)).isNotNull();
        assertThat(ModifierRegistry.get(modifierId)).isNotNull();
    }

    @Test
    void rejectsStructureWithoutMachine() {
        Identifier machineId = id("missing_machine");
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        structures.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectStructures(structures);

        assertThatThrownBy(ContentRegistrationCoordinator::commitStartup)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(machineId.toString());
        assertThat(MachineDefinitions.getRegistration(machineId)).isNull();
        assertThat(MachineRegistry.getMachine(machineId)).isNull();
    }

    @Test
    void dropsOrphanRecipeWithoutBlockingValidStartupRecipe() {
        Identifier machineId = id("missing_recipe_machine");
        Identifier validRecipeId = id("valid_startup_recipe");
        Identifier orphanRecipeId = id("orphan_startup_recipe");
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        structures.freeze();
        MachineRecipeRegistration recipes = new MachineRecipeRegistration();
        MachineRecipeDefinition validRecipe = MachineRecipeBuilder.recipe(validRecipeId).recipePool(machineId)
                .duration(1).build();
        MachineRecipeDefinition orphanRecipe = MachineRecipeBuilder.recipe(orphanRecipeId).recipePool(id("missing_recipe_pool"))
                .duration(1).build();
        recipes.registerRecipe(validRecipe);
        recipes.registerRecipe(orphanRecipe);
        recipes.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        ContentRegistrationCoordinator.collectStructures(structures);
        ContentRegistrationCoordinator.collectRecipes(recipes);
        ContentRegistrationCoordinator.commitStartup();

        assertThat(RecipeRegistry.getRecipe(validRecipeId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(orphanRecipeId)).isNull();
    }

    @Test
    void commitIsIdempotentAfterSuccess() {
        Identifier machineId = id("idempotent_machine");
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        ContentRegistrationCoordinator.commitStartup();
        int machineCount = MachineDefinitions.allRegistrations().size();

        ContentRegistrationCoordinator.commitStartup();

        assertThat(MachineDefinitions.allRegistrations()).hasSize(machineCount);
    }

    @Test
    void repeated_startup_begin_preserves_collected_content() {
        Identifier machineId = id("repeated_begin_machine");
        Identifier recipeId = id("repeated_begin_recipe");
        Identifier modifierId = id("repeated_begin_modifier");
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        structures.registerModifier(modifierId, ModifierDefinition.EMPTY);
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachine(MachineBuilder.machine(machineId).build());
        ContentRegistrationCoordinator.collectStructures(structures);
        ContentRegistrationCoordinator.collectRecipes(recipeEvent(
                MachineRecipeBuilder.recipe(recipeId).recipePool(machineId).duration(1).build()));

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.commitStartup();

        assertThat(MachineDefinitions.getRegistration(machineId)).isNotNull();
        assertThat(MachineStructureRegistry.startupSnapshot()).containsKey(machineId);
        assertThat(ModifierRegistry.get(modifierId)).isNotNull();
        assertThat(RecipeRegistry.getRecipe(recipeId)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void kubejs_machine_survives_production_startup_in_either_load_order(boolean kubejsFirst) {
        Identifier machineId = Identifier.parse("mmcr_kubejs:startup_load_order_machine");
        PublicApiBootstrap.begin();
        if (!kubejsFirst) StartupContentRegistration.registerProductionForModStartup();
        StartupContentRegistration.registerKubeJSStartupMachine(MachineBuilder.machine(machineId).build());
        StartupContentRegistration.completeKubeJSStartupIfReady();
        if (kubejsFirst) StartupContentRegistration.registerProductionForModStartup();
        StartupContentRegistration.completeProductionForModStartup(NeoForge.EVENT_BUS);
        StartupContentRegistration.completeProductionRecipesAfterComponentsBound(NeoForge.EVENT_BUS);

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MachineDefinitions.getRegistration(machineId)).isNotNull();
        assertThat(MachineDefinitions.getRegistration(id("artificial_star"))).isNotNull();
        var declaration = MachineStructureBuilder.structure().fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))).build(machineId);
        var structure = MachineDefinitionConverter.toStructureDefinition(declaration, Map.of());
        assertThatCode(() -> RuntimeContentCoordinator.commitDynamic(Map.of(machineId, structure), Map.of()))
                .doesNotThrowAnyException();
        assertThat(MachineRegistry.getMachine(machineId)).isNotNull();
    }

    @Test
    void commits_complete_startup_structure_snapshot() {
        Identifier machineId = id("complete_startup_machine");
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        structures.registerStructure(machineId, builder -> {
            builder.fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                    .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')));
            return builder.extension(stage -> stage.pattern(pattern -> pattern.layer("FS")
                    .where('F', BlockPredicate.block(Blocks.FURNACE))
                    .where('S', BlockPredicate.block(Blocks.STONE)).controller('F')));
        });
        structures.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        ContentRegistrationCoordinator.collectStructures(structures);
        ContentRegistrationCoordinator.commitStartup();

        assertThat(MachineStructureRegistry.effectiveSnapshot().get(machineId).declarations()).hasSize(2);
        assertThat(MachineRegistry.getCompiledStages(machineId)).hasSize(2);
    }

    @Test
    void invalid_level_snapshot_does_not_install_machine_levels_or_modifiers() {
        Identifier machineId = id("invalid_snapshot_machine");
        MachineDefinitionRegistration definitions = new MachineDefinitionRegistration();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        StructureRegistration structures = new StructureRegistration(List.of(machineId));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        structures.registerLevelType(new LevelType(
                id("invalid_type"), Component.literal("Invalid")));
        structures.registerLevel(new MachineLevel(
                id("invalid_level"), id("invalid_type"), 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()),
                ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        structures.registerLevel(new MachineLevel(
                id("duplicate_priority"), id("invalid_type"), 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.STONE.defaultBlockState()),
                ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        structures.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        assertThatThrownBy(() -> ContentRegistrationCoordinator.collectStructures(structures))
                .isInstanceOf(RuntimeException.class);
        assertThat(MachineDefinitions.getRegistration(machineId)).isNull();
        assertThat(MachineLevelRegistry.getType(id("invalid_type"))).isNull();
        assertThat(ModifierRegistry.definitions()).isEmpty();
    }

    @Test
    void recipe_failure_does_not_install_any_startup_registry_state() {
        Identifier existingMachineId = id("atomic_existing_machine");
        Identifier existingRecipeId = id("atomic_existing_recipe");
        MachineDefinition existingMachine = MachineBuilder.machine(existingMachineId).build();
        MachineDefinitions.register(MachineDefinitionConverter.toStartupRegistration(
                existingMachine, null));
        RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(
                MachineRecipeBuilder.recipe(existingRecipeId).recipePool(existingMachineId).duration(1).build(),
                new StructureRegistration.Snapshot(Map.of(), Map.of(),
                        Map.of(), Map.of())));
        ContentRegistrationCoordinator.beginStartup();

        Identifier newMachineId = id("atomic_new_machine");
        MachineDefinitionRegistration newDefinitions = new MachineDefinitionRegistration();
        newDefinitions.registerMachine(MachineBuilder.machine(newMachineId).build());
        newDefinitions.freeze();
        StructureRegistration structures = new StructureRegistration(List.of(newMachineId));
        structures.registerStructure(newMachineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        structures.freeze();
        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(newDefinitions);
        ContentRegistrationCoordinator.collectStructures(structures);
        ContentRegistrationCoordinator.collectRecipes(recipeEvent(
                MachineRecipeBuilder.recipe(existingRecipeId).recipePool(newMachineId).duration(1).build()));

        assertThatThrownBy(ContentRegistrationCoordinator::commitRecipes)
                .isInstanceOf(RuntimeException.class);
        assertThat(RecipeRegistry.getRecipe(existingRecipeId).recipePoolId()).isEqualTo(existingMachineId);
    }

    private static MachineRecipeRegistration recipeEvent(MachineRecipeDefinition recipe) {
        MachineRecipeRegistration event = new MachineRecipeRegistration();
        event.registerRecipe(recipe);
        event.freeze();
        return event;
    }

    @Test
    void production_bootstrap_commits_without_optional_gametest_classpath() {
        assertThatCode(StartupContentRegistration::registerProduction).doesNotThrowAnyException();
        var productionSnapshot = ContentRegistrationCoordinator.startupSnapshotForTesting();
        int productionCommitCount = ContentRegistrationCoordinator.commitCountForTesting();
        assertThat(productionSnapshot.machines()).isNotEmpty();
        assertThat(productionSnapshot.structures()).isEmpty();
        assertThat(productionSnapshot.recipes()).isEmpty();

        ContentRegistrationCoordinator.resetForTesting();
        TestBootstrap.restoreMachineDefinitions();
        assertThat(productionCommitCount).isEqualTo(1);
        assertThat(ContentRegistrationCoordinator.commitCountForTesting()).isEqualTo(1);
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().machines()).isNotEmpty();
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().structures()).isNotEmpty();
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().recipes()).isNotEmpty();
    }

    @Test
    void definition_subscribers_have_controller_blocks_before_structure_subscribers_run() {
        Identifier machineId = id("subscriber_controller_machine");
        assertThatCode(() -> StartupContentRegistration.registerForTesting(
                definitions -> definitions.registerMachine(machineId, builder -> builder),
                structures -> assertThat(ModBlocks.BLOCKS.containsKey(machineId.getPath() + "_controller")).isTrue(),
                recipes -> { })).doesNotThrowAnyException();
    }

    @Test
    void binds_item_components_before_structure_subscribers_run() throws Exception {
        Holder.Reference<?> holder = Items.DIAMOND_BLOCK.builtInRegistryHolder();
        Field components = Holder.Reference.class.getDeclaredField("components");
        components.setAccessible(true);
        Object previous = components.get(holder);
        components.set(holder, null);
        try {
            assertThatCode(() -> StartupContentRegistration.registerForTesting(
                    definitions -> { },
                    structures -> new ItemStack(Items.DIAMOND_BLOCK),
                    recipes -> { })).doesNotThrowAnyException();
        } finally {
            components.set(holder, previous == null ? DataComponentMap.EMPTY : previous);
        }
    }

    @Test
    void production_startup_seam_commits_before_register_attachment() {
        StartupContentRegistration.registerProduction();

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MMCR.startupPhaseForTesting()).isEqualTo("COMMITTED");
    }

    @Test
    void startup_registration_facade_preserves_testing_lifecycle() {
        StartupContentRegistration.registerForTesting(event -> { }, event -> { }, event -> { });

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(StartupContentRegistration.startupPhaseForTesting()).isEqualTo("COMMITTED");
    }

    @Test
    void public_startup_contracts_keep_phase_order_freeze_and_core_commit() {
        Identifier machine = id("public_startup_machine");
        Identifier recipe = id("public_startup_recipe");
        var phases = new ArrayList<String>();
        var definitions = new AtomicReference<RegisterMachineDefinitionsEvent>();
        var structures = new AtomicReference<RegisterMachineStructuresEvent>();
        StartupContentRegistration.registerPublicForTesting(
                event -> {
                    phases.add("definitions");
                    event.registerMachine(machine, draft -> { });
                    definitions.set(event);
                }, event -> {
                    phases.add("structures");
                    assertThatThrownBy(() -> definitions.get().registerMachine(id("public_late_machine"), draft -> { }))
                            .isInstanceOf(IllegalStateException.class);
                    assertThat(ModBlocks.BLOCKS.containsKey(machine.getPath() + "_controller")).isTrue();
                    event.registerStructure(machine, draft -> draft.fullStructure(stage -> stage.pattern(pattern -> pattern
                            .layer("F").where('F', BlockConditions.block(Blocks.FURNACE)).controller('F'))));
                    structures.set(event);
                }, event -> {
                    phases.add("recipes");
                    assertThatThrownBy(() -> structures.get().registerStructure(machine, draft -> { }))
                            .isInstanceOf(IllegalStateException.class);
                    event.registerRecipe(recipe, draft -> draft.recipePool(machine).duration(1));
                });
        assertThat(phases).containsExactly("definitions", "structures", "recipes");
        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MachineDefinitions.getRegistration(machine)).isNotNull();
        assertThat(MachineStructureRegistry.startupSnapshot()).containsKey(machine);
        assertThat(RecipeRegistry.getRecipe(recipe)).isNotNull();
    }

    @Test
    void coordinator_reset_also_resets_startup_registration_phase() {
        StartupContentRegistration.registerForTesting(event -> { }, event -> { }, event -> { });

        ContentRegistrationCoordinator.resetForTesting();

        assertThat(StartupContentRegistration.startupPhaseForTesting()).isEqualTo("NOT_STARTED");
    }

    @Test
    void public_api_clear_also_resets_startup_registration_phase() {
        StartupContentRegistration.registerForTesting(event -> { }, event -> { }, event -> { });

        PublicApiBootstrap.clearForTesting();

        assertThat(StartupContentRegistration.startupPhaseForTesting()).isEqualTo("NOT_STARTED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void kubejs_completion_after_register_attachment_waits_for_all_production_content(boolean recipesFirst) {
        Identifier machineId = Identifier.parse("mmcr_kubejs:early_completion_machine");
        PublicApiBootstrap.begin();
        StartupContentRegistration.markRegistersAttached();
        StartupContentRegistration.registerKubeJSStartupMachine(MachineBuilder.machine(machineId).build());

        StartupContentRegistration.completeKubeJSStartupIfReady();

        assertThat(ContentRegistrationCoordinator.isCommitted()).isFalse();
        assertThat(MachineDefinitions.isRegistryPhaseOpen()).isTrue();
        StartupContentRegistration.registerProductionForModStartup();
        if (recipesFirst) {
            StartupContentRegistration.completeProductionRecipesAfterComponentsBound();
        } else {
            StartupContentRegistration.completeProductionForModStartup(NeoForge.EVENT_BUS);
        }
        assertThat(ContentRegistrationCoordinator.isCommitted()).isFalse();
        if (recipesFirst) {
            StartupContentRegistration.completeProductionForModStartup(NeoForge.EVENT_BUS);
        } else {
            StartupContentRegistration.completeProductionRecipesAfterComponentsBound();
        }

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MachineDefinitions.getRegistration(machineId)).isNotNull();
        assertThat(MachineDefinitions.getRegistration(id("artificial_star"))).isNotNull();
        assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
        StartupContentRegistration.completeKubeJSStartupIfReady();
        assertThat(MachineDefinitions.getRegistration(id("artificial_star"))).isNotNull();
    }

    @Test
    void kubejs_completion_during_definition_collection_does_not_commit_an_incomplete_snapshot() {
        Identifier machineId = id("definition_completion_window_machine");
        var eventBus = BusBuilder.builder().build();
        eventBus.addListener(RegisterMachineDefinitionsEvent.class, event -> {
            StartupContentRegistration.completeKubeJSStartupIfReady();
            assertThat(ContentRegistrationCoordinator.isCommitted()).isFalse();
            event.registerMachine(machineId, draft -> { });
        });

        StartupContentRegistration.registerProductionForModStartup(eventBus);
        StartupContentRegistration.completeProductionForModStartup(eventBus);
        StartupContentRegistration.completeProductionRecipesAfterComponentsBound(eventBus);

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MachineDefinitions.getRegistration(machineId)).isNotNull();
        assertThat(MachineDefinitions.getRegistration(id("artificial_star"))).isNotNull();
    }

    @Test
    void kubejs_startup_levels_declared_after_structures_event_are_collected_with_production() {
        Identifier typeId = id("kubejs_deferred_type");
        Identifier levelId = id("kubejs_deferred_level");
        StartupContentRegistration.registerProductionForModStartup();
        StructureRegistration.current().registerLevelType(new LevelType(typeId,
                Component.literal("KubeJS Coil")));
        StructureRegistration.current().registerLevel(new MachineLevel(levelId, typeId, 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()),
                ItemStack.EMPTY,
                ModifierDefinition.EMPTY));
        StartupContentRegistration.completeProductionForModStartup(NeoForge.EVENT_BUS);

        assertThat(MachineLevelRegistry.getType(typeId)).isNotNull();
        assertThat(MachineLevelRegistry.getLevel(levelId)).isNotNull();
        assertThat(ContentRegistrationCoordinator.isCommitted()).isFalse();

        StartupContentRegistration.completeProductionRecipesAfterComponentsBound(NeoForge.EVENT_BUS);

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
    }

    @Test
    void production_bootstrap_projects_structures_into_effective_registry() {
        assertThatCode(StartupContentRegistration::registerProduction).doesNotThrowAnyException();
        assertThat(MachineStructureRegistry.startupSnapshot()).isEmpty();
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}

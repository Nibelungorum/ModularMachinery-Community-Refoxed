package cn.howxu.mmcr.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        ResourceLocation machineId = id("coordinated_machine");
        MachineDefinition machine = MachineBuilder.machine(machineId).build();
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(machine);
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(machineId));
        ResourceLocation typeId = id("coordinated_type");
        ResourceLocation levelId = id("coordinated_level");
        ResourceLocation modifierId = id("coordinated_modifier");
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
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
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
        ResourceLocation machineId = id("missing_machine");
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(machineId));
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
        ResourceLocation machineId = id("missing_recipe_machine");
        ResourceLocation validRecipeId = id("valid_startup_recipe");
        ResourceLocation orphanRecipeId = id("orphan_startup_recipe");
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(machineId));
        structures.freeze();
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
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
        ResourceLocation machineId = id("idempotent_machine");
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
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
    void commits_complete_startup_structure_snapshot() {
        ResourceLocation machineId = id("complete_startup_machine");
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(machineId));
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
        ResourceLocation machineId = id("invalid_snapshot_machine");
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(machineId));
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
        ResourceLocation existingMachineId = id("atomic_existing_machine");
        ResourceLocation existingRecipeId = id("atomic_existing_recipe");
        MachineDefinition existingMachine = MachineBuilder.machine(existingMachineId).build();
        MachineDefinitions.register(MachineDefinitionConverter.toStartupRegistration(
                existingMachine, null));
        RecipeRegistry.registerStatic(MachineRecipeConverter.toRecipe(
                MachineRecipeBuilder.recipe(existingRecipeId).recipePool(existingMachineId).duration(1).build(),
                new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(),
                        Map.of(), Map.of())));
        ContentRegistrationCoordinator.beginStartup();

        ResourceLocation newMachineId = id("atomic_new_machine");
        MMCRMachineDefinationsEvent newDefinitions = new MMCRMachineDefinationsEvent();
        newDefinitions.registerMachine(MachineBuilder.machine(newMachineId).build());
        newDefinitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(List.of(newMachineId));
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

    private static MMCRMachineRecipesEvent recipeEvent(MachineRecipeDefinition recipe) {
        MMCRMachineRecipesEvent event = new MMCRMachineRecipesEvent();
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
        ResourceLocation machineId = id("subscriber_controller_machine");
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

    @Test
    void kubejs_completion_after_register_attachment_still_commits_startup() {
        ContentRegistrationCoordinator.beginStartup();
        StartupContentRegistration.markCollectingForTesting();
        StartupContentRegistration.markRegistersAttached();

        StartupContentRegistration.completeKubeJSStartupIfReady();

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
    }

    @Test
    void kubejs_startup_levels_declared_after_structures_event_are_collected_with_production() {
        ResourceLocation typeId = id("kubejs_deferred_type");
        ResourceLocation levelId = id("kubejs_deferred_level");
        StartupContentRegistration.registerProductionForModStartup();
        MMCRMachineStructuresEvent.current().registerLevelType(new LevelType(typeId,
                Component.literal("KubeJS Coil")));
        MMCRMachineStructuresEvent.current().registerLevel(new MachineLevel(levelId, typeId, 1,
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

    private static ResourceLocation id(String path) {
        return MMCR.id(path);
    }
}

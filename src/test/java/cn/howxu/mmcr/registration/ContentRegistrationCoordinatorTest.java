package cn.howxu.mmcr.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(machine);
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(java.util.List.of(machineId));
        Identifier typeId = id("coordinated_type");
        Identifier levelId = id("coordinated_level");
        Identifier modifierId = id("coordinated_modifier");
        structures.registerLevelType(new cn.howxu.mmcr.api.machine.level.LevelType(typeId,
                net.minecraft.network.chat.Component.literal("Coil")));
        structures.registerLevel(new cn.howxu.mmcr.api.machine.level.MachineLevel(levelId, typeId, 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()),
                net.minecraft.world.item.ItemStack.EMPTY,
                cn.howxu.mmcr.api.machine.level.LevelModifier.IDENTITY));
        structures.registerModifier(modifierId, new cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition(java.util.List.of()));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .requirements(requirements -> requirements.levelSlot('F', typeId).modifier('F', modifierId))));
        structures.freeze();
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("coordinated_recipe"), machineId)
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
        assertThat(cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getType(typeId)).isNotNull();
        assertThat(cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getLevel(levelId)).isNotNull();
        assertThat(cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry.get(modifierId)).isNotNull();
    }

    @Test
    void rejectsStructureWithoutMachine() {
        Identifier machineId = id("missing_machine");
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(java.util.List.of(machineId));
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
    void rejectsRecipeWithoutMachine() {
        Identifier machineId = id("missing_recipe_machine");
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("orphan_recipe"), machineId)
                .duration(1).build();
        recipes.registerRecipe(recipe);
        recipes.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectRecipes(recipes);

        assertThatThrownBy(ContentRegistrationCoordinator::commitStartup)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(machineId.toString());
        assertThat(RecipeRegistry.getRecipe(recipe.id())).isNull();
    }

    @Test
    void commitIsIdempotentAfterSuccess() {
        Identifier machineId = id("idempotent_machine");
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
        Identifier machineId = id("complete_startup_machine");
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(java.util.List.of(machineId));
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
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(MachineBuilder.machine(machineId).build());
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(java.util.List.of(machineId));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        structures.registerLevelType(new cn.howxu.mmcr.api.machine.level.LevelType(
                id("invalid_type"), net.minecraft.network.chat.Component.literal("Invalid")));
        structures.registerLevel(new cn.howxu.mmcr.api.machine.level.MachineLevel(
                id("invalid_level"), id("invalid_type"), 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()),
                net.minecraft.world.item.ItemStack.EMPTY,
                cn.howxu.mmcr.api.machine.level.LevelModifier.IDENTITY));
        structures.registerLevel(new cn.howxu.mmcr.api.machine.level.MachineLevel(
                id("duplicate_priority"), id("invalid_type"), 1,
                new cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState(Blocks.STONE.defaultBlockState()),
                net.minecraft.world.item.ItemStack.EMPTY,
                cn.howxu.mmcr.api.machine.level.LevelModifier.IDENTITY));
        structures.freeze();

        ContentRegistrationCoordinator.beginStartup();
        ContentRegistrationCoordinator.collectMachines(definitions);
        ContentRegistrationCoordinator.collectStructures(structures);
        assertThatThrownBy(ContentRegistrationCoordinator::commitStartup).isInstanceOf(RuntimeException.class);
        assertThat(MachineDefinitions.getRegistration(machineId)).isNull();
        assertThat(cn.howxu.mmcr.api.machine.level.MachineLevelRegistry.getType(id("invalid_type"))).isNull();
        assertThat(cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry.definitions()).isEmpty();
    }

    @Test
    void production_bootstrap_commits_without_optional_gametest_classpath() {
        assertThatCode(MMCR::registerProductionApiLifecycleForTesting).doesNotThrowAnyException();
        var productionSnapshot = ContentRegistrationCoordinator.startupSnapshotForTesting();
        int productionCommitCount = ContentRegistrationCoordinator.commitCountForTesting();
        assertThat(productionSnapshot.machines()).isNotEmpty();
        assertThat(productionSnapshot.structures()).isNotEmpty();
        assertThat(productionSnapshot.recipes()).isNotEmpty();

        ContentRegistrationCoordinator.resetForTesting();
        TestBootstrap.restoreMachineDefinitions();
        assertThat(productionCommitCount).isEqualTo(1);
        assertThat(ContentRegistrationCoordinator.commitCountForTesting()).isEqualTo(1);
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().machines()).isNotEmpty();
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().structures()).isNotEmpty();
        assertThat(ContentRegistrationCoordinator.startupSnapshotForTesting().recipes()).isNotEmpty();
    }

    @Test
    void production_startup_seam_commits_before_register_attachment() {
        MMCR.registerProductionApiLifecycleForTesting();

        assertThat(ContentRegistrationCoordinator.isCommitted()).isTrue();
        assertThat(MMCR.startupPhaseForTesting()).isEqualTo("COMMITTED");
    }

    @Test
    void production_bootstrap_projects_structures_into_effective_registry() {
        assertThatCode(MMCR::registerProductionApiLifecycleForTesting).doesNotThrowAnyException();
        assertThat(MachineStructureRegistry.startupSnapshot()).isNotEmpty()
                .allSatisfy((machineId, structure) -> {
                    assertThat(MachineStructureRegistry.effectiveSnapshot()).containsKey(machineId);
                    assertThat(MachineRegistry.getMachine(machineId)).isNotNull();
                    assertThat(MachineRegistry.getCompiledStages(machineId)).isNotEmpty();
                });
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}

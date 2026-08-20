package cn.howxu.mmcr.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
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
        ContentRegistrationCoordinator.clearForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() {
        ContentRegistrationCoordinator.clearForTesting();
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void commitsMachineStructureAndRecipeAsOneStartupModel() {
        Identifier machineId = id("coordinated_machine");
        MachineDefinition machine = MachineBuilder.machine(machineId).build();
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        definitions.registerMachine(machine);
        definitions.freeze();
        MMCRMachineStructuresEvent structures = new MMCRMachineStructuresEvent(java.util.List.of(machineId));
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
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

    private static Identifier id(String path) {
        return MMCR.id(path);
    }
}

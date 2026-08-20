package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies public lifecycle event behavior with real declarations.
 * @author howxu <dev@howxu.cn>
 */
class PublicEventSubscribersTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void events_register_real_definition_structure_and_recipe_ids() {
        var machineId = MMCR.id("event_machine");
        var recipeId = MMCR.id("event_recipe");
        var definitions = new RegisterMachineDefinationsEvent();
        definitions.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.event_machine"));

        var structures = new RegisterMachineStructuresEvent(definitions.definitions().keySet());
        structures.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));

        var recipes = new MMCRRegisterRecipesEvent();
        recipes.registerRecipe(MachineRecipeBuilder.recipe(recipeId, machineId).duration(1).build());

        assertThat(definitions.definitions()).containsOnlyKeys(machineId);
        assertThat(structures.structures()).containsOnlyKeys(machineId);
        assertThat(recipes.recipes()).containsOnlyKeys(recipeId);
    }

    @Test
    void structure_registration_rejects_unknown_machine_duplicate_null_and_missing_main() {
        var machineId = MMCR.id("known_machine");
        var event = new RegisterMachineStructuresEvent(Set.of(machineId));
        assertThatThrownBy(() -> event.registerStructure(MMCR.id("unknown"), builder -> builder))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event.registerStructure(machineId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalArgumentException.class);
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void all_events_reject_null_duplicate_and_writes_after_freeze() {
        var machineId = MMCR.id("frozen_machine");
        var definitions = new RegisterMachineDefinationsEvent();
        assertThatThrownBy(() -> definitions.registerMachine(machineId, null)).isInstanceOf(NullPointerException.class);
        definitions.registerMachine(machineId, builder -> builder);
        assertThatThrownBy(() -> definitions.registerMachine(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        definitions.freeze();
        assertThatThrownBy(() -> definitions.registerMachine(MMCR.id("later"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var structures = new RegisterMachineStructuresEvent(Set.of(machineId));
        structures.freeze();
        assertThatThrownBy(() -> structures.registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var recipes = new MMCRRegisterRecipesEvent();
        var recipe = MachineRecipeBuilder.recipe(MMCR.id("frozen_recipe"), machineId).build();
        recipes.registerRecipe(recipe);
        assertThatThrownBy(() -> recipes.registerRecipe(recipe)).isInstanceOf(IllegalStateException.class);
        recipes.freeze();
        assertThatThrownBy(() -> recipes.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("later_recipe"), machineId).build()))
                .isInstanceOf(IllegalStateException.class);
    }
}

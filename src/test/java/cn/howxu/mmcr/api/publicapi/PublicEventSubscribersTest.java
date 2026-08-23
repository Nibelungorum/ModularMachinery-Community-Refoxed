package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.Event;
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
    void public_registration_events_are_game_bus_events() {
        assertThat(new MMCRMachineDefinationsEvent()).isInstanceOf(Event.class);
        assertThat(new MMCRMachineStructuresEvent(Set.of())).isInstanceOf(Event.class);
        assertThat(new MMCRMachineRecipesEvent()).isInstanceOf(Event.class);
    }

    @Test
    void structure_registration_rejects_unknown_machine_duplicate_null_and_missing_main() {
        var machineId = MMCR.id("known_machine");
        var event = new MMCRMachineStructuresEvent(Set.of(machineId));
        assertThatThrownBy(() -> event.registerStructure(MMCR.id("unknown"), builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
        assertThatThrownBy(() -> event.registerStructure(machineId, null))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> {
            throw new IllegalArgumentException("invalid declaration");
        }))
                .isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        ApiRegistrationException declared = new ApiRegistrationException("declared failure");
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> {
            throw declared;
        })).isInstanceOf(ApiRegistrationException.class)
                .hasMessageContaining(machineId.toString());
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
        event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
        assertThatThrownBy(() -> event.registerStructure(machineId, builder -> builder))
                .isInstanceOf(ApiRegistrationException.class);
    }

    @Test
    void all_events_reject_null_duplicate_and_writes_after_freeze() {
        var machineId = MMCR.id("frozen_machine");
        var definitions = new MMCRMachineDefinationsEvent();
        assertThatThrownBy(() -> definitions.registerMachine(machineId, null)).isInstanceOf(NullPointerException.class);
        definitions.registerMachine(machineId, builder -> builder);
        assertThatThrownBy(() -> definitions.registerMachine(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);
        definitions.freeze();
        assertThatThrownBy(() -> definitions.registerMachine(MMCR.id("later"), builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var structures = new MMCRMachineStructuresEvent(Set.of(machineId));
        structures.freeze();
        assertThatThrownBy(() -> structures.registerStructure(machineId, builder -> builder))
                .isInstanceOf(IllegalStateException.class);

        var recipes = new MMCRMachineRecipesEvent();
        var recipe = MachineRecipeBuilder.recipe(MMCR.id("frozen_recipe"), machineId).build();
        recipes.registerRecipe(recipe);
        assertThatThrownBy(() -> recipes.registerRecipe(recipe)).isInstanceOf(IllegalStateException.class);
        recipes.freeze();
        assertThatThrownBy(() -> recipes.registerRecipe(MachineRecipeBuilder.recipe(MMCR.id("later_recipe"), machineId).build()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provider_can_implement_only_the_canonical_definition_signature() {
        var event = new MMCRMachineDefinationsEvent();
        MachineDefinitionProvider provider = new MachineDefinitionProvider() {
            @Override
            public void register(MMCRMachineDefinationsEvent event) {
                event.registerMachine(MMCR.id("canonical_provider_machine"), builder -> builder);
            }
        };

        provider.register(event);

        assertThat(event.definitions()).containsKey(MMCR.id("canonical_provider_machine"));
    }

}

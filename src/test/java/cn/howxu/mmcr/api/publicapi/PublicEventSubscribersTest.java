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
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        var definitionReceives = new AtomicInteger();
        var structureReceives = new AtomicInteger();
        var recipeReceives = new AtomicInteger();
        var definitions = new AtomicReference<RegisterMachineDefinationsEvent>();
        var structures = new AtomicReference<RegisterMachineStructuresEvent>();
        var recipes = new AtomicReference<MMCRRegisterRecipesEvent>();
        NeoForge.EVENT_BUS.addListener(RegisterMachineDefinationsEvent.class, event -> {
            definitionReceives.incrementAndGet();
            event.registerMachine(machineId, builder -> builder.displayNameKey("machine.mmcr.event_machine"));
            definitions.set(event);
        });
        NeoForge.EVENT_BUS.addListener(RegisterMachineStructuresEvent.class, event -> {
            structureReceives.incrementAndGet();
            event.registerStructure(machineId, builder -> builder.fullStructure(stage -> stage.pattern(pattern -> pattern
                    .layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))));
            structures.set(event);
        });
        NeoForge.EVENT_BUS.addListener(MMCRRegisterRecipesEvent.class, event -> {
            recipeReceives.incrementAndGet();
            event.registerRecipe(MachineRecipeBuilder.recipe(recipeId, machineId).duration(1).build());
            recipes.set(event);
        });

        NeoForge.EVENT_BUS.post(new RegisterMachineDefinationsEvent());
        NeoForge.EVENT_BUS.post(new RegisterMachineStructuresEvent(Set.of(machineId)));
        NeoForge.EVENT_BUS.post(new MMCRRegisterRecipesEvent());

        assertThat(definitionReceives).hasValue(1);
        assertThat(structureReceives).hasValue(1);
        assertThat(recipeReceives).hasValue(1);
        assertThat(definitions.get().definitions()).containsOnlyKeys(machineId);
        assertThat(structures.get().structures()).containsOnlyKeys(machineId);
        assertThat(recipes.get().recipes()).containsOnlyKeys(recipeId);
    }

    @Test
    void structure_registration_rejects_unknown_machine_duplicate_null_and_missing_main() {
        var machineId = MMCR.id("known_machine");
        var event = new RegisterMachineStructuresEvent(Set.of(machineId));
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
        })).isSameAs(declared);
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

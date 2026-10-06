package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterJeiRecipeInformationEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises public declarations and their real game-bus registration windows.
 * @author howxu <dev@howxu.cn>
 */
class PublicEventSubscribersTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void definition_event_is_delivered_on_game_bus_and_snapshot_is_read_only() {
        var id = MMCR.id("game_bus_public_machine");
        var event = new RegisterMachineDefinitionsEvent();
        var calls = new AtomicInteger();
        java.util.function.Consumer<RegisterMachineDefinitionsEvent> subscriber = new java.util.function.Consumer<>() {
            @Override public void accept(RegisterMachineDefinitionsEvent posted) {
                if (posted != event) return;
                posted.registerMachine(id, draft -> draft.displayNameKey("machine.mmcr.public_test"));
                calls.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(subscriber);
        try { NeoForge.EVENT_BUS.post(event); }
        finally { NeoForge.EVENT_BUS.unregister(subscriber); }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(event.definitions()).containsKey(id);
        assertThatThrownBy(() -> event.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(event).isInstanceOf(Event.class);
        assertThat(new RegisterMachineStructuresEvent(Set.of())).isInstanceOf(Event.class);
        assertThat(new RegisterMachineRecipesEvent()).isInstanceOf(Event.class);
    }

    @Test
    void game_bus_registration_events_can_register_and_dispatch_on_the_game_bus() {
        var subscriber = new LifecycleSubscriber();
        var definitions = new RegisterMachineDefinitionsEvent();
        var structures = new RegisterMachineStructuresEvent(Set.of());
        var recipes = new RegisterMachineRecipesEvent();
        var information = new RegisterJeiRecipeInformationEvent();
        var workstations = new RegisterJeiWorkstationsEvent();
        NeoForge.EVENT_BUS.register(subscriber);
        try {
            NeoForge.EVENT_BUS.post(definitions);
            NeoForge.EVENT_BUS.post(structures);
            NeoForge.EVENT_BUS.post(recipes);
            NeoForge.EVENT_BUS.post(information);
            NeoForge.EVENT_BUS.post(workstations);
        } finally {
            NeoForge.EVENT_BUS.unregister(subscriber);
        }
        assertThat(subscriber.events).containsExactly(definitions, structures, recipes,
                information, workstations);
    }

    /** Listener fixture exercising annotated subscribers on the platform game bus.
     * @author howxu <dev@howxu.cn>
     */
    public static final class LifecycleSubscriber {
        private final List<Event> events = new ArrayList<>();
        @SubscribeEvent
        public void definitions(RegisterMachineDefinitionsEvent event) { events.add(event); }
        @SubscribeEvent
        public void structures(RegisterMachineStructuresEvent event) { events.add(event); }
        @SubscribeEvent
        public void recipes(RegisterMachineRecipesEvent event) { events.add(event); }
        @SubscribeEvent
        public void information(RegisterJeiRecipeInformationEvent event) { events.add(event); }
        @SubscribeEvent
        public void workstations(RegisterJeiWorkstationsEvent event) { events.add(event); }
    }

    @Test
    void structure_callback_failure_keeps_id_message_cause_and_does_not_register() {
        var id = MMCR.id("public_invalid_structure");
        var event = new RegisterMachineStructuresEvent(Set.of(id));
        var cause = new IllegalArgumentException("invalid declaration");
        assertThatThrownBy(() -> event.registerStructure(id, draft -> { throw cause; }))
                .isInstanceOf(RegistrationException.class).hasRootCause(cause)
                .hasMessageContaining(id.toString()).hasMessageContaining(cause.getMessage());
        assertThat(event.structures()).isEmpty();
        event.registerStructure(id, draft -> draft.fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("F").where('F', BlockConditions.block(Blocks.FURNACE)).controller('F'))));
        assertThat(event.structures()).containsKey(id);
    }

    @Test
    void rejected_structures_do_not_run_configuration() {
        var id = MMCR.id("public_known_structure");
        var event = new RegisterMachineStructuresEvent(Set.of(id));
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> event.registerStructure(MMCR.id("unknown"), draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("unknown");
        RegistrationAdapters.freeze(event);
        assertThatThrownBy(() -> event.registerStructure(id, draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThat(calls.get()).isZero();
    }

    @Test
    void definitions_and_recipes_reject_duplicates_and_frozen_callbacks() {
        var id = MMCR.id("public_frozen_machine");
        var definitions = new RegisterMachineDefinitionsEvent();
        definitions.registerMachine(Machines.machine(id).build());
        assertThatThrownBy(() -> definitions.registerMachine(id, draft -> { }))
                .isInstanceOf(RegistrationException.class).hasMessageContaining(id.toString());
        RegistrationAdapters.freeze(definitions);
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> definitions.registerMachine(MMCR.id("later"), draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class);
        var recipeId = MMCR.id("public_frozen_recipe");
        var recipes = new RegisterMachineRecipesEvent();
        recipes.registerRecipe(Recipes.recipe(recipeId).recipePool(id).build());
        assertThatThrownBy(() -> recipes.registerRecipe(recipeId, draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class).hasMessageContaining(recipeId.toString());
        RegistrationAdapters.freeze(recipes);
        assertThatThrownBy(() -> recipes.registerRecipe(MMCR.id("later_recipe"), draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class);
        assertThat(calls.get()).isZero();
        assertThatThrownBy(() -> recipes.recipes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void provider_implements_new_public_signature() {
        var event = new RegisterMachineDefinitionsEvent();
        MachineDefinitionProvider provider = definitions -> definitions.registerMachine(
                MMCR.id("canonical_provider_machine"), draft -> { });
        provider.register(event);
        assertThat(event.definitions()).containsKey(MMCR.id("canonical_provider_machine"));
    }

    @Test
    void core_recipe_freeze_closes_the_same_public_callback_window() {
        var event = new RegisterMachineRecipesEvent();
        RegistrationAdapters.core(event).freeze();
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> event.registerRecipe(MMCR.id("core_frozen_public_recipe"), draft -> calls.incrementAndGet()))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThat(calls.get()).isZero();
        assertThat(event.recipes()).isEmpty();
    }
}

package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.IModBusEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises public declarations on the 1.21.1 mod-bus registration boundary.
 * @author howxu <dev@howxu.cn>
 */
class PublicEventSubscribersTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void definition_event_is_delivered_on_mod_bus_and_snapshot_is_read_only() {
        var id = MMCR.id("mod_bus_public_machine");
        var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
        var event = new RegisterMachineDefinitionsEvent();
        var calls = new AtomicInteger();
        java.util.function.Consumer<RegisterMachineDefinitionsEvent> subscriber = new java.util.function.Consumer<>() {
            @Override public void accept(RegisterMachineDefinitionsEvent posted) {
                if (posted != event) return;
                posted.registerMachine(id, draft -> draft.displayNameKey("machine.mmcr.public_test"));
                calls.incrementAndGet();
            }
        };
        bus.addListener(subscriber);
        try { bus.post(event); }
        finally { bus.unregister(subscriber); }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(event.definitions()).containsKey(id);
        assertThatThrownBy(() -> event.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(event).isInstanceOf(Event.class);
        assertThat(new RegisterMachineStructuresEvent(Set.of())).isInstanceOf(Event.class);
        assertThat(new RegisterMachineRecipesEvent()).isInstanceOf(Event.class);
    }

    @Test
    void all_startup_events_can_register_and_dispatch_on_the_mod_bus() {
        var bus = BusBuilder.builder().markerType(IModBusEvent.class).build();
        var subscriber = new LifecycleSubscriber();
        bus.register(subscriber);
        bus.post(new RegisterMachineDefinitionsEvent());
        bus.post(new RegisterMachineStructuresEvent(Set.of()));
        bus.post(new RegisterMachineRecipesEvent());
        assertThat(subscriber.calls.get()).isEqualTo(3);
    }

    /** Listener fixture exercising the marker-restricted platform bus.
     * @author howxu <dev@howxu.cn>
     */
    public static final class LifecycleSubscriber {
        private final AtomicInteger calls = new AtomicInteger();
        @SubscribeEvent
        public void definitions(RegisterMachineDefinitionsEvent event) { calls.incrementAndGet(); }
        @SubscribeEvent
        public void structures(RegisterMachineStructuresEvent event) { calls.incrementAndGet(); }
        @SubscribeEvent
        public void recipes(RegisterMachineRecipesEvent event) { calls.incrementAndGet(); }
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

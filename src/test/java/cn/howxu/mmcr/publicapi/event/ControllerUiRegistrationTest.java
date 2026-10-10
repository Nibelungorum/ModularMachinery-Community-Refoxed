package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.client.UiClientAdapters;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiFactory;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public registration delegates to the frozen authoritative client collector.
 * @author howxu <dev@howxu.cn> */
class ControllerUiRegistrationTest {
    private static final ResourceLocation MACHINE = ResourceLocation.parse("test:machine");
    private static final ResourceLocation OTHER = ResourceLocation.parse("test:other");

    @Test
    void accepts_known_ids_without_executing_factory_and_copies_known_machine_ids() {
        var ids = new ArrayList<>(List.of(MACHINE));
        var event = new RegisterControllerUisEvent(ids);
        ids.add(OTHER);
        var calls = new AtomicInteger();
        event.register(MACHINE, context -> { calls.incrementAndGet(); return null; });
        assertThat(UiClientAdapters.core(event).find(MACHINE)).isPresent();
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> event.register(OTHER, context -> null))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Unknown machine");
        assertThat(UiClientAdapters.core(event).find(OTHER)).isEmpty();
    }

    @Test
    void duplicate_fails_registration_and_retains_first_factory() {
        var event = new RegisterControllerUisEvent(List.of(MACHINE));
        ControllerUiFactory first = context -> null;
        event.register(MACHINE, first);
        var retained = UiClientAdapters.core(event).find(MACHINE).orElseThrow();
        assertThatThrownBy(() -> event.register(MACHINE, context -> null))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Duplicate");
        assertThat(UiClientAdapters.core(event).find(MACHINE)).containsSame(retained);
        assertThat(retained.toString()).isEqualTo(first.getClass().getName());
    }

    @Test
    void retained_event_and_registrar_reject_late_registration_after_idempotent_freeze() {
        var event = new RegisterControllerUisEvent(List.of(MACHINE, OTHER));
        var registrar = event.registrar();
        event.register(MACHINE, context -> null);
        var retained = UiClientAdapters.core(event).find(MACHINE).orElseThrow();
        UiClientAdapters.freeze(event);
        UiClientAdapters.freeze(event);
        assertThatThrownBy(() -> event.register(OTHER, context -> null))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> registrar.register(MACHINE, context -> null))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThat(UiClientAdapters.core(event).find(MACHINE)).containsSame(retained);
    }

}

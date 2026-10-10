package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.event.IModBusEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public factories and registration share typed core values without public handles.
 * @author howxu <dev@howxu.cn>
 */
class UiProtocolAdapterTest {
    private static final Identifier MACHINE = Identifier.parse("test:machine");
    private static final Identifier REQUEST = Identifier.parse("test:request");
    private static final Identifier STATE = Identifier.parse("test:state");
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), RegistryFriendlyByteBuf::readVarInt);

    @Test
    void descriptor_factories_preserve_typed_codecs_and_core_identity() {
        UiRequestType<Integer, Integer> request = UiRequestType.of(REQUEST, 3, CODEC, CODEC);
        UiStateType<Integer> state = UiStateType.of(STATE, 2, CODEC);
        var nativeRequest = UiProtocolAdapters.unwrap(request);
        var nativeState = UiProtocolAdapters.unwrap(state);
        assertThat(request.id()).isEqualTo(REQUEST);
        assertThat(request.version()).isEqualTo(3);
        assertThat(state.id()).isEqualTo(STATE);
        assertThat(state.version()).isEqualTo(2);
        assertThat(nativeRequest.requestCodec()).isSameAs(CODEC);
        assertThat(nativeRequest.responseCodec()).isSameAs(CODEC);
        assertThat(nativeState.codec()).isSameAs(CODEC);
        assertThat(UiProtocolAdapters.unwrap(UiProtocolAdapters.wrap(nativeRequest))).isSameAs(nativeRequest);
        assertThat(UiProtocolAdapters.unwrap(UiProtocolAdapters.wrap(nativeState))).isSameAs(nativeState);
    }

    @Test
    void successful_results_retain_the_typed_value_and_rejections_own_their_reason() {
        UiResult<Integer> success = UiResult.success(17);
        assertThat(success.status()).isEqualTo(UiResult.Status.SUCCESS);
        assertThat(success.value()).contains(17);
        assertThat(success.message()).isEmpty();
        var nativeSuccess = UiProtocolAdapters.unwrap(success);
        assertThat(nativeSuccess.value()).contains(17);
        assertThat(UiProtocolAdapters.unwrap(UiProtocolAdapters.wrap(nativeSuccess))).isSameAs(nativeSuccess);

        MutableComponent reason = Component.translatable("gui.mmcr.ui.rejected");
        Component expected = reason.copy();
        UiResult<Integer> rejected = UiResult.reject(reason);
        assertThat(rejected.status()).isEqualTo(UiResult.Status.REJECTED);
        assertThat(rejected.value()).isEmpty();
        assertThat(rejected.message()).contains(reason);
        assertThat(rejected.message().orElseThrow()).isNotSameAs(reason);
        reason.append(Component.translatable("test.changed.input"));
        ((MutableComponent) rejected.message().orElseThrow()).append(Component.translatable("test.changed.output"));
        assertThat(rejected.message()).contains(expected);
        assertThat(UiProtocolAdapters.unwrap(rejected).message()).contains(expected);
    }

    @Test
    void rejection_reason_owns_nested_input_components_and_isolates_each_reader() {
        MutableComponent sibling = Component.translatable("test.sibling");
        MutableComponent argument = Component.translatable("test.argument");
        MutableComponent hover = Component.translatable("test.hover");
        MutableComponent reason = Component.translatable("test.rejected", argument).append(sibling)
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
        Component expected = Component.translatable("test.rejected", Component.translatable("test.argument"))
                .append(Component.translatable("test.sibling"))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.translatable("test.hover"))));
        UiResult<Integer> rejected = UiResult.reject(reason);
        sibling.append(" input mutation");
        argument.append(" input mutation");
        hover.append(" input mutation");
        assertThat(rejected.message()).contains(expected);

        Component first = rejected.message().orElseThrow();
        Component second = rejected.message().orElseThrow();
        ((MutableComponent) first.getSiblings().getFirst()).append(" reader mutation");
        ((MutableComponent) ((TranslatableContents) first.getContents()).getArgs()[0]).append(" reader mutation");
        ((MutableComponent) ((HoverEvent.ShowText) first.getStyle().getHoverEvent()).value()).append(" reader mutation");
        assertThat(second).isEqualTo(expected);
        assertThat(rejected.message()).contains(expected);
        assertThat(UiProtocolAdapters.unwrap(rejected).message()).contains(expected);
    }

    @Test
    void all_transport_statuses_cross_the_facade_without_a_success_value() {
        for (var status : UiProtocolRegistration.Status.values()) {
            if (status == UiProtocolRegistration.Status.SUCCESS) continue;
            var result = new UiProtocolRegistration.Result<Integer>(status, Optional.empty(), Optional.empty());
            var view = UiProtocolAdapters.wrap(result);
            assertThat(view.status()).isEqualTo(UiResult.Status.valueOf(status.name()));
            assertThat(view.value()).isEmpty();
            assertThat(view.message()).isEmpty();
            assertThat(UiProtocolAdapters.unwrap(view)).isSameAs(result);
        }
    }

    @Test
    void factories_reject_invalid_descriptors_and_absent_values() {
        assertThatThrownBy(() -> UiRequestType.of(REQUEST, 0, CODEC, CODEC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UiStateType.of(STATE, -1, CODEC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UiRequestType.of(null, 1, CODEC, CODEC))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UiStateType.of(STATE, 1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UiResult.success(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UiResult.reject(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void event_registrar_freezes_the_authoritative_core_and_defers_callbacks_until_dispatch() {
        var event = new RegisterControllerUiProtocolsEvent(List.of(MACHINE));
        assertThat(event).isInstanceOf(IModBusEvent.class);
        var core = UiProtocolAdapters.core(event);
        var request = UiRequestType.of(REQUEST, 1, CODEC, CODEC);
        var state = UiStateType.of(STATE, 1, CODEC);
        AtomicInteger callbacks = new AtomicInteger();
        event.registrar().request(MACHINE, request, (context, value) -> {
            callbacks.incrementAndGet();
            return UiResult.success(value + 1);
        });
        event.registrar().state(MACHINE, state, new UiStateProvider<Integer>() {
            @Override public long revision(UiServerContext context) { callbacks.incrementAndGet(); return 1L; }
            @Override public Integer snapshot(UiServerContext context) { callbacks.incrementAndGet(); return 2; }
        });
        assertThat(callbacks).hasValue(0);
        assertThat(core.request(MACHINE, REQUEST).orElseThrow().type()).isSameAs(UiProtocolAdapters.unwrap(request));
        assertThat(core.state(MACHINE, STATE).orElseThrow().type()).isSameAs(UiProtocolAdapters.unwrap(state));
        assertThat(UiProtocolAdapters.freeze(event)).isSameAs(core);
        assertThat(UiProtocolAdapters.freeze(event)).isSameAs(core);
        assertThat(core.capabilities(MACHINE)).containsExactly(
                new UiProtocolRegistration.Capability(REQUEST, 1, false),
                new UiProtocolRegistration.Capability(STATE, 1, true));
        assertThatThrownBy(() -> event.registrar().request(MACHINE, request,
                (context, value) -> UiResult.success(value)))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> event.registrar().state(MACHINE, state, new UiStateProvider<Integer>() {
            @Override public long revision(UiServerContext context) { return 1L; }
            @Override public Integer snapshot(UiServerContext context) { return 2; }
        })).isInstanceOf(RegistrationException.class).hasMessageContaining("frozen");
        assertThat(callbacks).hasValue(0);
    }

    @Test
    void public_registration_rejects_duplicates_and_unknown_machines_without_overwriting_types() {
        var event = new RegisterControllerUiProtocolsEvent(List.of(MACHINE));
        var request = UiRequestType.of(REQUEST, 1, CODEC, CODEC);
        event.registrar().request(MACHINE, request, (context, value) -> UiResult.success(value));
        var entry = UiProtocolAdapters.core(event).request(MACHINE, REQUEST).orElseThrow();
        assertThatThrownBy(() -> event.registrar().request(MACHINE, request,
                (context, value) -> UiResult.success(-1)))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> event.registrar().request(Identifier.parse("test:unknown"), request,
                (context, value) -> UiResult.success(value)))
                .isInstanceOf(RegistrationException.class).hasMessageContaining("Unknown machine");
        assertThat(UiProtocolAdapters.core(event).request(MACHINE, REQUEST)).containsSame(entry);
    }
}

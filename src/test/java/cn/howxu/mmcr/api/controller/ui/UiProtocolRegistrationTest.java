package cn.howxu.mmcr.api.controller.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Machine-local lookup, declaration consistency and registration lifetime contracts.
 * @author howxu <dev@howxu.cn>
 */
class UiProtocolRegistrationTest {
    private static final ResourceLocation FIRST = ResourceLocation.parse("test:first");
    private static final ResourceLocation SECOND = ResourceLocation.parse("test:second");
    private static final ResourceLocation REQUEST = ResourceLocation.parse("test:request");
    private static final ResourceLocation STATE = ResourceLocation.parse("test:state");
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> CODEC = integerCodec();

    @Test
    void lookup_keeps_machine_specific_typed_handlers_and_providers() {
        var registry = new UiProtocolRegistration(List.of(FIRST, SECOND));
        var request = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        var state = new UiProtocolRegistration.StateType<>(STATE, 2, CODEC);
        var calls = new ArrayList<String>();
        registry.request(FIRST, request, (context, value) -> {
            calls.add("first:" + value);
            return UiProtocolRegistration.Result.success(value + 1);
        });
        registry.request(SECOND, request, (context, value) -> {
            calls.add("second:" + value);
            return UiProtocolRegistration.Result.success(value + 10);
        });
        AtomicInteger snapshots = new AtomicInteger();
        registry.state(FIRST, state, new UiProtocolRegistration.Provider<Integer>() {
            @Override public long revision(UiProtocolRegistration.ServerContext context) { return 7L; }
            @Override public Integer snapshot(UiProtocolRegistration.ServerContext context) {
                snapshots.incrementAndGet();
                return 42;
            }
        });
        registry.state(SECOND, state, provider());
        registry.freeze();

        assertThat(invokeInteger(registry.request(SECOND, REQUEST).orElseThrow(), 5).value()).contains(15);
        assertThat(invokeInteger(registry.request(FIRST, REQUEST).orElseThrow(), 8).value()).contains(9);
        assertThat(calls).containsExactly("second:5", "first:8");
        var registeredState = registry.state(FIRST, STATE).orElseThrow();
        assertThat(registeredState.type()).isSameAs(state);
        assertThat(registeredState.provider().revision(null)).isEqualTo(7L);
        assertThat(snapshots).hasValue(0);
        assertThat(registeredState.provider().snapshot(null)).isEqualTo(42);
        assertThat(snapshots).hasValue(1);
        assertThat(registry.state(SECOND, STATE).orElseThrow().provider().snapshot(null)).isEqualTo(1);
        assertThat(registry.state(FIRST, REQUEST)).isEmpty();
        assertThat(registry.request(FIRST, STATE)).isEmpty();
        assertThat(registry.request(ResourceLocation.parse("test:unknown"), REQUEST)).isEmpty();
        assertThat(registry.state(ResourceLocation.parse("test:unknown"), STATE)).isEmpty();
    }

    @Test
    void duplicates_unknown_machines_and_frozen_writes_do_not_replace_existing_callbacks() {
        var registry = new UiProtocolRegistration(List.of(FIRST));
        var request = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        registry.request(FIRST, request, (context, value) -> UiProtocolRegistration.Result.success(value));
        assertThatThrownBy(() -> registry.request(FIRST, request,
                (context, value) -> UiProtocolRegistration.Result.success(-1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> registry.state(FIRST,
                new UiProtocolRegistration.StateType<>(REQUEST, 1, CODEC), provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> registry.request(SECOND, request,
                (context, value) -> UiProtocolRegistration.Result.success(value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown machine");
        var state = new UiProtocolRegistration.StateType<>(STATE, 1, CODEC);
        registry.state(FIRST, state, provider());
        assertThatThrownBy(() -> registry.state(FIRST, state, provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> registry.request(FIRST,
                new UiProtocolRegistration.RequestType<>(STATE, 1, CODEC, CODEC),
                (context, value) -> UiProtocolRegistration.Result.success(value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> registry.state(SECOND, state, provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown machine");
        registry.freeze();
        registry.freeze();
        assertThatThrownBy(() -> registry.request(FIRST, request,
                (context, value) -> UiProtocolRegistration.Result.success(-2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> registry.state(FIRST,
                new UiProtocolRegistration.StateType<>(STATE, 1, CODEC), provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("frozen");
        assertThat(invokeInteger(registry.request(FIRST, REQUEST).orElseThrow(), 12).value()).contains(12);
        assertThat(registry.capabilities(FIRST)).containsExactly(
                new UiProtocolRegistration.Capability(REQUEST, 1, false),
                new UiProtocolRegistration.Capability(STATE, 1, true));
    }

    @Test
    void shared_message_definitions_reject_codec_version_and_kind_conflicts() {
        var registry = new UiProtocolRegistration(List.of(FIRST, SECOND));
        var request = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        registry.request(FIRST, request, (context, value) -> UiProtocolRegistration.Result.success(value));
        for (var conflicting : List.of(
                new UiProtocolRegistration.RequestType<>(REQUEST, 2, CODEC, CODEC),
                new UiProtocolRegistration.RequestType<>(REQUEST, 1, integerCodec(), CODEC),
                new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, integerCodec()))) {
            assertThatThrownBy(() -> registry.request(SECOND, conflicting,
                    (context, value) -> UiProtocolRegistration.Result.success(value)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting");
        }
        assertThatThrownBy(() -> registry.state(SECOND,
                new UiProtocolRegistration.StateType<>(REQUEST, 1, CODEC), provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting");
        assertThat(registry.capabilities(SECOND)).isEmpty();
        registry.request(SECOND, new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC),
                (context, value) -> UiProtocolRegistration.Result.success(value));
        registry.state(FIRST, new UiProtocolRegistration.StateType<>(STATE, 1, CODEC), provider());
        assertThatThrownBy(() -> registry.state(SECOND,
                new UiProtocolRegistration.StateType<>(STATE, 2, CODEC), provider()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry.state(SECOND,
                new UiProtocolRegistration.StateType<>(STATE, 1, integerCodec()), provider()))
                .isInstanceOf(IllegalStateException.class);
        registry.state(SECOND, new UiProtocolRegistration.StateType<>(STATE, 1, CODEC), provider());
    }

    @Test
    void protocol_limit_counts_requests_and_states_per_machine_without_partial_registration() {
        var registry = new UiProtocolRegistration(List.of(FIRST, SECOND));
        for (int i = 0; i < 64; i++) {
            var id = ResourceLocation.parse("test:message_" + i);
            if (i % 2 == 0) {
                registry.request(FIRST, new UiProtocolRegistration.RequestType<>(id, 1, CODEC, CODEC),
                        (context, value) -> UiProtocolRegistration.Result.success(value));
            } else {
                registry.state(FIRST, new UiProtocolRegistration.StateType<>(id, 1, CODEC), provider());
            }
        }
        var overflow = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        assertThatThrownBy(() -> registry.request(FIRST, overflow,
                (context, value) -> UiProtocolRegistration.Result.success(value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Too many");
        assertThatThrownBy(() -> registry.state(FIRST,
                new UiProtocolRegistration.StateType<>(STATE, 1, CODEC), provider()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Too many");
        assertThat(registry.request(FIRST, REQUEST)).isEmpty();
        assertThat(registry.state(FIRST, STATE)).isEmpty();
        registry.request(SECOND, overflow, (context, value) -> UiProtocolRegistration.Result.success(value));
        assertThat(invokeInteger(registry.request(SECOND, REQUEST).orElseThrow(), 3).value()).contains(3);
    }

    @Test
    void capability_lists_are_ordered_owned_snapshots_and_machine_ids_are_copied() {
        var machineIds = new ArrayList<>(List.of(FIRST));
        var registry = new UiProtocolRegistration(machineIds);
        machineIds.add(SECOND);
        registry.state(FIRST, new UiProtocolRegistration.StateType<>(STATE, 2, CODEC), provider());
        var earlier = registry.capabilities(FIRST);
        var request = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        assertThatThrownBy(() -> registry.request(SECOND, request,
                (context, value) -> UiProtocolRegistration.Result.success(value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unknown machine");
        registry.request(FIRST, request, (context, value) -> UiProtocolRegistration.Result.success(value));
        registry.freeze();
        var frozen = registry.capabilities(FIRST);
        assertThat(earlier).containsExactly(new UiProtocolRegistration.Capability(STATE, 2, true));
        assertThat(frozen).containsExactly(new UiProtocolRegistration.Capability(STATE, 2, true),
                new UiProtocolRegistration.Capability(REQUEST, 1, false));
        assertThatThrownBy(frozen::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(earlier::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.capabilities(SECOND)).isEmpty();
    }

    @Test
    void descriptors_and_registration_reject_nulls_and_nonpositive_versions() {
        assertThatThrownBy(() -> new UiProtocolRegistration.RequestType<>(REQUEST, 0, CODEC, CODEC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.StateType<>(STATE, -1, CODEC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.RequestType<>(null, 1, CODEC, CODEC))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.RequestType<>(REQUEST, 1, null, CODEC))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.StateType<>(STATE, 1, null))
                .isInstanceOf(NullPointerException.class);
        var registry = new UiProtocolRegistration(List.of(FIRST));
        var request = new UiProtocolRegistration.RequestType<>(REQUEST, 1, CODEC, CODEC);
        assertThatThrownBy(() -> registry.request(null, request,
                (context, value) -> UiProtocolRegistration.Result.success(value)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.request(FIRST, request, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.state(FIRST,
                new UiProtocolRegistration.StateType<>(STATE, 1, CODEC), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(registry.capabilities(FIRST)).isEmpty();
        assertThatThrownBy(() -> new UiProtocolRegistration.Result<>(UiProtocolRegistration.Status.SUCCESS,
                Optional.empty(), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UiProtocolRegistration.Result<>(UiProtocolRegistration.Status.TIMEOUT,
                Optional.of(1), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    // Only the fixture registers Integer codecs; production dispatch captures Q/R from the registration.
    @SuppressWarnings("unchecked")
    private static UiProtocolRegistration.Result<Integer> invokeInteger(
            UiProtocolRegistration.RequestRegistration<?, ?> registration, int request) {
        var typed = (UiProtocolRegistration.RequestRegistration<Integer, Integer>) registration;
        assertThat(typed.type().requestCodec()).isSameAs(CODEC);
        assertThat(typed.type().responseCodec()).isSameAs(CODEC);
        return typed.handler().handle(null, request);
    }

    private static StreamCodec<RegistryFriendlyByteBuf, Integer> integerCodec() {
        return StreamCodec.of((buffer, value) -> buffer.writeVarInt(value), RegistryFriendlyByteBuf::readVarInt);
    }

    private static UiProtocolRegistration.Provider<Integer> provider() {
        return new UiProtocolRegistration.Provider<>() {
            @Override public long revision(UiProtocolRegistration.ServerContext context) { return 1L; }
            @Override public Integer snapshot(UiProtocolRegistration.ServerContext context) { return 1; }
        };
    }
}

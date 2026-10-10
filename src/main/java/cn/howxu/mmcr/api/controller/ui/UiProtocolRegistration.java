package cn.howxu.mmcr.api.controller.ui;

import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Authoritative, typed controller UI protocol registration window.
 * @author howxu <dev@howxu.cn>
 */
public final class UiProtocolRegistration {
    private static final int MAX_PROTOCOLS_PER_MACHINE = 64;
    private final Set<ResourceLocation> machineIds;
    private final Map<Key, RequestRegistration<?, ?>> requests = new LinkedHashMap<>();
    private final Map<Key, StateRegistration<?>> states = new LinkedHashMap<>();
    private final Map<ResourceLocation, RequestType<?, ?>> requestTypes = new LinkedHashMap<>();
    private final Map<ResourceLocation, StateType<?>> stateTypes = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<Capability>> capabilities = new LinkedHashMap<>();
    private boolean frozen;

    public UiProtocolRegistration(Collection<ResourceLocation> machineIds) {
        this.machineIds = Set.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
    }

    public <Q, R> void request(ResourceLocation machineId, RequestType<Q, R> type, Handler<Q, R> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        requireRegistration(machineId, type.id(), type.version());
        RequestType<?, ?> previous = requestTypes.get(type.id());
        if (stateTypes.containsKey(type.id()) || previous != null
                && (previous.version() != type.version() || previous.requestCodec() != type.requestCodec()
                || previous.responseCodec() != type.responseCodec())) {
            throw new IllegalStateException("Conflicting controller UI protocol: " + type.id());
        }
        requests.put(new Key(machineId, type.id()), new RequestRegistration<>(type, handler));
        requestTypes.putIfAbsent(type.id(), type);
        capabilities.computeIfAbsent(machineId, ignored -> new ArrayList<>())
                .add(new Capability(type.id(), type.version(), false));
    }

    public <T> void state(ResourceLocation machineId, StateType<T> type, Provider<T> provider) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        requireRegistration(machineId, type.id(), type.version());
        StateType<?> previous = stateTypes.get(type.id());
        if (requestTypes.containsKey(type.id()) || previous != null
                && (previous.version() != type.version() || previous.codec() != type.codec())) {
            throw new IllegalStateException("Conflicting controller UI protocol: " + type.id());
        }
        states.put(new Key(machineId, type.id()), new StateRegistration<>(type, provider));
        stateTypes.putIfAbsent(type.id(), type);
        capabilities.computeIfAbsent(machineId, ignored -> new ArrayList<>())
                .add(new Capability(type.id(), type.version(), true));
    }

    public Optional<RequestRegistration<?, ?>> request(ResourceLocation machineId, ResourceLocation id) {
        return Optional.ofNullable(requests.get(new Key(machineId, id)));
    }

    public Optional<StateRegistration<?>> state(ResourceLocation machineId, ResourceLocation id) {
        return Optional.ofNullable(states.get(new Key(machineId, id)));
    }

    /** Immutable, registration-ordered protocol metadata for a menu's capability handshake. */
    public List<Capability> capabilities(ResourceLocation machineId) {
        Objects.requireNonNull(machineId, "machineId");
        return List.copyOf(capabilities.getOrDefault(machineId, List.of()));
    }

    public void freeze() {
        if (frozen) return;
        capabilities.replaceAll((machineId, entries) -> List.copyOf(entries));
        frozen = true;
    }

    private void requireRegistration(ResourceLocation machineId, ResourceLocation messageId, int version) {
        if (frozen) throw new IllegalStateException("Controller UI registration is frozen");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(messageId, "messageId");
        if (!machineIds.contains(machineId)) throw new IllegalStateException("Unknown machine: " + machineId);
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        Key key = new Key(machineId, messageId);
        if (requests.containsKey(key) || states.containsKey(key)) {
            throw new IllegalStateException("Duplicate controller UI protocol for " + machineId + ": " + messageId);
        }
        if (capabilities.getOrDefault(machineId, List.of()).size() >= MAX_PROTOCOLS_PER_MACHINE) {
            throw new IllegalStateException("Too many controller UI protocols for " + machineId);
        }
    }

    private static void requireType(ResourceLocation id, int version) {
        Objects.requireNonNull(id, "id");
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
    }

    /** A machine-local message key shared by requests and states.
     * @author howxu <dev@howxu.cn>
     */
    private record Key(ResourceLocation machineId, ResourceLocation id) {
        private Key {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(id, "id");
        }
    }

    /** Typed request and response codecs; reuse the same codecs across machines.
     * @author howxu <dev@howxu.cn>
     */
    public record RequestType<Q, R>(ResourceLocation id, int version,
                                    StreamCodec<RegistryFriendlyByteBuf, Q> requestCodec,
                                    StreamCodec<RegistryFriendlyByteBuf, R> responseCodec) {
        public RequestType {
            requireType(id, version);
            Objects.requireNonNull(requestCodec, "requestCodec");
            Objects.requireNonNull(responseCodec, "responseCodec");
        }
    }

    /** Typed latest-value state codec.
     * @author howxu <dev@howxu.cn>
     */
    public record StateType<T>(ResourceLocation id, int version, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        public StateType {
            requireType(id, version);
            Objects.requireNonNull(codec, "codec");
        }
    }

    /** A request definition keeps its codecs and handler under the same generic types.
     * @author howxu <dev@howxu.cn>
     */
    public record RequestRegistration<Q, R>(RequestType<Q, R> type, Handler<Q, R> handler) {
        public RequestRegistration {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(handler, "handler");
        }
    }

    /** A state definition keeps its codec and provider under the same generic type.
     * @author howxu <dev@howxu.cn>
     */
    public record StateRegistration<T>(StateType<T> type, Provider<T> provider) {
        public StateRegistration {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /** Immutable metadata, without callbacks or codecs, advertised when a menu opens.
     * @author howxu <dev@howxu.cn>
     */
    public record Capability(ResourceLocation id, int version, boolean state) { }

    /** Transport and handler outcomes share the same status vocabulary.
     * @author howxu <dev@howxu.cn>
     */
    public enum Status {
        SUCCESS, REJECTED, UNSUPPORTED, VERSION_MISMATCH, INVALID_REQUEST,
        CLOSED, TIMEOUT, BUSY, HANDLER_FAILED
    }

    /** Owned rejection text and a typed successful response.
     * @author howxu <dev@howxu.cn>
     */
    public record Result<R>(Status status, Optional<R> value, Optional<Component> message) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(message, "message");
            if ((status == Status.SUCCESS) != value.isPresent()) {
                throw new IllegalArgumentException("Only successful UI results must contain a value");
            }
            message = message.map(ComponentSnapshots::copy);
        }

        public static <R> Result<R> success(R value) {
            return new Result<>(Status.SUCCESS, Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
        }

        public static <R> Result<R> reject(Component reason) {
            return new Result<>(Status.REJECTED, Optional.empty(), Optional.of(Objects.requireNonNull(reason, "reason")));
        }

        @Override public Optional<Component> message() { return message.map(ComponentSnapshots::copy); }
    }

    /** Server callback context; do not retain it for asynchronous world access.
     * @author howxu <dev@howxu.cn>
     */
    public record ServerContext(ServerPlayer player, MachineBehaviorContext machine, Optional<String> laneId) {
        public ServerContext {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(machine, "machine");
            Objects.requireNonNull(laneId, "laneId");
        }
    }

    /** Synchronous, server-thread request callback.
     * @author howxu <dev@howxu.cn>
     */
    @FunctionalInterface
    public interface Handler<Q, R> {
        Result<R> handle(ServerContext context, Q request);
    }

    /** Revision is queried before a latest-value snapshot is captured.
     * @author howxu <dev@howxu.cn>
     */
    public interface Provider<T> {
        long revision(ServerContext context);
        T snapshot(ServerContext context);
    }
}

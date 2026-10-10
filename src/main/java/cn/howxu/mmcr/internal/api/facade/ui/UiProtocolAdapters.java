package cn.howxu.mmcr.internal.api.facade.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.ServerContext;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.StateType;
import cn.howxu.mmcr.internal.api.facade.behavior.BehaviorAdapters;
import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.ui.UiProtocolRegistrar;
import cn.howxu.mmcr.publicapi.ui.UiRequestHandler;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.publicapi.ui.UiServerContext;
import cn.howxu.mmcr.publicapi.ui.UiStateProvider;
import cn.howxu.mmcr.publicapi.ui.UiStateType;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Typed bridges for public descriptors, callbacks and the core registration lifecycle.
 * @author howxu <dev@howxu.cn>
 */
public final class UiProtocolAdapters {
    private UiProtocolAdapters() { }

    public static <Q, R> UiRequestType<Q, R> requestType(ResourceLocation id, int version,
            StreamCodec<RegistryFriendlyByteBuf, Q> requestCodec,
            StreamCodec<RegistryFriendlyByteBuf, R> responseCodec) {
        return wrap(new RequestType<>(id, version, requestCodec, responseCodec));
    }

    public static <T> UiStateType<T> stateType(ResourceLocation id, int version,
            StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        return wrap(new StateType<>(id, version, codec));
    }

    public static <R> UiResult<R> success(R value) { return wrap(Result.success(value)); }
    public static <R> UiResult<R> reject(Component reason) { return wrap(Result.reject(reason)); }
    public static <Q, R> UiRequestType<Q, R> wrap(RequestType<Q, R> value) {
        return new RequestAdapter<>(Objects.requireNonNull(value, "value"));
    }
    public static <T> UiStateType<T> wrap(StateType<T> value) {
        return new StateAdapter<>(Objects.requireNonNull(value, "value"));
    }
    public static <R> UiResult<R> wrap(Result<R> value) {
        return new ResultAdapter<>(Objects.requireNonNull(value, "value"));
    }
    public static UiServerContext wrap(ServerContext value) {
        return new ContextAdapter(Objects.requireNonNull(value, "value"));
    }
    public static <Q, R> RequestType<Q, R> unwrap(UiRequestType<Q, R> value) {
        return ((RequestAdapter<Q, R>) Objects.requireNonNull(value, "value")).delegate;
    }
    public static <T> StateType<T> unwrap(UiStateType<T> value) {
        return ((StateAdapter<T>) Objects.requireNonNull(value, "value")).delegate;
    }
    public static <R> Result<R> unwrap(UiResult<R> value) {
        return ((ResultAdapter<R>) Objects.requireNonNull(value, "value")).delegate;
    }

    public static UiProtocolRegistrar registrar(Collection<ResourceLocation> machineIds) {
        return new RegistrarAdapter(new UiProtocolRegistration(machineIds));
    }
    public static UiProtocolRegistration core(RegisterControllerUiProtocolsEvent event) {
        return ((RegistrarAdapter) Objects.requireNonNull(event, "event").registrar()).delegate;
    }
    public static UiProtocolRegistration freeze(RegisterControllerUiProtocolsEvent event) {
        UiProtocolRegistration registration = core(event);
        registration.freeze();
        return registration;
    }

    private static void registration(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException exception) {
            throw new RegistrationException(exception.getMessage(), exception);
        }
    }

    /** Runtime-owned request descriptor view.
     * @author howxu <dev@howxu.cn>
     */
    private record RequestAdapter<Q, R>(RequestType<Q, R> delegate) implements UiRequestType<Q, R> {
        @Override public ResourceLocation id() { return delegate.id(); }
        @Override public int version() { return delegate.version(); }
    }

    /** Runtime-owned state descriptor view.
     * @author howxu <dev@howxu.cn>
     */
    private record StateAdapter<T>(StateType<T> delegate) implements UiStateType<T> {
        @Override public ResourceLocation id() { return delegate.id(); }
        @Override public int version() { return delegate.version(); }
    }

    /** Runtime-owned result view, retaining the core's defensive message copies.
     * @author howxu <dev@howxu.cn>
     */
    private record ResultAdapter<R>(Result<R> delegate) implements UiResult<R> {
        @Override public Status status() { return Status.valueOf(delegate.status().name()); }
        @Override public Optional<R> value() { return delegate.value(); }
        @Override public Optional<Component> message() { return delegate.message(); }
    }

    /** Server-only context view, reusing the existing machine/storage facade.
     * @author howxu <dev@howxu.cn>
     */
    private record ContextAdapter(ServerContext delegate) implements UiServerContext {
        @Override public ServerPlayer player() { return delegate.player(); }
        @Override public MachineContext machine() { return BehaviorAdapters.wrap(delegate.machine()); }
        @Override public Optional<String> laneId() { return delegate.laneId(); }
    }

    /** Public declarations delegate to the same frozen core used by the transport.
     * @author howxu <dev@howxu.cn>
     */
    private record RegistrarAdapter(UiProtocolRegistration delegate) implements UiProtocolRegistrar {
        @Override public <Q, R> void request(ResourceLocation machineId, UiRequestType<Q, R> type,
                UiRequestHandler<Q, R> handler) {
            Objects.requireNonNull(handler, "handler");
            registration(() -> delegate.request(machineId, unwrap(type),
                    (context, request) -> unwrap(handler.handle(wrap(context), request))));
        }

        @Override public <T> void state(ResourceLocation machineId, UiStateType<T> type, UiStateProvider<T> provider) {
            Objects.requireNonNull(provider, "provider");
            registration(() -> delegate.state(machineId, unwrap(type), new UiProtocolRegistration.Provider<>() {
                @Override public long revision(ServerContext context) { return provider.revision(wrap(context)); }
                @Override public T snapshot(ServerContext context) { return provider.snapshot(wrap(context)); }
            }));
        }
    }
}

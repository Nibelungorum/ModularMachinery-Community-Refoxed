package cn.howxu.mmcr.internal.api.facade.client;

import cn.howxu.mmcr.api.controller.ui.client.ControllerUiRegistration;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import cn.howxu.mmcr.internal.api.facade.ui.UiSnapshotAdapters;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiFactory;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiOpenContext;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiSession;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiSlots;
import cn.howxu.mmcr.publicapi.client.ui.UiSubscription;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUisEvent;
import cn.howxu.mmcr.publicapi.registration.RegistrationException;
import cn.howxu.mmcr.publicapi.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.publicapi.ui.UiStateType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Thin client contract adapters; all lifecycle and dispatch live in the core.
 * @author howxu <dev@howxu.cn> */
public final class UiClientAdapters {
    private UiClientAdapters() {}

    public static RegisterControllerUisEvent.Registrar registration(Collection<Identifier> machineIds) {
        return new RegistrationAdapter(new ControllerUiRegistration(machineIds));
    }

    public static ControllerUiRegistration core(RegisterControllerUisEvent event) {
        return ((RegistrationAdapter) event.registrar()).delegate;
    }

    public static void freeze(RegisterControllerUisEvent event) { core(event).freeze(); }

    public static ControllerUiSession wrap(ControllerUiRegistration.Session session) {
        return new SessionAdapter(Objects.requireNonNull(session, "session"));
    }

    public static ControllerUiOpenContext wrap(ControllerUiRegistration.OpenContext context) {
        return new ContextAdapter(context, wrap(context.session()));
    }

    public static ControllerUiRegistration.Factory unwrap(ControllerUiFactory factory) {
        return new FactoryAdapter(Objects.requireNonNull(factory, "factory"));
    }

    public static ControllerUiRegistration.OpenContext context(AbstractContainerMenu menu, Inventory inventory,
                                                               Component title, ControllerUiRegistration.Session session) {
        return new CoreContext(menu, inventory, title, session);
    }

    /** Authoritative collector forwarding with public registration failures.
     * @author howxu <dev@howxu.cn> */
    private record RegistrationAdapter(ControllerUiRegistration delegate) implements RegisterControllerUisEvent.Registrar {
        @Override public void register(Identifier machineId, ControllerUiFactory factory) {
            try { delegate.register(machineId, unwrap(factory)); }
            catch (IllegalStateException exception) {
                throw new RegistrationException(exception.getMessage(), exception);
            }
        }
    }

    /** Preserves author factory identity for failure diagnostics.
     * @author howxu <dev@howxu.cn> */
    private record FactoryAdapter(ControllerUiFactory delegate) implements ControllerUiRegistration.Factory {
        @Override public Screen create(ControllerUiRegistration.OpenContext context) { return delegate.create(wrap(context)); }
        @Override public String toString() { return delegate.getClass().getName(); }
    }

    /** Owned native factory arguments.
     * @author howxu <dev@howxu.cn> */
    private record CoreContext(AbstractContainerMenu menu, Inventory inventory, Component title,
                               ControllerUiRegistration.Session session) implements ControllerUiRegistration.OpenContext {
        private CoreContext {
            Objects.requireNonNull(menu, "menu");
            Objects.requireNonNull(inventory, "inventory");
            title = ComponentSnapshots.copy(Objects.requireNonNull(title, "title"));
            Objects.requireNonNull(session, "session");
        }
        @Override public Component title() { return ComponentSnapshots.copy(title); }
    }

    /** Native context view.
     * @author howxu <dev@howxu.cn> */
    private record ContextAdapter(ControllerUiRegistration.OpenContext delegate,
                                  ControllerUiSession session) implements ControllerUiOpenContext {
        @Override public AbstractContainerMenu menu() { return delegate.menu(); }
        @Override public Inventory inventory() { return delegate.inventory(); }
        @Override public Component title() { return delegate.title(); }
    }

    /** Session forwarding, without independent state or request storage.
     * @author howxu <dev@howxu.cn> */
    private record SessionAdapter(ControllerUiRegistration.Session delegate) implements ControllerUiSession {
        @Override public UUID id() { return delegate.id(); }
        @Override public boolean isOpen() { return delegate.isOpen(); }
        @Override public ControllerUiSnapshot snapshot() { return UiSnapshotAdapters.wrap(delegate.snapshot()); }
        @Override public ControllerUiSlots slots() { return new SlotsAdapter(delegate.slots()); }
        @Override public boolean supports(UiRequestType<?, ?> type) { return delegate.supports(UiProtocolAdapters.unwrap(type)); }
        @Override public boolean supports(UiStateType<?> type) { return delegate.supports(UiProtocolAdapters.unwrap(type)); }
        @Override public <Q, R> CompletionStage<UiResult<R>> request(UiRequestType<Q, R> type, Q body) {
            return delegate.request(UiProtocolAdapters.unwrap(type), body).thenApply(UiProtocolAdapters::wrap);
        }
        @Override public <Q, R> CompletionStage<UiResult<R>> request(UiRequestType<Q, R> type, String laneId, Q body) {
            return delegate.request(UiProtocolAdapters.unwrap(type), laneId, body).thenApply(UiProtocolAdapters::wrap);
        }
        @Override public UiSubscription subscribe(Executor executor, Consumer<ControllerUiSnapshot> listener) {
            Objects.requireNonNull(listener, "listener");
            return new SubscriptionAdapter(delegate.subscribe(executor, value -> listener.accept(UiSnapshotAdapters.wrap(value))));
        }
        @Override public <T> UiSubscription subscribe(UiStateType<T> type, Executor executor, Consumer<T> listener) {
            return new SubscriptionAdapter(delegate.subscribe(UiProtocolAdapters.unwrap(type), executor, listener));
        }
        @Override public <T> Optional<T> state(UiStateType<T> type) { return delegate.state(UiProtocolAdapters.unwrap(type)); }
        @Override public UiSubscription onClosed(Executor executor, Runnable listener) {
            return new SubscriptionAdapter(delegate.onClosed(executor, listener));
        }
        @Override public void close() { delegate.close(); }
    }

    /** Slot policy forwarding.
     * @author howxu <dev@howxu.cn> */
    private record SlotsAdapter(ControllerUiRegistration.Slots delegate) implements ControllerUiSlots {
        @Override public boolean isPlayerInventoryVisible() { return delegate.isPlayerInventoryVisible(); }
        @Override public void setPlayerInventoryVisible(boolean visible) { delegate.setPlayerInventoryVisible(visible); }
    }

    /** Cancellation forwarding.
     * @author howxu <dev@howxu.cn> */
    private record SubscriptionAdapter(ControllerUiRegistration.Subscription delegate) implements UiSubscription {
        @Override public void close() { delegate.close(); }
    }
}

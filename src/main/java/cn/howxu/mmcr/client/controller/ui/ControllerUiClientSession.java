package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.StateType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.api.controller.ui.client.ControllerUiRegistration;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiCustomStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiProgressPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiRequestPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiResponsePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One opening's main-thread transport and atomically published, owned UI values.
 * @author howxu <dev@howxu.cn> */
public final class ControllerUiClientSession implements ControllerUiRegistration.Session {
    private final AbstractContainerMenu menu;
    private final ControllerMenuOpenData opening;
    private final ControllerUiSnapshotData initial;
    private final RegistryAccess.Frozen registries;
    private final UiProtocolRegistration protocols;
    private final Executor mainThread;
    private final BooleanSupplier isMainThread;
    private final Supplier<AbstractContainerMenu> currentMenu;
    private final Supplier<AbstractContainerMenu> observedMenu;
    private final AbstractContainerMenu precedingMenu;
    private final Runnable closeMenu;
    private final Consumer<CustomPacketPayload> sender;
    private final PendingUiRequests pending;
    private final ControllerUiSlotVisibility slots;
    private final AtomicReference<ControllerUiSnapshotData> snapshot;
    private final Map<ResourceLocation, FrozenState> states = new ConcurrentHashMap<>();
    private final List<Delivery<ControllerUiSnapshot>> snapshotListeners = new ArrayList<>();
    private final List<StateListener<?>> stateListeners = new ArrayList<>();
    private final List<Delivery<Boolean>> closeListeners = new ArrayList<>();
    private volatile boolean open = true;
    private volatile boolean installed;
    private long fullBaseline = -1;

    public ControllerUiClientSession(AbstractContainerMenu menu, ControllerMenuOpenData opening, Component title,
                                     RegistryAccess registries, UiProtocolRegistration protocols, Executor mainThread,
                                     LongSupplier monotonicNanos, Consumer<CustomPacketPayload> sender,
                                     BooleanSupplier isMainThread, Supplier<AbstractContainerMenu> currentMenu,
                                     Runnable closeMenu) {
        this(menu, opening, title, registries, protocols, mainThread, monotonicNanos, sender,
                isMainThread, currentMenu, closeMenu, () -> menu);
    }

    /** currentMenu is main-thread-only; observedMenu must read a safely published reference,
     * never a live player/world. Native lifecycle entries invalidate even idle subscriptions. */
    ControllerUiClientSession(AbstractContainerMenu menu, ControllerMenuOpenData opening, Component title,
                              RegistryAccess registries, UiProtocolRegistration protocols, Executor mainThread,
                              LongSupplier monotonicNanos, Consumer<CustomPacketPayload> sender,
                              BooleanSupplier isMainThread, Supplier<AbstractContainerMenu> currentMenu,
                              Runnable closeMenu, Supplier<AbstractContainerMenu> observedMenu) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.opening = Objects.requireNonNull(opening, "opening");
        if (!(menu instanceof ControllerUiMenu controller) || !opening.equals(controller.uiOpenData())) {
            throw new IllegalArgumentException("Opening metadata must belong to the actual menu");
        }
        Objects.requireNonNull(registries, "registries");
        this.registries = registries instanceof RegistryAccess.Frozen frozen ? frozen : registries.freeze();
        this.protocols = Objects.requireNonNull(protocols, "protocols");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.isMainThread = Objects.requireNonNull(isMainThread, "isMainThread");
        this.currentMenu = Objects.requireNonNull(currentMenu, "currentMenu");
        this.observedMenu = Objects.requireNonNull(observedMenu, "observedMenu");
        requireMainThread();
        precedingMenu = currentMenu.get();
        installed = precedingMenu == menu;
        this.closeMenu = Objects.requireNonNull(closeMenu, "closeMenu");
        this.pending = new PendingUiRequests(monotonicNanos);
        initial = new ControllerUiSnapshotData(opening.sessionId(), 0, false, opening.dimension(), opening.pos(),
                new ControllerUiSnapshotData.HeaderData(opening.machineId(), opening.kind(), opening.role(), title,
                        opening.formed(), false, false, opening.installedModuleCount(), opening.connectedHostId().orElse(null),
                        0, 1, List.of(), 0, 1, 0, 0, List.of(), null, null, false, Map.of(), List.of()), List.of());
        snapshot = new AtomicReference<>(initial);
        slots = new ControllerUiSlotVisibility(menu, id(), isMainThread, this::isOpen, currentMenu);
    }

    @Override public UUID id() { return opening.sessionId(); }
    @Override public boolean isOpen() { return open; }
    @Override public ControllerUiSnapshot snapshot() { return snapshot.get(); }
    @Override public ControllerUiSlotVisibility slots() { return slots; }

    @Override public boolean supports(RequestType<?, ?> type) {
        Objects.requireNonNull(type, "type");
        return advertised(type.id(), type.version(), false) && protocols.request(opening.machineId(), type.id())
                .map(registration -> registration.type().version() == type.version()
                        && registration.type().requestCodec() == type.requestCodec()
                        && registration.type().responseCodec() == type.responseCodec()).orElse(false);
    }

    @Override public boolean supports(StateType<?> type) {
        Objects.requireNonNull(type, "type");
        return advertised(type.id(), type.version(), true) && protocols.state(opening.machineId(), type.id())
                .map(registration -> registration.type().version() == type.version()
                        && registration.type().codec() == type.codec()).orElse(false);
    }

    private boolean advertised(ResourceLocation id, int version, boolean state) {
        return opening.capabilities().stream().anyMatch(value -> value.id().equals(id)
                && value.version() == version && value.state() == state);
    }

    @Override public <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, Q body) {
        return request(type, Optional.empty(), body);
    }

    @Override public <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, String laneId, Q body) {
        Objects.requireNonNull(laneId, "laneId");
        return request(type, Optional.of(laneId), body);
    }

    private <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, Optional<String> laneId, Q body) {
        Objects.requireNonNull(type, "type");
        var result = new CompletableFuture<Result<R>>();
        byte[] frozen = null;
        Status failure = null;
        if (!open) failure = Status.CLOSED;
        else if (!supports(type)) failure = Status.UNSUPPORTED;
        else {
            try {
                frozen = ControllerUiPayloadCodec.encodeBounded(type.requestCodec(), body, registries,
                        ControllerUiPayloadCodec.REQUEST_LIMIT);
            } catch (RuntimeException invalid) {
                failure = Status.INVALID_REQUEST;
            }
        }
        byte[] owned = frozen;
        Status encodingStatus = failure;
        boolean constructionRequest = constructionCall();
        mainThread.execute(() -> {
            requireMainThread();
            if (!validMenu()) {
                // Only a request made in the router's one-time construction scope may wait
                // for this opening. No transport or general stale-menu permission is granted.
                if (open && constructionRequest && !installed && currentMenu.get() == precedingMenu) {
                    result.complete(PendingUiRequests.status(Status.INVALID_REQUEST));
                } else {
                    end();
                    result.complete(PendingUiRequests.status(Status.CLOSED));
                }
                return;
            }
            if (encodingStatus != null) { result.complete(PendingUiRequests.status(encodingStatus)); return; }
            if (!snapshot.get().ready() || laneId.filter(String::isBlank).isPresent()
                    || laneId.isPresent() && snapshot.get().lanes().stream().noneMatch(lane -> lane.id().equals(laneId.get()))) {
                result.complete(PendingUiRequests.status(Status.INVALID_REQUEST));
                return;
            }
            var entry = pending.add(type);
            entry.result().thenAccept(result::complete);
            if (entry.requestId() == 0) return;
            try {
                PktControllerUiRequestPayload packet;
                try {
                    packet = new PktControllerUiRequestPayload(menu.containerId, id(), entry.requestId(), type.id(),
                            type.version(), laneId, owned);
                } catch (IllegalArgumentException invalidHeader) {
                    pending.fail(entry.requestId(), Status.INVALID_REQUEST);
                    return;
                }
                sender.accept(packet);
            } catch (RuntimeException transportFailure) {
                pending.fail(entry.requestId(), Status.HANDLER_FAILED);
            }
        });
        return result.minimalCompletionStage();
    }

    public void handle(PktControllerUiSnapshotPayload packet) {
        if (!accept(packet.containerId(), packet.sessionId())) return;
        ControllerUiSnapshotData next = packet.snapshotData();
        if (packet.revision() <= snapshot.get().revision() || next.revision() != packet.revision()
                || !id().equals(next.sessionId()) || !next.ready() || !opening.dimension().equals(next.dimension())
                || !opening.pos().equals(next.controllerPos()) || !opening.machineId().equals(next.machineId())
                || opening.role() != next.role()) return;
        fullBaseline = packet.revision();
        publish(next);
    }

    public void handle(PktControllerUiProgressPayload packet) {
        if (!accept(packet.containerId(), packet.sessionId()) || fullBaseline < 0
                || packet.fullBaselineRevision() != fullBaseline || packet.revision() <= snapshot.get().revision()) return;
        try {
            publish(snapshot.get().withProgress(packet.revision(), packet.progress()));
        } catch (IllegalArgumentException invalidDelta) {
            // Keep the last valid full/progress value; only a matching full baseline can replace it.
        }
    }

    public void handle(PktControllerUiResponsePayload packet) {
        if (!accept(packet.containerId(), packet.sessionId())) return;
        pending.respond(packet.requestId(), packet.status(), packet.reason(), packet.body(), registries);
    }

    public void handle(PktControllerUiCustomStatePayload packet) {
        if (!accept(packet.containerId(), packet.sessionId()) || !advertised(packet.stateId(), packet.version(), true)) return;
        var registration = protocols.state(opening.machineId(), packet.stateId());
        if (registration.isEmpty() || registration.get().type().version() != packet.version()) return;
        FrozenState previous = states.get(packet.stateId());
        if (previous != null && packet.providerRevision() <= previous.revision) return;
        captureState(registration.get().type(), packet);
    }

    private <T> void captureState(StateType<T> type, PktControllerUiCustomStatePayload packet) {
        var frozen = new FrozenState(packet.version(), packet.providerRevision(), packet.body());
        try {
            decode(type, frozen); // Reject malformed values even when there are no listeners.
        } catch (RuntimeException invalidState) {
            return;
        }
        states.put(type.id(), frozen);
        for (StateListener<?> listener : List.copyOf(stateListeners)) listener.publish(type.id(), frozen);
    }

    private void publish(ControllerUiSnapshotData next) {
        snapshot.set(next);
        for (Delivery<ControllerUiSnapshot> listener : List.copyOf(snapshotListeners)) listener.offer(next.revision(), next);
    }

    @Override public <T> Optional<T> state(StateType<T> type) {
        if (!open || !supports(type)) return Optional.empty();
        FrozenState frozen = states.get(type.id());
        if (frozen == null || frozen.version != type.version()) return Optional.empty();
        T value = decode(type, frozen);
        return open ? Optional.of(value) : Optional.empty();
    }

    private <T> T decode(StateType<T> type, FrozenState state) {
        return Objects.requireNonNull(ControllerUiPayloadCodec.decodeExact(type.codec(), state.body.clone(), registries,
                ControllerUiPayloadCodec.STATE_LIMIT), "decoded state");
    }

    @Override public ControllerUiRegistration.Subscription subscribe(Executor executor, Consumer<ControllerUiSnapshot> listener) {
        var delivery = new Delivery<>(executor, listener, this::deliveryValid, false);
        delivery.removal = () -> snapshotListeners.remove(delivery);
        boolean constructionSubscription = constructionCall();
        mainThread.execute(() -> {
            requireMainThread();
            if (delivery.cancelled() || !registrationValid(constructionSubscription)) { delivery.close(); return; }
            snapshotListeners.add(delivery);
            ControllerUiSnapshot current = snapshot();
            delivery.offer(current.revision(), current);
        });
        return delivery;
    }

    @Override public <T> ControllerUiRegistration.Subscription subscribe(StateType<T> type, Executor executor, Consumer<T> listener) {
        Objects.requireNonNull(type, "type");
        var delivery = new Delivery<>(executor, listener, this::deliveryValid, false);
        var subscription = new StateListener<>(type, delivery);
        delivery.removal = () -> stateListeners.remove(subscription);
        boolean constructionSubscription = constructionCall();
        mainThread.execute(() -> {
            requireMainThread();
            if (delivery.cancelled() || !registrationValid(constructionSubscription) || !supports(type)) { delivery.close(); return; }
            stateListeners.add(subscription);
            FrozenState current = states.get(type.id());
            if (current != null) subscription.publish(type.id(), current);
        });
        return delivery;
    }

    @Override public ControllerUiRegistration.Subscription onClosed(Executor executor, Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        var delivery = new Delivery<Boolean>(executor, ignored -> listener.run(), () -> !open, true);
        delivery.removal = () -> closeListeners.remove(delivery);
        boolean constructionSubscription = constructionCall();
        mainThread.execute(() -> {
            requireMainThread();
            if (delivery.cancelled()) return;
            if (open) registrationValid(constructionSubscription);
            if (open) closeListeners.add(delivery);
            else delivery.offer(0, true);
        });
        return delivery;
    }

    @Override public void close() {
        mainThread.execute(() -> {
            requireMainThread();
            boolean matches = validMenu();
            end();
            if (matches && matchesBoundMenu()) closeMenu.run();
        });
    }

    /** Called every client tick, independent of the current Screen implementation. */
    public void tick() {
        requireMainThread();
        if (!validMenu()) end();
        else pending.expire();
    }

    /** Main-thread logout/menu-switch cleanup, without closing any replacement menu. */
    public void invalidate() { requireMainThread(); end(); }

    /** Synchronous native installation/close observation, before tick or executor delivery. */
    void menuChanged(AbstractContainerMenu current) {
        requireMainThread();
        if (current == menu && id().equals(((ControllerUiMenu) menu).uiOpenData().sessionId())) installed = true;
        else end();
    }

    private boolean deliveryValid() {
        AbstractContainerMenu observed = observedMenu.get();
        return open && (observed == menu || !installed && observed == precedingMenu);
    }

    private boolean constructionCall() {
        return isMainThread.getAsBoolean() && slots.isConstructingScreen() && !installed;
    }

    private boolean registrationValid(boolean constructionSubscription) {
        if (validMenu()) return true;
        // Initial-value subscriptions can be registered before installation; transport cannot.
        if (open && constructionSubscription && !installed && currentMenu.get() == precedingMenu) return true;
        end();
        return false;
    }

    private boolean validMenu() {
        return open && matchesBoundMenu();
    }

    private boolean matchesBoundMenu() {
        boolean matches = currentMenu.get() == menu && id().equals(((ControllerUiMenu) menu).uiOpenData().sessionId());
        if (matches) installed = true;
        return matches;
    }

    private boolean accept(int containerId, UUID sessionId) {
        requireMainThread();
        if (!validMenu()) { end(); return false; }
        return menu.containerId == containerId && id().equals(sessionId);
    }

    private void requireMainThread() {
        if (!isMainThread.getAsBoolean()) throw new IllegalStateException("Controller UI transport requires the client main thread");
    }

    private void end() {
        if (!open) return;
        open = false;
        states.clear();
        snapshot.set(initial);
        fullBaseline = -1;
        for (var listener : List.copyOf(snapshotListeners)) listener.close();
        for (var listener : List.copyOf(stateListeners)) listener.delivery.close();
        snapshotListeners.clear();
        stateListeners.clear();
        pending.close();
        var closing = List.copyOf(closeListeners);
        closeListeners.clear();
        for (var listener : closing) listener.offer(0, true);
    }

    /** Frozen bytes are private and cloned at each decoder boundary.
     * @author howxu <dev@howxu.cn> */
    private record FrozenState(int version, long revision, byte[] body) {
        private FrozenState { body = body.clone(); }
    }

    /** Typed state ownership is acquired on the main thread before executor scheduling.
     * @author howxu <dev@howxu.cn> */
    private final class StateListener<T> {
        private final StateType<T> type;
        private final Delivery<T> delivery;
        private StateListener(StateType<T> type, Delivery<T> delivery) { this.type = type; this.delivery = delivery; }
        private void publish(ResourceLocation id, FrozenState state) {
            if (!type.id().equals(id) || state.version != type.version() || delivery.cancelled() || !open) return;
            try {
                delivery.offer(state.revision, decode(type, state));
            } catch (RuntimeException failure) {
                MMCR.LOG.error("Controller UI state subscriber decode failed for {}", id, failure);
            }
        }
    }

    /** One drain per subscriber, regardless of how parallel its executor is.
     * @author howxu <dev@howxu.cn> */
    private final class Delivery<T> implements ControllerUiRegistration.Subscription {
        private final Executor executor;
        private Consumer<T> listener;
        private final BooleanSupplier valid;
        private final boolean terminal;
        private final ArrayDeque<T> queue = new ArrayDeque<>();
        private Runnable removal = () -> {};
        private boolean closed;
        private boolean running;
        private boolean hasRevision;
        private long revision;

        private Delivery(Executor executor, Consumer<T> listener, BooleanSupplier valid, boolean terminal) {
            this.executor = Objects.requireNonNull(executor, "executor");
            this.listener = Objects.requireNonNull(listener, "listener");
            this.valid = valid;
            this.terminal = terminal;
        }

        private void offer(long nextRevision, T value) {
            synchronized (this) {
                if (closed || !valid.getAsBoolean() || hasRevision && nextRevision <= revision) return;
                hasRevision = true;
                revision = nextRevision;
                // Latest-value updates can coalesce while preserving serial callback order.
                queue.clear();
                queue.add(value);
                if (running) return;
                running = true;
            }
            try {
                executor.execute(this::drain);
            } catch (RuntimeException rejected) {
                close();
                MMCR.LOG.error("Controller UI subscription executor rejected delivery", rejected);
            }
        }

        private void drain() {
            while (true) {
                T value;
                Consumer<T> callback;
                synchronized (this) {
                    if (closed || !valid.getAsBoolean()) { queue.clear(); running = false; return; }
                    value = queue.poll();
                    if (value == null) { running = false; return; }
                    callback = listener;
                }
                // Once dequeued the callback is in flight. Never hold a lock while calling author code:
                // a UI callback may wait for a main-thread request completion.
                try {
                    callback.accept(value);
                } catch (RuntimeException failure) {
                    MMCR.LOG.error("Controller UI subscriber failed", failure);
                }
                if (terminal) { close(); return; }
            }
        }

        private synchronized boolean cancelled() { return closed; }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                queue.clear();
                listener = null;
            }
            mainThread.execute(removal);
        }
    }
}

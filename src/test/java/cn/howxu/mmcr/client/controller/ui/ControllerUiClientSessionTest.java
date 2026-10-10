package cn.howxu.mmcr.client.controller.ui;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.RequestType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Result;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.StateType;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.api.controller.ui.client.ControllerUiRegistration;
import cn.howxu.mmcr.internal.api.facade.client.UiClientAdapters;
import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiCustomStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiProgressPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiRequestPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiResponsePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Queued executors and pure-data codecs exercise the real client session without a world.
 * @author howxu <dev@howxu.cn> */
class ControllerUiClientSessionTest {
    private static final ResourceLocation MACHINE = ResourceLocation.parse("test:machine");
    private static final ResourceLocation RECIPE = ResourceLocation.parse("test:recipe");
    private static final StreamCodec<RegistryFriendlyByteBuf, ArrayList<Integer>> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value.getFirst()),
            buffer -> new ArrayList<>(List.of(buffer.readVarInt())));
    private static final RequestType<ArrayList<Integer>, ArrayList<Integer>> REQUEST = new RequestType<>(
            ResourceLocation.parse("test:request"), 1, CODEC, CODEC);
    private static final StateType<ArrayList<Integer>> STATE = new StateType<>(ResourceLocation.parse("test:state"), 1, CODEC);

    @Test
    void opening_snapshot_preserves_all_kinds_roles_identity_and_owned_name_without_ready() {
        for (var kind : ControllerUiSnapshot.Kind.values()) {
            for (var role : ControllerUiSnapshot.Role.values()) {
                var fixture = new Fixture(kind, role);
                var snapshot = fixture.session.snapshot();
                assertThat(snapshot.ready()).isFalse();
                assertThat(snapshot.sessionId()).isEqualTo(fixture.menu.uiOpenData().sessionId());
                assertThat(snapshot.machineId()).isEqualTo(MACHINE);
                assertThat(snapshot.kind()).isEqualTo(kind);
                assertThat(snapshot.role()).isEqualTo(role);
                assertThat(snapshot.dimension()).isEqualTo(Level.OVERWORLD);
                assertThat(snapshot.controllerPos()).isEqualTo(new BlockPos(4, 5, 6));
                ((MutableComponent) snapshot.machineName()).append(Component.translatable("test:changed"));
                fixture.title.append(Component.translatable("test:changed"));
                assertThat(fixture.session.snapshot().machineName()).isEqualTo(Component.translatable("test:name"));
                var pending = fixture.session.request(REQUEST, body(1));
                fixture.main.drain();
                assertThat(result(pending).status()).isEqualTo(Status.INVALID_REQUEST);
                assertThat(fixture.sent).isEmpty();
            }
        }
    }

    @Test
    void any_thread_request_freezes_before_enqueue_and_main_thread_allocates_in_actual_send_order() throws InterruptedException {
        var fixture = new Fixture();
        fixture.ready(1);
        var firstStage = new AtomicReference<CompletionStage<Result<ArrayList<Integer>>>>();
        var secondStage = new AtomicReference<CompletionStage<Result<ArrayList<Integer>>>>();
        Thread first = new Thread(() -> {
            var mutable = body(7);
            firstStage.set(fixture.session.request(REQUEST, mutable));
            mutable.set(0, 99);
        });
        Thread second = new Thread(() -> secondStage.set(fixture.session.request(REQUEST, body(8))));
        first.start();
        second.start();
        first.join();
        second.join();
        assertThat(fixture.sent).isEmpty();
        fixture.main.drain();
        var packets = fixture.sent.stream().map(PktControllerUiRequestPayload.class::cast).toList();
        assertThat(packets).extracting(PktControllerUiRequestPayload::requestId).containsExactly(1L, 2L);
        assertThat(packets.stream().map(packet -> decode(packet.body()).getFirst())).containsExactlyInAnyOrder(7, 8);
        var completedOn = new AtomicReference<Thread>();
        firstStage.get().thenAccept(value -> completedOn.set(Thread.currentThread()));
        long firstRequestId = packets.stream().filter(packet -> decode(packet.body()).getFirst() == 7)
                .findFirst().orElseThrow().requestId();
        fixture.session.handle(new PktControllerUiResponsePayload(fixture.menu.containerId, fixture.session.id(), firstRequestId,
                Status.SUCCESS, Optional.empty(), encode(40)));
        assertThat(completedOn.get()).isSameAs(Thread.currentThread());
        assertThat(result(firstStage.get()).value().orElseThrow()).containsExactly(40);
        assertThat(secondStage.get().toCompletableFuture()).isNotDone();
    }

    @Test
    void queued_requests_cannot_send_after_switch_and_close_never_closes_replacement_menu() {
        var fixture = new Fixture();
        fixture.ready(1);
        var request = fixture.session.request(REQUEST, body(2));
        fixture.session.close();
        fixture.current.set(new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL)));
        fixture.main.drain();
        assertThat(result(request).status()).isEqualTo(Status.CLOSED);
        assertThat(fixture.sent).isEmpty();
        assertThat(fixture.menuCloseCalls).hasValue(0);
        assertThat(fixture.session.isOpen()).isFalse();
    }

    @Test
    void matching_close_is_idempotent_and_does_not_leave_pending_or_ready_cache() {
        var fixture = new Fixture();
        fixture.ready(1);
        var request = fixture.session.request(REQUEST, body(2));
        fixture.main.drain();
        fixture.session.close();
        fixture.session.close();
        fixture.main.drain();
        assertThat(result(request).status()).isEqualTo(Status.CLOSED);
        assertThat(fixture.menuCloseCalls).hasValue(1);
        assertThat(fixture.session.snapshot().ready()).isFalse();
    }

    @Test
    void full_and_progress_validate_token_baseline_structure_and_strict_monotonic_revision() {
        var fixture = new Fixture();
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 2, 1, List.of()));
        assertThat(fixture.session.snapshot().ready()).isFalse();
        fixture.ready(1);
        var baseline = fixture.session.snapshot();
        fixture.session.handle(new PktControllerUiProgressPayload(3, UUID.randomUUID(), 2, 1, progress(2)));
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 100, 99, progress(2)));
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 2, 1,
                List.of(new LaneProgress("base", ResourceLocation.parse("test:wrong"), 2, 10, 1))));
        assertThat(fixture.session.snapshot()).isSameAs(baseline);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 3, 1, progress(3)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(3);
        assertThat(fixture.session.snapshot().lanes().getFirst().tick()).isEqualTo(3);
        assertThat(baseline.lanes().getFirst().tick()).isEqualTo(0);
        fixture.ready(2);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 2, 1, progress(2)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(3);
        fixture.ready(4);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 5, 1, progress(5)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(4);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 5, 4, progress(5)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(5);
    }

    @Test
    void same_opening_accepts_dynamic_normal_factory_full_and_progress_on_each_new_baseline() {
        var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL, false), true, false);
        assertThat(fixture.session.snapshot().formed()).isFalse();
        var opening = (ControllerUiSnapshotData) fixture.session.snapshot();
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, fixture.session.id(), 1,
                new ControllerUiSnapshotData(fixture.session.id(), 1, true, opening.dimension(), opening.controllerPos(),
                        opening.header(), fixture.snapshot(1).laneData())));
        assertThat(fixture.session.snapshot().formed()).isFalse();
        fixture.full(2, ControllerUiSnapshot.Kind.FACTORY);
        assertThat(fixture.session.snapshot().ready()).isTrue();
        assertThat(fixture.session.snapshot().formed()).isTrue();
        assertThat(fixture.session.snapshot().kind()).isEqualTo(ControllerUiSnapshot.Kind.FACTORY);
        assertThat(fixture.session.snapshot().lanes()).extracting(ControllerUiSnapshot.Lane::id).containsExactly("base", "factory");
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 3, 1, progress(3)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(2);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 3, 2, progress(3)));
        assertThat(fixture.session.snapshot().lanes().getFirst().tick()).isEqualTo(3);
        fixture.full(4, ControllerUiSnapshot.Kind.NORMAL);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 5, 4, progress(5)));
        assertThat(fixture.session.snapshot().kind()).isEqualTo(ControllerUiSnapshot.Kind.NORMAL);
        assertThat(fixture.session.snapshot().revision()).isEqualTo(5);
        assertThat(fixture.session.snapshot().lanes().getFirst().tick()).isEqualTo(5);
        assertThat(fixture.session.snapshot().lanes()).extracting(ControllerUiSnapshot.Lane::id).containsExactly("base");
    }

    @Test
    void first_full_can_already_be_factory_but_dynamic_kind_does_not_relax_identity_role_or_revision() {
        var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL, false), true, false);
        fixture.full(1, ControllerUiSnapshot.Kind.FACTORY);
        var baseline = fixture.session.snapshot();
        var next = fixture.snapshot(2, ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.HOST, MACHINE);
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, fixture.session.id(), 2, next));
        next = fixture.snapshot(2, ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL, ResourceLocation.parse("test:other"));
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, fixture.session.id(), 2, next));
        next = fixture.snapshot(2);
        fixture.session.handle(new PktControllerUiSnapshotPayload(4, fixture.session.id(), 2, next));
        UUID oldToken = UUID.randomUUID();
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, oldToken, 2,
                new ControllerUiSnapshotData(oldToken, 2, true, next.dimension(), next.controllerPos(), next.header(), next.laneData())));
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, fixture.session.id(), 2,
                new ControllerUiSnapshotData(fixture.session.id(), 2, true, Level.NETHER, next.controllerPos(), next.header(), next.laneData())));
        fixture.session.handle(new PktControllerUiSnapshotPayload(3, fixture.session.id(), 2,
                new ControllerUiSnapshotData(fixture.session.id(), 2, true, next.dimension(), BlockPos.ZERO, next.header(), next.laneData())));
        fixture.full(1, ControllerUiSnapshot.Kind.NORMAL);
        assertThat(fixture.session.snapshot()).isSameAs(baseline);
        fixture.session.handle(new PktControllerUiProgressPayload(3, fixture.session.id(), 2, 1, progress(2)));
        assertThat(fixture.session.snapshot().revision()).isEqualTo(2);
        assertThat(fixture.session.snapshot().kind()).isEqualTo(ControllerUiSnapshot.Kind.FACTORY);
    }

    @Test
    void construction_request_before_installation_is_not_ready_without_ending_immediate_or_deferred_opening() {
        for (boolean immediate : List.of(true, false)) {
            var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL), false, immediate);
            var request = fixture.session.slots().duringScreenConstruction(() -> fixture.session.request(REQUEST, body(1)));
            fixture.main.drain(); // Deliberately still before the native installation.
            assertThat(result(request).status()).isEqualTo(Status.INVALID_REQUEST);
            assertThat(fixture.sent).isEmpty();
            assertThat(fixture.session.isOpen()).isTrue();
            fixture.current.set(fixture.menu);
            fixture.ready(1);
            assertThat(fixture.session.snapshot().ready()).isTrue();
            fixture.session.request(REQUEST, body(2));
            fixture.main.drain();
            assertThat(fixture.sent).hasSize(1);
        }
    }

    @Test
    void deferred_construction_request_can_run_after_installation_but_old_opening_cannot_revive() {
        var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL), false, false);
        var request = fixture.session.slots().duringScreenConstruction(() -> fixture.session.request(REQUEST, body(1)));
        fixture.current.set(fixture.menu);
        fixture.ready(1);
        fixture.main.drain();
        assertThat(fixture.sent).hasSize(1);
        assertThat(request.toCompletableFuture()).isNotDone();

        var old = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL), false, false);
        var stale = old.session.slots().duringScreenConstruction(() -> old.session.request(REQUEST, body(1)));
        old.current.set(new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL)));
        old.main.drain();
        assertThat(result(stale).status()).isEqualTo(Status.CLOSED);
        old.current.set(old.menu);
        old.ready(1);
        assertThat(old.session.isOpen()).isFalse();
        assertThat(old.session.snapshot().ready()).isFalse();
        assertThat(old.sent).isEmpty();
    }

    @Test
    void unscoped_preinstallation_request_is_not_a_general_stale_menu_permission() {
        var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL), false, true);
        var request = fixture.session.request(REQUEST, body(1));
        assertThat(result(request).status()).isEqualTo(Status.CLOSED);
        assertThat(fixture.session.isOpen()).isFalse();
        assertThat(fixture.sent).isEmpty();
    }

    @Test
    void native_invalidation_of_pending_opening_prevents_deferred_request_and_late_installation_from_reviving_it() {
        var fixture = new Fixture(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL), false, false);
        var request = fixture.session.slots().duringScreenConstruction(() -> fixture.session.request(REQUEST, body(1)));
        var sessions = new IdentityHashMap<AbstractContainerMenu, ControllerUiClientSession>();
        sessions.put(fixture.menu, fixture.session);
        ControllerUiClientEvents.observeMenu(new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL)),
                sessions, fixture.current);
        fixture.current.set(fixture.menu);
        fixture.ready(1);
        fixture.main.drain();
        assertThat(result(request).status()).isEqualTo(Status.CLOSED);
        assertThat(fixture.session.isOpen()).isFalse();
        assertThat(fixture.session.snapshot().ready()).isFalse();
        assertThat(fixture.sent).isEmpty();
    }

    @Test
    void response_only_completes_future_and_timeout_late_response_never_overwrites_snapshot() {
        var fixture = new Fixture();
        fixture.ready(1);
        var snapshot = fixture.session.snapshot();
        var request = fixture.session.request(REQUEST, body(1));
        fixture.main.drain();
        fixture.session.handle(new PktControllerUiResponsePayload(3, fixture.session.id(), 1,
                Status.SUCCESS, Optional.empty(), encode(6)));
        assertThat(result(request).value().orElseThrow()).containsExactly(6);
        assertThat(fixture.session.snapshot()).isSameAs(snapshot);
        var timeout = fixture.session.request(REQUEST, body(2));
        fixture.main.drain();
        fixture.now.set(Duration.ofSeconds(10).toNanos());
        fixture.session.tick();
        fixture.session.handle(new PktControllerUiResponsePayload(3, fixture.session.id(), 2,
                Status.SUCCESS, Optional.empty(), encode(7)));
        assertThat(result(timeout).status()).isEqualTo(Status.TIMEOUT);
        assertThat(fixture.session.snapshot()).isSameAs(snapshot);
    }

    @Test
    void subscription_cancellation_and_session_closure_drop_already_queued_callbacks() {
        var fixture = new Fixture();
        var callbacks = new QueueExecutor();
        var updates = new ArrayList<Long>();
        var subscription = fixture.session.subscribe(callbacks, value -> updates.add(value.revision()));
        fixture.main.drain();
        fixture.ready(1);
        subscription.close();
        callbacks.drain();
        fixture.main.drain();
        assertThat(updates).isEmpty();
        fixture.session.subscribe(callbacks, value -> updates.add(value.revision()));
        fixture.main.drain();
        fixture.session.invalidate();
        callbacks.drain();
        fixture.main.drain();
        assertThat(updates).isEmpty();
    }

    @Test
    void direct_published_menu_change_without_tick_invalidate_or_hook_drops_queued_snapshot_and_state_on_worker() throws InterruptedException {
        for (boolean closedToInventory : List.of(false, true)) {
            var fixture = new Fixture();
            var callbacks = new QueueExecutor();
            var calls = new AtomicInteger();
            var failure = new AtomicReference<Throwable>();
            fixture.session.subscribe(callbacks, ignored -> calls.incrementAndGet());
            fixture.session.subscribe(STATE, callbacks, ignored -> calls.incrementAndGet());
            fixture.main.drain();
            fixture.ready(1);
            fixture.session.handle(new PktControllerUiCustomStatePayload(3, fixture.session.id(), STATE.id(), 1, 1, encode(7)));
            fixture.current.set(closedToInventory ? null : new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL)));
            // No tick, invalidate, or menuChanged: only the safely published fixture source changes.
            assertThat(fixture.session.isOpen()).isTrue();
            Thread worker = new Thread(() -> {
                try { callbacks.drain(); }
                catch (Throwable thrown) { failure.set(thrown); }
            });
            worker.start();
            worker.join();
            assertThat(failure.get()).isNull();
            assertThat(calls).hasValue(0);
            fixture.session.subscribe(callbacks, ignored -> calls.incrementAndGet());
            fixture.session.subscribe(STATE, callbacks, ignored -> calls.incrementAndGet());
            fixture.main.drain(); // Registration must not offer old current values either.
            callbacks.drain();
            assertThat(calls).hasValue(0);
            assertThat(fixture.session.isOpen()).isFalse();
        }
    }

    @Test
    void native_lifecycle_observation_invalidates_before_callbacks_preserves_same_menu_and_logs_out_once() {
        var fixture = new Fixture();
        var callbacks = new QueueExecutor();
        var updates = new ArrayList<Long>();
        var closes = new AtomicInteger();
        var sessions = new IdentityHashMap<AbstractContainerMenu, ControllerUiClientSession>();
        sessions.put(fixture.menu, fixture.session);
        fixture.session.subscribe(callbacks, value -> updates.add(value.revision()));
        fixture.session.onClosed(callbacks, closes::incrementAndGet);
        fixture.main.drain();
        fixture.ready(1);
        // Same native menu during temporary page / resize / resource reload.
        ControllerUiClientEvents.observeMenu(fixture.menu, sessions, fixture.current);
        callbacks.drain();
        assertThat(updates).containsExactly(1L);
        assertThat(fixture.session.isOpen()).isTrue();
        fixture.ready(2);
        ControllerUiClientEvents.observeMenu(null, sessions, fixture.current);
        assertThat(fixture.session.isOpen()).isFalse();
        assertThat(sessions).isEmpty();
        ControllerUiClientEvents.observeMenu(null, sessions, fixture.current);
        callbacks.drain();
        assertThat(updates).containsExactly(1L);
        assertThat(closes).hasValue(1);
        assertThat(fixture.menuCloseCalls).hasValue(0);
    }

    @Test
    void native_replacement_publishes_before_closed_authors_and_reentrant_close_supersedes_outer_observation() {
        var old = new Fixture();
        var replacement = new Fixture();
        var sessions = new LinkedHashMap<AbstractContainerMenu, ControllerUiClientSession>();
        sessions.put(old.menu, old.session);
        sessions.put(replacement.menu, replacement.session);
        var closes = new AtomicInteger();
        old.session.onClosed(Runnable::run, () -> {
            assertThat(old.current.get()).isSameAs(replacement.menu);
            closes.incrementAndGet();
            ControllerUiClientEvents.observeMenu(null, sessions, old.current);
        });
        old.main.drain();
        ControllerUiClientEvents.observeMenu(replacement.menu, sessions, old.current);
        assertThat(old.current.get()).isNull();
        assertThat(old.session.isOpen()).isFalse();
        assertThat(replacement.session.isOpen()).isFalse();
        assertThat(sessions).isEmpty();
        assertThat(closes).hasValue(1);
    }

    @Test
    void arbitrary_executor_can_never_deliver_old_snapshot_after_new_and_reentrant_publish_is_ordered() {
        var fixture = new Fixture();
        var callbacks = new QueueExecutor();
        var updates = new ArrayList<Long>();
        fixture.session.subscribe(callbacks, value -> updates.add(value.revision()));
        fixture.main.drain();
        fixture.ready(1);
        fixture.ready(2);
        // There is only one runnable for this subscriber even on a parallel/reordering executor.
        callbacks.runLast();
        callbacks.drain();
        assertThat(updates).containsExactly(2L);
        var immediate = new ArrayList<Long>();
        fixture.session.subscribe(Runnable::run, value -> {
            if (value.revision() == 3) fixture.ready(4);
        });
        fixture.session.subscribe(Runnable::run, value -> immediate.add(value.revision()));
        fixture.main.drain();
        fixture.ready(3);
        assertThat(immediate).containsExactly(2L, 4L);
    }

    @Test
    void custom_state_bytes_are_validated_and_decoded_independently_for_each_read_and_subscriber() {
        var fixture = new Fixture();
        var firstCallbacks = new QueueExecutor();
        var secondCallbacks = new QueueExecutor();
        var secondValues = new ArrayList<Integer>();
        fixture.session.subscribe(STATE, firstCallbacks, value -> value.set(0, 99));
        fixture.session.subscribe(STATE, secondCallbacks, value -> secondValues.add(value.getFirst()));
        fixture.main.drain();
        byte[] bytes = encode(7);
        var packet = new PktControllerUiCustomStatePayload(3, fixture.session.id(), STATE.id(), 1, 1, bytes);
        bytes[0] = 100;
        fixture.session.handle(packet);
        firstCallbacks.drain();
        secondCallbacks.drain();
        assertThat(secondValues).containsExactly(7);
        fixture.session.state(STATE).orElseThrow().set(0, 80);
        assertThat(fixture.session.state(STATE).orElseThrow()).containsExactly(7);
        fixture.session.handle(new PktControllerUiCustomStatePayload(3, fixture.session.id(), STATE.id(), 1, 1, encode(8)));
        fixture.session.handle(new PktControllerUiCustomStatePayload(3, fixture.session.id(), STATE.id(), 1, 2, new byte[]{1, 2}));
        assertThat(fixture.session.state(STATE).orElseThrow()).containsExactly(7);
        fixture.session.handle(new PktControllerUiCustomStatePayload(3, fixture.session.id(), STATE.id(), 1, 3, encode(9)));
        var latest = new ArrayList<Integer>();
        fixture.session.subscribe(STATE, secondCallbacks, value -> latest.add(value.getFirst()));
        fixture.main.drain();
        secondCallbacks.drain();
        assertThat(latest).containsExactly(9);
        fixture.session.invalidate();
        assertThat(fixture.session.state(STATE)).isEmpty();
    }

    @Test
    void in_flight_ui_callback_never_holds_a_lock_needed_by_main_thread_publication_or_cleanup() throws Exception {
        var fixture = new Fixture();
        var queued = new QueueExecutor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var workerFailure = new AtomicReference<Throwable>();
        fixture.session.subscribe(queued, value -> {
            calls.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Main-thread cleanup blocked on author code");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        });
        fixture.main.drain();
        Thread worker = new Thread(() -> {
            try { queued.drain(); }
            catch (Throwable failure) { workerFailure.set(failure); }
        });
        worker.start();
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            fixture.ready(1);
            fixture.session.invalidate();
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertThat(worker.isAlive()).isFalse();
        assertThat(workerFailure.get()).isNull();
        assertThat(calls).hasValue(1);
    }

    @Test
    void on_closed_reentrant_menu_switch_cannot_close_the_new_menu() {
        var fixture = new Fixture();
        fixture.session.onClosed(Runnable::run, () -> fixture.current.set(
                new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL))));
        fixture.main.drain();
        fixture.session.close();
        fixture.main.drain();
        assertThat(fixture.menuCloseCalls).hasValue(0);
    }

    @Test
    void menu_identity_tick_and_logout_invalidation_emit_closed_once_and_cancelled_closed_listener_never_runs() {
        var fixture = new Fixture();
        var callbacks = new QueueExecutor();
        var closes = new AtomicInteger();
        var cancelled = fixture.session.onClosed(callbacks, () -> { throw new AssertionError("cancelled close"); });
        fixture.session.onClosed(callbacks, closes::incrementAndGet);
        fixture.main.drain();
        fixture.session.tick(); // Screen changes are irrelevant while the native menu is unchanged.
        assertThat(fixture.session.isOpen()).isTrue();
        fixture.current.set(new TestMenu(metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL)));
        fixture.session.tick();
        fixture.session.invalidate();
        fixture.session.close();
        fixture.main.drain();
        cancelled.close();
        callbacks.drain();
        assertThat(closes).hasValue(1);
        assertThat(fixture.menuCloseCalls).hasValue(0);
        fixture.session.onClosed(callbacks, closes::incrementAndGet);
        fixture.main.drain();
        callbacks.drain();
        assertThat(closes).hasValue(2);
    }

    @Test
    void reopen_same_container_id_rejects_old_tokens_before_custom_decoder_and_keeps_new_pending() {
        var old = new Fixture();
        old.ready(1);
        var reopened = new Fixture();
        reopened.ready(1);
        var pending = reopened.session.request(REQUEST, body(2));
        reopened.main.drain();
        reopened.session.handle(new PktControllerUiSnapshotPayload(3, old.session.id(), 2, old.snapshot(2)));
        reopened.session.handle(new PktControllerUiResponsePayload(3, old.session.id(), 1, Status.SUCCESS, Optional.empty(), encode(9)));
        reopened.session.handle(new PktControllerUiCustomStatePayload(3, old.session.id(), STATE.id(), 1, 1, new byte[]{(byte) 0x80}));
        assertThat(reopened.session.snapshot().revision()).isEqualTo(1);
        assertThat(reopened.session.state(STATE)).isEmpty();
        assertThat(pending.toCompletableFuture()).isNotDone();
        reopened.session.handle(new PktControllerUiResponsePayload(3, reopened.session.id(), 1, Status.SUCCESS, Optional.empty(), encode(8)));
        assertThat(result(pending).value().orElseThrow()).containsExactly(8);
    }

    @Test
    void supports_requires_metadata_and_local_descriptor_and_slots_guard_thread_token_and_closed_state() throws InterruptedException {
        var fixture = new Fixture();
        assertThat(fixture.session.supports(REQUEST)).isTrue();
        assertThat(fixture.session.supports(STATE)).isTrue();
        var missing = new RequestType<>(ResourceLocation.parse("test:missing"), 1, CODEC, CODEC);
        var wrongVersion = new RequestType<>(REQUEST.id(), 2, CODEC, CODEC);
        var differentCodec = StreamCodec.<RegistryFriendlyByteBuf, ArrayList<Integer>>of(
                (buffer, value) -> buffer.writeVarInt(value.getFirst()), buffer -> body(buffer.readVarInt()));
        assertThat(fixture.session.supports(missing)).isFalse();
        assertThat(fixture.session.supports(wrongVersion)).isFalse();
        assertThat(fixture.session.supports(new StateType<>(STATE.id(), 1, differentCodec))).isFalse();
        var unsupported = fixture.session.request(missing, body(1));
        fixture.main.drain();
        assertThat(result(unsupported).status()).isEqualTo(Status.UNSUPPORTED);
        fixture.session.slots().setPlayerInventoryVisible(false);
        assertThat(fixture.menu.visible).isFalse();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try { fixture.session.slots().setPlayerInventoryVisible(true); }
            catch (Throwable thrown) { failure.set(thrown); }
        });
        thread.start();
        thread.join();
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
        fixture.menu.metadata = metadata(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL);
        fixture.session.slots().setPlayerInventoryVisible(true);
        assertThat(fixture.menu.visible).isFalse();
        fixture.session.tick();
        fixture.session.slots().setPlayerInventoryVisible(true);
        assertThat(fixture.menu.visible).isFalse();
    }

    @Test
    void facade_delegates_typed_requests_states_snapshots_slots_and_cancellation() {
        var fixture = new Fixture();
        fixture.ready(1);
        var facade = UiClientAdapters.wrap(fixture.session);
        assertThat(facade.id()).isEqualTo(fixture.session.id());
        assertThat(facade.snapshot().machineId()).isEqualTo(MACHINE);
        assertThat(facade.supports(UiProtocolAdapters.wrap(REQUEST))).isTrue();
        facade.slots().setPlayerInventoryVisible(false);
        assertThat(fixture.menu.visible).isFalse();
        var result = facade.request(UiProtocolAdapters.wrap(REQUEST), "base", body(4));
        result.toCompletableFuture().cancel(false);
        fixture.main.drain();
        fixture.session.handle(new PktControllerUiResponsePayload(3, facade.id(), 1, Status.SUCCESS, Optional.empty(), encode(6)));
        assertThat(result.toCompletableFuture().join().value().orElseThrow()).containsExactly(6);
        fixture.session.handle(new PktControllerUiCustomStatePayload(3, facade.id(), STATE.id(), 1, 1, encode(5)));
        facade.state(UiProtocolAdapters.wrap(STATE)).orElseThrow().set(0, 99);
        assertThat(facade.state(UiProtocolAdapters.wrap(STATE)).orElseThrow()).containsExactly(5);
        var callbacks = new QueueExecutor();
        var calls = new AtomicInteger();
        var subscription = facade.subscribe(callbacks, ignored -> calls.incrementAndGet());
        fixture.main.drain();
        subscription.close();
        callbacks.drain();
        assertThat(calls).hasValue(0);
    }

    @Test
    void factory_registry_copies_machine_ids_rejects_duplicates_unknown_and_late_without_overwrite() {
        var ids = new ArrayList<>(List.of(MACHINE));
        var registry = new ControllerUiRegistration(ids);
        ids.clear();
        ControllerUiRegistration.Factory factory = context -> null;
        registry.register(MACHINE, factory);
        assertThatThrownBy(() -> registry.register(MACHINE, context -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> registry.register(ResourceLocation.parse("test:unknown"), factory)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.find(MACHINE)).containsSame(factory);
        registry.freeze();
        registry.freeze();
        assertThatThrownBy(() -> registry.register(MACHINE, factory)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.find(MACHINE)).containsSame(factory);
    }

    private static ArrayList<Integer> body(int value) { return new ArrayList<>(List.of(value)); }
    private static byte[] encode(int value) {
        return ControllerUiPayloadCodec.encodeBounded(CODEC, body(value), RegistryAccess.EMPTY, ControllerUiPayloadCodec.STATE_LIMIT);
    }
    private static ArrayList<Integer> decode(byte[] bytes) {
        return ControllerUiPayloadCodec.decodeExact(CODEC, bytes, RegistryAccess.EMPTY, ControllerUiPayloadCodec.STATE_LIMIT);
    }
    private static <R> Result<R> result(CompletionStage<Result<R>> stage) { return stage.toCompletableFuture().join(); }
    private static List<LaneProgress> progress(int tick) { return List.of(new LaneProgress("base", RECIPE, tick, 10, 1)); }
    private static ControllerMenuOpenData metadata(ControllerUiSnapshot.Kind kind, ControllerUiSnapshot.Role role) {
        return metadata(kind, role, true);
    }
    private static ControllerMenuOpenData metadata(ControllerUiSnapshot.Kind kind, ControllerUiSnapshot.Role role, boolean formed) {
        return new ControllerMenuOpenData(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(4, 5, 6), MACHINE,
                kind, role, formed, 2, Optional.empty(), List.of(
                new ControllerMenuOpenData.Capability(REQUEST.id(), 1, false),
                new ControllerMenuOpenData.Capability(STATE.id(), 1, true)));
    }

    /** Injectable real session wiring, not a fake transport protocol.
     * @author howxu <dev@howxu.cn> */
    private static final class Fixture {
        private final Thread mainThread = Thread.currentThread();
        private final QueueExecutor main = new QueueExecutor();
        private final AtomicLong now = new AtomicLong();
        private final List<CustomPacketPayload> sent = new ArrayList<>();
        private final AtomicInteger menuCloseCalls = new AtomicInteger();
        private final MutableComponent title = Component.translatable("test:name");
        private final TestMenu menu;
        private final AtomicReference<AbstractContainerMenu> current;
        private final ControllerUiClientSession session;

        private Fixture() { this(ControllerUiSnapshot.Kind.NORMAL, ControllerUiSnapshot.Role.NORMAL); }
        private Fixture(ControllerUiSnapshot.Kind kind, ControllerUiSnapshot.Role role) {
            this(metadata(kind, role), true, false);
        }
        private Fixture(ControllerMenuOpenData opening, boolean preinstalled, boolean immediate) {
            menu = new TestMenu(opening);
            current = new AtomicReference<>(preinstalled ? menu : new TestMenu(metadata(opening.kind(), opening.role())));
            var protocols = new UiProtocolRegistration(List.of(MACHINE));
            protocols.request(MACHINE, REQUEST, (context, value) -> Result.success(value));
            protocols.state(MACHINE, STATE, new UiProtocolRegistration.Provider<>() {
                @Override public long revision(UiProtocolRegistration.ServerContext context) { return 0; }
                @Override public ArrayList<Integer> snapshot(UiProtocolRegistration.ServerContext context) { return body(0); }
            });
            protocols.freeze();
            session = new ControllerUiClientSession(menu, menu.metadata, title, RegistryAccess.EMPTY, protocols,
                    immediate ? Runnable::run : main, now::get, sent::add, () -> Thread.currentThread() == mainThread,
                    () -> {
                        if (Thread.currentThread() != mainThread) throw new AssertionError("Live current-menu getter called off main thread");
                        return current.get();
                    }, menuCloseCalls::incrementAndGet, current::get);
        }

        private void ready(long revision) {
            session.handle(new PktControllerUiSnapshotPayload(3, session.id(), revision, snapshot(revision)));
        }
        private ControllerUiSnapshotData snapshot(long revision) {
            var opening = (ControllerUiSnapshotData) session.snapshot();
            return snapshot(revision, opening.kind(), opening.role(), opening.machineId());
        }
        private void full(long revision, ControllerUiSnapshot.Kind kind) {
            session.handle(new PktControllerUiSnapshotPayload(3, session.id(), revision,
                    snapshot(revision, kind, menu.metadata.role(), MACHINE)));
        }
        private ControllerUiSnapshotData snapshot(long revision, ControllerUiSnapshot.Kind kind,
                                                  ControllerUiSnapshot.Role role, ResourceLocation machine) {
            var header = new ControllerUiSnapshotData.HeaderData(machine, kind, role, title,
                    true, false, false, 2, null, 0, 1, List.of(), 0, 1, 0, 0, List.of(), null, null, false, Map.of(), List.of());
            var lanes = new ArrayList<ControllerUiSnapshotData.LaneData>();
            if (kind != ControllerUiSnapshot.Kind.TICK) {
                lanes.add(new ControllerUiSnapshotData.LaneData("base", 0, true, true, true, RECIPE, 0, 10, 1,
                        null, List.of(), new ControllerUiSnapshotData.RecipeData(List.of(), 0, 0, 0, 10, 1)));
            }
            if (kind == ControllerUiSnapshot.Kind.FACTORY) {
                lanes.add(new ControllerUiSnapshotData.LaneData("factory", 1, false, false, true, RECIPE, 0, 10, 1,
                        null, List.of(), new ControllerUiSnapshotData.RecipeData(List.of(), 0, 0, 0, 10, 1)));
            }
            return new ControllerUiSnapshotData(session.id(), revision, true, Level.OVERWORLD, menu.metadata.pos(), header, lanes);
        }
    }

    /** Only metadata and the native menu boolean are needed for pure client lifecycle tests.
     * @author howxu <dev@howxu.cn> */
    private static final class TestMenu extends AbstractContainerMenu implements ControllerUiMenu {
        private ControllerMenuOpenData metadata;
        private boolean visible = true;
        private TestMenu(ControllerMenuOpenData metadata) { super(null, 3); this.metadata = metadata; }
        @Override public ControllerMenuOpenData uiOpenData() { return metadata; }
        @Override public ControllerUiServerSession uiServerSession() { return null; }
        @Override public boolean playerInventoryVisible() { return visible; }
        @Override public void setPlayerInventoryVisible(boolean value) { visible = value; }
        @Override public ItemStack quickMoveStack(Player player, int index) { throw new UnsupportedOperationException(); }
        @Override public boolean stillValid(Player player) { return true; }
    }

    /** Thread-safe enqueue with deliberately selectable executor delivery order.
     * @author howxu <dev@howxu.cn> */
    private static final class QueueExecutor implements Executor {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        @Override public synchronized void execute(Runnable runnable) { queue.addLast(runnable); }
        private void drain() {
            while (true) {
                Runnable task;
                synchronized (this) { task = queue.pollFirst(); }
                if (task == null) return;
                task.run();
            }
        }
        private void runLast() {
            Runnable task;
            synchronized (this) { task = queue.removeLast(); }
            task.run();
        }
    }
}

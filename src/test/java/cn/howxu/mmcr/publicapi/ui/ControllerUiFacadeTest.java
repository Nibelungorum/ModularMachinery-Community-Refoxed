package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiOpenContext;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.client.controller.ui.ControllerUiClientSession;
import cn.howxu.mmcr.internal.api.facade.client.UiClientAdapters;
import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiRequestPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Public factory arguments and lifecycle use the same native menu and actual core session.
 * @author howxu <dev@howxu.cn> */
class ControllerUiFacadeTest {
    private static final Identifier MACHINE = Identifier.parse("example:controller");
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), RegistryFriendlyByteBuf::readVarInt);
    private static final UiRequestType<Integer, Integer> REQUEST = UiRequestType.of(
            Identifier.parse("example:set_mode"), 1, CODEC, CODEC);

    @BeforeAll
    static void bootstrapNativeMenus() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void factory_context_keeps_native_identity_and_owns_title_without_creating_another_session() {
        var fixture = new Fixture();
        var inventory = new Inventory(null, null);
        var title = Component.translatable("gui.mmcr.ui.loading");
        var expected = title.copy();
        var coreContext = UiClientAdapters.context(fixture.menu, inventory, title, fixture.core);
        var observed = new AtomicReference<ControllerUiOpenContext>();
        var factory = UiClientAdapters.unwrap(context -> { observed.set(context); return null; });
        title.append(Component.translatable("gui.mmcr.ui.closed"));
        factory.create(coreContext);
        var context = observed.get();
        assertThat(context.menu()).isSameAs(fixture.menu);
        assertThat(context.inventory()).isSameAs(inventory);
        assertThat(context.session().id()).isEqualTo(fixture.core.id());
        assertThat(context.session().snapshot().ready()).isFalse();
        ((MutableComponent) context.title()).append(Component.translatable("gui.mmcr.ui.closed"));
        assertThat(context.title()).isEqualTo(expected);
        context.session().slots().setPlayerInventoryVisible(false);
        assertThat(fixture.menu.visible).isFalse();
        assertThat(fixture.sent).isEmpty();
    }

    @Test
    void public_close_completes_pending_and_cancels_queued_subscriptions_without_closing_a_new_menu() {
        var fixture = new Fixture();
        var facade = UiClientAdapters.wrap(fixture.core);
        var opening = (ControllerUiSnapshotData) fixture.core.snapshot();
        fixture.core.handle(new PktControllerUiSnapshotPayload(6, facade.id(), 1,
                new ControllerUiSnapshotData(facade.id(), 1, true, Level.OVERWORLD, BlockPos.ZERO,
                        opening.header(), List.of())));
        var callbacks = new ArrayDeque<Runnable>();
        var snapshots = new AtomicInteger();
        var closed = new AtomicInteger();
        var subscription = facade.subscribe(callbacks::addLast, ignored -> snapshots.incrementAndGet());
        facade.onClosed(callbacks::addLast, closed::incrementAndGet);
        var pending = facade.request(REQUEST, 2);
        fixture.drain();
        assertThat(fixture.sent).hasSize(1);
        assertThat(fixture.sent.getFirst().sessionId()).isEqualTo(facade.id());
        fixture.current.set(new TestMenu(fixture.menu.metadata));
        subscription.close();
        facade.close();
        fixture.drain();
        while (!callbacks.isEmpty()) callbacks.removeFirst().run();
        assertThat(pending.toCompletableFuture().join().status()).isEqualTo(UiResult.Status.CLOSED);
        assertThat(facade.isOpen()).isFalse();
        assertThat(facade.snapshot().ready()).isFalse();
        assertThat(snapshots).hasValue(0);
        assertThat(closed).hasValue(1);
        assertThat(fixture.nativeCloses).hasValue(0);
    }

    /** Injectable wiring around the real client implementation.
     * @author howxu <dev@howxu.cn> */
    private static final class Fixture {
        private final ArrayDeque<Runnable> main = new ArrayDeque<>();
        private final List<PktControllerUiRequestPayload> sent = new ArrayList<>();
        private final AtomicInteger nativeCloses = new AtomicInteger();
        private final TestMenu menu = new TestMenu(new ControllerMenuOpenData(UUID.randomUUID(), Level.OVERWORLD,
                BlockPos.ZERO, MACHINE, Kind.TICK, Role.NORMAL, false, 0, Optional.empty(),
                List.of(new ControllerMenuOpenData.Capability(REQUEST.id(), 1, false))));
        private final AtomicReference<AbstractContainerMenu> current = new AtomicReference<>(menu);
        private final ControllerUiClientSession core;
        private Fixture() {
            var protocols = new UiProtocolRegistration(List.of(MACHINE));
            protocols.request(MACHINE, UiProtocolAdapters.unwrap(REQUEST), (context, mode) -> UiProtocolRegistration.Result.success(mode));
            protocols.freeze();
            core = new ControllerUiClientSession(menu, menu.metadata, Component.translatable("gui.mmcr.ui.loading"),
                    RegistryAccess.EMPTY, protocols, main::addLast, () -> 0L,
                    packet -> sent.add((PktControllerUiRequestPayload) packet), () -> true, current::get, nativeCloses::incrementAndGet);
        }
        private void drain() { while (!main.isEmpty()) main.removeFirst().run(); }
    }

    /** Native menu identity only; inventory/items are covered by the runtime GameTest.
     * @author howxu <dev@howxu.cn> */
    private static final class TestMenu extends AbstractContainerMenu implements ControllerUiMenu {
        private final ControllerMenuOpenData metadata;
        private boolean visible = true;
        private TestMenu(ControllerMenuOpenData metadata) { super(null, 6); this.metadata = metadata; }
        @Override public ControllerMenuOpenData uiOpenData() { return metadata; }
        @Override public ControllerUiServerSession uiServerSession() { return null; }
        @Override public boolean playerInventoryVisible() { return visible; }
        @Override public void setPlayerInventoryVisible(boolean visible) { this.visible = visible; }
        @Override public ItemStack quickMoveStack(Player player, int index) { throw new UnsupportedOperationException(); }
        @Override public boolean stillValid(Player player) { return true; }
    }
}

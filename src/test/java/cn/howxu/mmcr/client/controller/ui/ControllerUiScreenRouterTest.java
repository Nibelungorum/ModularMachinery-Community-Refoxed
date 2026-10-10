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
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.publicapi.client.ui.ControllerUiFactory;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUisEvent;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Executes native ScreenConstructor.create without a running client or a ready snapshot.
 * @author howxu <dev@howxu.cn> */
class ControllerUiScreenRouterTest {
    private static final Identifier MACHINE = Identifier.parse("test:machine");
    private static final Identifier OTHER = Identifier.parse("test:other");
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt());
    private static final RequestType<Integer, Integer> REQUEST = new RequestType<>(Identifier.parse("test:request"), 1, CODEC, CODEC);

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void public_factory_routes_from_opening_metadata_for_unformed_normal_tick_and_factory() {
        for (var kind : ControllerUiSnapshot.Kind.values()) {
            for (boolean formed : List.of(false, true)) {
                var fixture = new Fixture(kind, formed);
                var selected = new AtomicReference<Screen>();
                fixture.registrations.register(MACHINE, context -> {
                    assertThat(context.menu()).isSameAs(fixture.menu);
                    assertThat(context.inventory()).isSameAs(fixture.inventory);
                    assertThat(context.title()).isEqualTo(fixture.title);
                    assertThat(context.session().id()).isEqualTo(fixture.menu.uiOpenData().sessionId());
                    assertThat(context.session().snapshot().machineId()).isEqualTo(MACHINE);
                    assertThat(context.session().snapshot().kind().name()).isEqualTo(kind.name());
                    assertThat(context.session().snapshot().formed()).isEqualTo(formed);
                    assertThat(context.session().snapshot().ready()).isFalse();
                    assertThat(fixture.current.get()).isNotSameAs(fixture.menu);
                    Screen screen = new FrameworkScreen(context.menu());
                    selected.set(screen);
                    return screen;
                });
                fixture.registrations.register(OTHER, context -> { throw new AssertionError("Wrong machine selected"); });
                UiClientAdapters.freeze(fixture.registrations);

                Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> nativeFactories = new HashMap<>();
                var nativeEvent = new RegisterMenuScreensEvent(nativeFactories);
                var type = new MenuType<TestMenu>((id, inventory) -> fixture.menu, FeatureFlags.VANILLA_SET);
                fixture.registerNative(nativeEvent, type);
                Screen screen = nativeCreate(nativeFactories, type, fixture);
                assertThat(screen).isSameAs(selected.get());
                assertThat(((MenuAccess<?>) screen).getMenu()).isSameAs(fixture.menu);
                assertThat(fixture.defaults).hasValue(0);
                assertThat(fixture.sessionCreations).hasValue(1);
            }
        }
    }

    @Test
    void absent_factory_uses_native_default_arguments_and_creates_same_menu_session_first() {
        var fixture = new Fixture(ControllerUiSnapshot.Kind.NORMAL, false);
        fixture.registrations.register(OTHER, context -> { throw new AssertionError("Wrong machine selected"); });
        UiClientAdapters.freeze(fixture.registrations);
        Screen screen = fixture.constructor().create(fixture.menu, fixture.inventory, fixture.title);
        assertThat(screen).isInstanceOf(DefaultScreen.class);
        var fallback = (DefaultScreen) screen;
        assertThat(fallback.getMenu()).isSameAs(fixture.menu);
        assertThat(fallback.inventory).isSameAs(fixture.inventory);
        assertThat(fallback.getTitle()).isSameAs(fixture.title);
        assertThat(fixture.defaults).hasValue(1);
        assertThat(fixture.sessionCreations).hasValue(1);
        assertThat(fixture.session.get().isOpen()).isTrue();
    }

    @Test
    void exception_null_non_menu_access_and_wrong_menu_fall_back_without_replacing_session() {
        for (ControllerUiFactory factory : List.<ControllerUiFactory>of(
                context -> { throw new IllegalStateException("Author factory failed"); },
                context -> null,
                context -> new PlainScreen(),
                context -> new FrameworkScreen(new TestMenu(ControllerUiSnapshot.Kind.NORMAL, true)))) {
            var fixture = new Fixture(ControllerUiSnapshot.Kind.FACTORY, true);
            fixture.registrations.register(MACHINE, context -> {
                context.session().slots().setPlayerInventoryVisible(false);
                assertThat(fixture.menu.playerInventoryVisible()).isFalse();
                return factory.create(context);
            });
            fixture.onDefault = slots -> assertThat(slots.isPlayerInventoryVisible()).isTrue();
            UiClientAdapters.freeze(fixture.registrations);
            Screen screen = fixture.createNative();
            assertThat(screen).isInstanceOf(DefaultScreen.class);
            assertThat(((MenuAccess<?>) screen).getMenu()).isSameAs(fixture.menu);
            assertThat(fixture.defaults).hasValue(1);
            assertThat(fixture.sessionCreations).hasValue(1);
            assertThat(fixture.session.get().id()).isEqualTo(fixture.menu.uiOpenData().sessionId());
            assertThat(fixture.session.get().isOpen()).isTrue();
            assertThat(fixture.menu.playerInventoryVisible()).isTrue();
            fixture.slots.get().setPlayerInventoryVisible(false);
            assertThat(fixture.menu.playerInventoryVisible()).isTrue();
        }
    }

    @Test
    void native_registration_create_path_preserves_each_menu_and_default_constructor() {
        var normal = new Fixture(ControllerUiSnapshot.Kind.NORMAL, true);
        var factory = new Fixture(ControllerUiSnapshot.Kind.FACTORY, true);
        UiClientAdapters.freeze(normal.registrations);
        UiClientAdapters.freeze(factory.registrations);
        Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> nativeFactories = new HashMap<>();
        var event = new RegisterMenuScreensEvent(nativeFactories);
        var normalType = new MenuType<TestMenu>((id, inventory) -> normal.menu, FeatureFlags.VANILLA_SET);
        var factoryType = new MenuType<TestMenu>((id, inventory) -> factory.menu, FeatureFlags.VANILLA_SET);
        normal.registerNative(event, normalType);
        factory.registerNative(event, factoryType);

        Screen normalScreen = nativeCreate(nativeFactories, normalType, normal);
        Screen factoryScreen = nativeCreate(nativeFactories, factoryType, factory);
        assertThat(((MenuAccess<?>) normalScreen).getMenu()).isSameAs(normal.menu);
        assertThat(((MenuAccess<?>) factoryScreen).getMenu()).isSameAs(factory.menu);
        assertThat(normal.defaults).hasValue(1);
        assertThat(factory.defaults).hasValue(1);
        assertThat(normal.session.get()).isNotSameAs(factory.session.get());
    }

    @Test
    void native_custom_construction_can_hide_before_installation_but_not_deferred_or_repeated_opening() {
        var fixture = new Fixture(ControllerUiSnapshot.Kind.NORMAL, false);
        var deferred = new AtomicReference<Runnable>();
        fixture.registrations.register(MACHINE, context -> {
            context.session().slots().setPlayerInventoryVisible(false);
            deferred.set(() -> context.session().slots().setPlayerInventoryVisible(true));
            return new FrameworkScreen(context.menu());
        });
        UiClientAdapters.freeze(fixture.registrations);
        Screen screen = fixture.createNative();
        assertThat(((MenuAccess<?>) screen).getMenu()).isSameAs(fixture.menu);
        assertThat(fixture.menu.playerInventoryVisible()).isFalse();
        assertThat(((ControllerUiMenu) fixture.current.get()).playerInventoryVisible()).isTrue();
        deferred.get().run();
        assertThat(fixture.menu.playerInventoryVisible()).isFalse();
        assertThatThrownBy(fixture::createNative).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("construction no longer belongs");

        fixture.current.set(fixture.menu);
        deferred.get().run();
        assertThat(fixture.menu.playerInventoryVisible()).isTrue();
        assertThat(fixture.createNative()).isInstanceOf(FrameworkScreen.class);
        assertThat(fixture.menu.playerInventoryVisible()).isFalse();
        assertThat(fixture.sessionCreations).hasValue(1);
    }

    @Test
    void native_default_constructor_runs_in_the_same_guarded_preinstallation_scope() {
        var fixture = new Fixture(ControllerUiSnapshot.Kind.FACTORY, false);
        fixture.onDefault = slots -> slots.setPlayerInventoryVisible(false);
        UiClientAdapters.freeze(fixture.registrations);
        Screen screen = fixture.createNative();
        assertThat(((MenuAccess<?>) screen).getMenu()).isSameAs(fixture.menu);
        assertThat(fixture.menu.playerInventoryVisible()).isFalse();
        assertThat(((ControllerUiMenu) fixture.current.get()).playerInventoryVisible()).isTrue();
        fixture.slots.get().setPlayerInventoryVisible(true);
        assertThat(fixture.menu.playerInventoryVisible()).isFalse();
    }

    @Test
    void native_default_failure_consumes_preinstallation_permission_in_finally() {
        var fixture = new Fixture(ControllerUiSnapshot.Kind.NORMAL, false);
        fixture.onDefault = slots -> { throw new IllegalStateException("Default construction failed"); };
        UiClientAdapters.freeze(fixture.registrations);
        assertThatThrownBy(fixture::createNative).isInstanceOf(IllegalStateException.class)
                .hasMessage("Default construction failed");
        fixture.slots.get().setPlayerInventoryVisible(false);
        assertThat(fixture.menu.playerInventoryVisible()).isTrue();
        assertThatThrownBy(fixture::createNative).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("construction no longer belongs");
        assertThat(fixture.defaults).hasValue(1);
    }

    @Test
    void native_factory_request_on_immediate_or_deferred_main_executor_does_not_end_preinstalled_opening() {
        for (boolean immediate : List.of(true, false)) {
            var fixture = new Fixture(ControllerUiSnapshot.Kind.NORMAL, false);
            fixture.useSessionSlots = true;
            var tasks = new ArrayDeque<Runnable>();
            if (!immediate) fixture.mainExecutor = tasks::addLast;
            var request = new AtomicReference<CompletionStage<UiResult<Integer>>>();
            fixture.registrations.register(MACHINE, context -> {
                assertThat(fixture.current.get()).isNotSameAs(fixture.menu);
                request.set(context.session().request(UiProtocolAdapters.wrap(REQUEST), 1));
                assertThat(context.session().isOpen()).isTrue();
                return new FrameworkScreen(context.menu());
            });
            UiClientAdapters.freeze(fixture.registrations);
            Screen screen = fixture.createNative();
            while (!tasks.isEmpty()) tasks.removeFirst().run(); // Still before native installation.
            assertThat(request.get().toCompletableFuture().join().status().name()).isEqualTo(Status.INVALID_REQUEST.name());
            assertThat(fixture.sent).isEmpty();
            assertThat(fixture.session.get().isOpen()).isTrue();
            fixture.current.set(((MenuAccess<?>) screen).getMenu());
            fixture.ready();
            assertThat(fixture.session.get().snapshot().ready()).isTrue();
            fixture.session.get().request(REQUEST, 2);
            while (!tasks.isEmpty()) tasks.removeFirst().run();
            assertThat(fixture.sent).hasSize(1);
        }
    }

    @Test
    void native_factory_deferred_request_does_not_revive_an_opening_replaced_before_installation() {
        var fixture = new Fixture(ControllerUiSnapshot.Kind.NORMAL, false);
        fixture.useSessionSlots = true;
        var tasks = new ArrayDeque<Runnable>();
        fixture.mainExecutor = tasks::addLast;
        var request = new AtomicReference<CompletionStage<Result<Integer>>>();
        fixture.registrations.register(MACHINE, context -> {
            request.set(fixture.session.get().request(REQUEST, 1));
            return new FrameworkScreen(context.menu());
        });
        UiClientAdapters.freeze(fixture.registrations);
        fixture.createNative();
        fixture.current.set(new TestMenu(ControllerUiSnapshot.Kind.NORMAL, false));
        while (!tasks.isEmpty()) tasks.removeFirst().run();
        assertThat(request.get().toCompletableFuture().join().status()).isEqualTo(Status.CLOSED);
        fixture.current.set(fixture.menu);
        fixture.ready();
        assertThat(fixture.session.get().isOpen()).isFalse();
        assertThat(fixture.session.get().snapshot().ready()).isFalse();
        assertThat(fixture.sent).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Screen nativeCreate(Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> factories,
                                        MenuType<TestMenu> type, Fixture fixture) {
        // AdvancedOpenScreenPayload calls precisely create() on the registered native constructor.
        var constructor = (MenuScreens.ScreenConstructor<TestMenu, ?>) factories.get(type);
        return constructor.create(fixture.menu, fixture.inventory, fixture.title);
    }

    /** Production session with injected current-menu and transport boundaries.
     * @author howxu <dev@howxu.cn> */
    private static final class Fixture {
        private final RegisterControllerUisEvent registrations = new RegisterControllerUisEvent(List.of(MACHINE, OTHER));
        private final Inventory inventory = new Inventory(null, null);
        private final Component title = Component.translatable("test:opening_title");
        private final TestMenu menu;
        private final AtomicReference<AbstractContainerMenu> current = new AtomicReference<>();
        private final AtomicReference<ControllerUiClientSession> session = new AtomicReference<>();
        private final AtomicReference<ControllerUiSlotVisibility> slots = new AtomicReference<>();
        private ControllerUiRegistration.Session routingSession;
        private final AtomicInteger defaults = new AtomicInteger();
        private final AtomicInteger sessionCreations = new AtomicInteger();
        private Consumer<ControllerUiSlotVisibility> onDefault = ignored -> {};
        private Executor mainExecutor = Runnable::run;
        private boolean useSessionSlots;
        private final List<CustomPacketPayload> sent = new ArrayList<>();

        private Fixture(ControllerUiSnapshot.Kind kind, boolean formed) {
            menu = new TestMenu(kind, formed);
            current.set(new TestMenu(kind, true));
        }

        private Screen createNative() {
            Map<MenuType<?>, MenuScreens.ScreenConstructor<?, ?>> nativeFactories = new HashMap<>();
            var event = new RegisterMenuScreensEvent(nativeFactories);
            var type = new MenuType<TestMenu>((id, inventory) -> menu, FeatureFlags.VANILLA_SET);
            registerNative(event, type);
            return nativeCreate(nativeFactories, type, this);
        }

        private void registerNative(RegisterMenuScreensEvent event, MenuType<TestMenu> type) {
            ControllerUiScreenRouter.register(event, type, UiClientAdapters.core(registrations),
                    this::defaultScreen, this::openSession);
        }

        private MenuScreens.ScreenConstructor<TestMenu, ?> constructor() {
            return ControllerUiScreenRouter.constructor(UiClientAdapters.core(registrations),
                    this::defaultScreen, this::openSession);
        }

        private DefaultScreen defaultScreen(TestMenu openingMenu, Inventory openingInventory, Component openingTitle) {
            assertThat(session.get()).isNotNull();
            defaults.incrementAndGet();
            onDefault.accept(slots.get());
            return new DefaultScreen(openingMenu, openingInventory, openingTitle);
        }

        private ControllerUiRegistration.Session openSession(TestMenu openingMenu, Component openingTitle) {
            if (routingSession != null) return routingSession;
            var protocols = new UiProtocolRegistration(List.of(MACHINE, OTHER));
            protocols.request(MACHINE, REQUEST, (context, value) -> Result.success(value));
            protocols.freeze();
            ControllerUiClientSession created = new ControllerUiClientSession(openingMenu, openingMenu.uiOpenData(), openingTitle,
                    RegistryAccess.EMPTY, protocols, mainExecutor, () -> 0L, sent::add,
                    () -> true, current::get, () -> { throw new AssertionError("Routing must not close the menu"); }, current::get);
            session.set(created);
            sessionCreations.incrementAndGet();
            var visibility = new ControllerUiSlotVisibility(openingMenu, created.id(), () -> true,
                    created::isOpen, current::get, () -> null);
            slots.set(visibility);
            routingSession = useSessionSlots ? created : new SlotSession(created, visibility);
            return routingSession;
        }

        private void ready() {
            var initial = (ControllerUiSnapshotData) session.get().snapshot();
            var snapshot = new ControllerUiSnapshotData(initial.sessionId(), 1, true, initial.dimension(),
                    initial.controllerPos(), initial.header(), List.of());
            session.get().handle(new PktControllerUiSnapshotPayload(menu.containerId, initial.sessionId(), 1, snapshot));
        }
    }

    /** Real session forwarding, with the real slot policy's injected screen supplier for headless tests.
     * @author howxu <dev@howxu.cn> */
    private record SlotSession(ControllerUiClientSession delegate, ControllerUiSlotVisibility slots)
            implements ControllerUiRegistration.Session {
        @Override public UUID id() { return delegate.id(); }
        @Override public boolean isOpen() { return delegate.isOpen(); }
        @Override public ControllerUiSnapshot snapshot() { return delegate.snapshot(); }
        @Override public boolean supports(RequestType<?, ?> type) { return delegate.supports(type); }
        @Override public boolean supports(StateType<?> type) { return delegate.supports(type); }
        @Override public <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, Q body) {
            return delegate.request(type, body);
        }
        @Override public <Q, R> CompletionStage<Result<R>> request(RequestType<Q, R> type, String laneId, Q body) {
            return delegate.request(type, laneId, body);
        }
        @Override public ControllerUiRegistration.Subscription subscribe(Executor executor, Consumer<ControllerUiSnapshot> listener) {
            return delegate.subscribe(executor, listener);
        }
        @Override public <T> ControllerUiRegistration.Subscription subscribe(StateType<T> type, Executor executor, Consumer<T> listener) {
            return delegate.subscribe(type, executor, listener);
        }
        @Override public <T> Optional<T> state(StateType<T> type) { return delegate.state(type); }
        @Override public ControllerUiRegistration.Subscription onClosed(Executor executor, Runnable listener) {
            return delegate.onClosed(executor, listener);
        }
        @Override public void close() { delegate.close(); }
    }

    /** Lightweight menu preserves actual object identity and opening metadata.
     * @author howxu <dev@howxu.cn> */
    private static final class TestMenu extends AbstractContainerMenu implements ControllerUiMenu {
        private final ControllerMenuOpenData opening;
        private boolean visible = true;

        private TestMenu(ControllerUiSnapshot.Kind kind, boolean formed) {
            super(null, 3);
            opening = new ControllerMenuOpenData(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(1, 2, 3), MACHINE,
                    kind, ControllerUiSnapshot.Role.NORMAL, formed, 0, Optional.empty(),
                    List.of(new ControllerMenuOpenData.Capability(REQUEST.id(), REQUEST.version(), false)));
        }
        @Override public ControllerMenuOpenData uiOpenData() { return opening; }
        @Override public ControllerUiServerSession uiServerSession() { return null; }
        @Override public boolean playerInventoryVisible() { return visible; }
        @Override public void setPlayerInventoryVisible(boolean visible) { this.visible = visible; }
        @Override public ItemStack quickMoveStack(Player player, int index) { throw new UnsupportedOperationException(); }
        @Override public boolean stillValid(Player player) { return true; }
    }

    /** Independent framework screen, deliberately not AbstractContainerScreen<TestMenu>.
     * @author howxu <dev@howxu.cn> */
    private static final class FrameworkScreen extends Screen implements MenuAccess<AbstractContainerMenu> {
        private final AbstractContainerMenu menu;
        private FrameworkScreen(AbstractContainerMenu menu) { super(null, null, Component.empty()); this.menu = menu; }
        @Override public AbstractContainerMenu getMenu() { return menu; }
    }

    /** Native default constructor arguments retained for fallback assertions.
     * @author howxu <dev@howxu.cn> */
    private static final class DefaultScreen extends Screen implements MenuAccess<TestMenu> {
        private final TestMenu menu;
        private final Inventory inventory;
        private DefaultScreen(TestMenu menu, Inventory inventory, Component title) {
            super(null, null, title);
            this.menu = menu;
            this.inventory = inventory;
        }
        @Override public TestMenu getMenu() { return menu; }
    }

    /** Invalid author return with no menu access.
     * @author howxu <dev@howxu.cn> */
    private static final class PlainScreen extends Screen {
        private PlainScreen() { super(null, null, Component.empty()); }
    }
}

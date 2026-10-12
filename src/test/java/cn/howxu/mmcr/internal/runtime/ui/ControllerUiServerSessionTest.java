package cn.howxu.mmcr.internal.runtime.ui;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.ServerContext;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.event.ControllerSyncEvents;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.network.ui.ControllerUiRequestDispatcher.RequestWindow;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession.MachineBinding;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession.ProviderContext;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession.ProviderState;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure tests of the production binding/lane gates and provider callback boundaries; no world authorization stubs.
 * @author howxu <dev@howxu.cn>
 */
class ControllerUiServerSessionTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:server_ui_a");
    private static final ResourceLocation B = ResourceLocation.parse("test:server_ui_b");
    private static ServerPlayer player;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        // Provider callbacks only need an identity; CleanupMenu attaches the player to its owner's server level.
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        player = (ServerPlayer) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ServerPlayer.class);
    }

    @Test
    void configured_switch_is_rejected_even_while_formed_matched_a_persists() {
        Machine a = machine(A), b = machine(B);
        MachineBinding bound = MachineBinding.bind(structure(a, a, true, 1), A);
        StructureSnapshot changed = structure(b, a, true, 2);
        assertThat(changed.machine()).isSameAs(a);
        assertThat(changed.formed()).isTrue();
        assertThat(bound.matches(changed, A)).isFalse();
        assertThat(bound.matches(structure(a, a, true, 3), B)).isFalse();
    }

    @Test
    void direct_close_immediately_forgets_both_indices_and_repeated_close_keeps_a_new_opening() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                ResourceLocation.parse("mmcr:test_cube"), BlockPos.ZERO);
        CleanupMenu first = new CleanupMenu(controller, player);
        CleanupMenu later = new CleanupMenu(controller, player);
        Map<?, ?> reverse = observerMap(ControllerSyncEvents.class, null, "MENUS");
        Map<?, ?> local = observerMap(MachineControllerBlockEntity.class, controller, "menuObservers");
        try {
            player.containerMenu = first;
            ControllerSyncEvents.onMenuOpen(new PlayerContainerEvent.Open(player, first));
            assertThat(reverse.containsKey(player)).isTrue();
            assertThat(local.get(player)).isSameAs(first);
            first.session.close();
            assertThat(reverse.containsKey(player)).isFalse();
            assertThat(local.containsKey(player)).isFalse();
            assertThat(first.session.active()).isFalse();
            assertThat(first.session.pollSnapshot()).isEmpty();

            player.containerMenu = later;
            ControllerSyncEvents.onMenuOpen(new PlayerContainerEvent.Open(player, later));
            Object opening = reverse.get(player);
            assertThat(opening).isNotNull();
            first.session.close();
            assertThat(reverse.get(player)).isSameAs(opening);
            assertThat(local.get(player)).isSameAs(later);
            assertThat(later.session.active()).isTrue();
            assertThat(player.containerMenu).isSameAs(later);
        } finally {
            first.session.close();
            later.session.close();
            player.containerMenu = null;
        }
    }

    @Test
    void late_close_of_replaced_session_preserves_current_opening_with_the_same_container_id() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                ResourceLocation.parse("mmcr:test_cube"), BlockPos.ZERO);
        CleanupMenu first = new CleanupMenu(controller, player);
        CleanupMenu later = new CleanupMenu(controller, player);
        Map<?, ?> reverse = observerMap(ControllerSyncEvents.class, null, "MENUS");
        Map<?, ?> local = observerMap(MachineControllerBlockEntity.class, controller, "menuObservers");
        try {
            player.containerMenu = first;
            ControllerSyncEvents.onMenuOpen(new PlayerContainerEvent.Open(player, first));
            player.containerMenu = later;
            ControllerSyncEvents.onMenuOpen(new PlayerContainerEvent.Open(player, later));
            Object opening = reverse.get(player);
            assertThat(opening).isNotNull();
            first.session.close();
            assertThat(reverse.get(player)).isSameAs(opening);
            assertThat(local.get(player)).isSameAs(later);
            assertThat(later.session.active()).isTrue();
            assertThat(player.containerMenu).isSameAs(later);
            later.session.close();
            assertThat(reverse.containsKey(player)).isFalse();
            assertThat(local.containsKey(player)).isFalse();
        } finally {
            first.session.close();
            later.session.close();
            player.containerMenu = null;
        }
    }

    private static Map<?, ?> observerMap(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(target);
    }

    @Test
    void direct_close_cleans_a_local_observer_without_a_reverse_index_entry() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(
                ResourceLocation.parse("mmcr:test_cube"), BlockPos.ZERO);
        CleanupMenu menu = new CleanupMenu(controller, player);
        Map<?, ?> reverse = observerMap(ControllerSyncEvents.class, null, "MENUS");
        Map<?, ?> local = observerMap(MachineControllerBlockEntity.class, controller, "menuObservers");
        try {
            player.containerMenu = menu;
            controller.observeMenu(player, menu);
            assertThat(reverse.containsKey(player)).isFalse();
            assertThat(local.get(player)).isSameAs(menu);

            menu.session.close();

            assertThat(local.containsKey(player)).isFalse();
            assertThat(menu.session.closed()).isTrue();
            assertThat(menu.session.pollSnapshot()).isEmpty();
        } finally {
            menu.session.close();
            player.containerMenu = null;
        }
    }

    /** Close fixture with complete session fields and exact world/menu identities, without encoding.
     * @author howxu <dev@howxu.cn> */
    private static final class CleanupMenu extends AbstractContainerMenu implements ControllerUiMenu {
        private final ControllerMenuOpenData openData;
        private final ControllerUiServerSession session;

        private CleanupMenu(MachineControllerBlockEntity owner, ServerPlayer player) throws Exception {
            super(null, 7);
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            if (owner.getLevel() == null) {
                CleanupLevel level = (CleanupLevel) unsafe.allocateInstance(CleanupLevel.class);
                level.owner = owner;
                Field dimension = Level.class.getDeclaredField("dimension");
                dimension.setAccessible(true);
                dimension.set(level, Level.OVERWORLD);
                owner.setLevel(level);
            }
            ServerLevel level = (ServerLevel) owner.getLevel();
            Field playerLevel = Entity.class.getDeclaredField("level");
            playerLevel.setAccessible(true);
            playerLevel.set(player, level);
            openData = ControllerMenuOpenData.forOwner(owner, level, Kind.NORMAL);
            ResourceLocation physicalId = ((MachineControllerBlock) owner.getBlockState().getBlock()).machineId();
            session = (ControllerUiServerSession) unsafe.allocateInstance(ControllerUiServerSession.class);
            // Keep protocol registration's global construction latch untouched in this cleanup-only fixture.
            for (var entry : Map.ofEntries(Map.entry("menu", this), Map.entry("owner", owner),
                    Map.entry("player", player), Map.entry("level", level), Map.entry("openData", openData),
                    Map.entry("machineBinding", MachineBinding.bind(owner.runtimeSnapshot().structure(), physicalId)),
                    Map.entry("protocols", ControllerUiServerSession.currentProtocols()),
                    Map.entry("requests", new RequestWindow()), Map.entry("textSnapshots", Map.of()),
                    Map.entry("providerStates", new LinkedHashMap<>()), Map.entry("active", true)).entrySet()) {
                Field field = ControllerUiServerSession.class.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(session, entry.getValue());
            }
        }

        @Override public ControllerMenuOpenData uiOpenData() { return openData; }
        @Override public ControllerUiServerSession uiServerSession() { return session; }
        @Override public boolean playerInventoryVisible() { return true; }
        @Override public void setPlayerInventoryVisible(boolean visible) {}
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }

    /** Minimal server world for the production session's exact owner lookup.
     * @author howxu <dev@howxu.cn> */
    private static final class CleanupLevel extends ServerLevel {
        private MachineControllerBlockEntity owner;

        private CleanupLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, null, false, 0L, List.of(), false, null);
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            return owner.getBlockPos().equals(pos) ? owner : null;
        }
    }

    @Test
    void null_configuration_can_initialize_to_same_physical_machine_but_not_another_machine() {
        MachineBinding bound = MachineBinding.bind(StructureSnapshot.empty(), A);
        assertThat(bound.matches(structure(machine(A), null, false, 1), A)).isTrue();
        assertThat(bound.matches(structure(machine(B), null, false, 1), A)).isFalse();
        assertThat(bound.matches(StructureSnapshot.empty(), B)).isFalse();
    }

    @Test
    void configured_identity_is_bound_independently_of_a_different_physical_controller() {
        Machine a = machine(A);
        MachineBinding bound = MachineBinding.bind(structure(a, a, true, 1), B);
        assertThat(bound.matches(structure(a, a, true, 2), B)).isTrue();
        assertThat(bound.matches(structure(machine(B), a, true, 2), B)).isFalse();
    }

    @Test
    void normal_authorizes_its_published_base_without_borrowing_factory_placeholder_lanes() {
        ControllerRuntimeSnapshot normal = runtime(structure(machine(A), null, false, 1), false,
                List.of(lane(0, "idle-0")));
        assertThat(snapshot(normal).kind()).isEqualTo(Kind.NORMAL);
        assertThat(snapshot(normal).lanes()).extracting(lane -> lane.id()).containsExactly("base");
        assertThat(ControllerUiServerSession.laneExists(normal, Optional.of("base"))).isTrue();
        assertThat(ControllerUiServerSession.laneExists(normal, Optional.of("idle-0"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(normal, Optional.of("missing"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(normal, Optional.empty())).isTrue();
    }

    @Test
    void tick_authorizes_no_targeted_lane_even_with_factory_metadata_and_published_placeholders() {
        Machine tick = new DynamicMachine(A, "Tick", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(A), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1,
                List.of(), MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(),
                TickBehavior.builder().build());
        ControllerRuntimeSnapshot runtime = runtime(structure(tick, null, false, 1), true,
                List.of(lane(0, "base"), lane(1, "idle-0")));
        assertThat(snapshot(runtime).kind()).isEqualTo(Kind.TICK);
        assertThat(snapshot(runtime).lanes()).isEmpty();
        assertThat(ControllerUiServerSession.laneExists(runtime, Optional.of("base"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(runtime, Optional.of("idle-0"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(runtime, Optional.empty())).isTrue();
    }

    @Test
    void kind_and_factory_lane_gate_follow_current_runtime_after_shape_change_and_lane_removal() {
        StructureSnapshot structure = structure(machine(A), null, false, 1);
        ControllerRuntimeSnapshot initial = runtime(structure, false, List.of(lane(0, "idle-0")));
        ControllerRuntimeSnapshot factory = runtime(structure, true, List.of(lane(0, "base"), lane(1, "factory-1")));
        ControllerRuntimeSnapshot removed = runtime(structure, true, List.of(lane(0, "base"), lane(1, "idle-1")));
        assertThat(ControllerUiServerSession.runtimeKind(initial)).isEqualTo(snapshot(initial).kind()).isEqualTo(Kind.NORMAL);
        assertThat(ControllerUiServerSession.runtimeKind(factory)).isEqualTo(snapshot(factory).kind()).isEqualTo(Kind.FACTORY);
        assertThat(ControllerUiServerSession.laneExists(initial, Optional.of("factory-1"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(factory, Optional.of("factory-1"))).isTrue();
        assertThat(ControllerUiServerSession.laneExists(removed, Optional.of("factory-1"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(removed, Optional.of("idle-1"))).isFalse();
        assertThat(ControllerUiServerSession.laneExists(removed, Optional.of("base"))).isTrue();
    }

    @Test
    void revision_closure_stops_capture_and_encoding_even_when_revision_is_unchanged() {
        ProviderFixture fixture = new ProviderFixture();
        var provider = fixture.registration(ignored -> fixture.open = false, ignored -> {});
        assertThat(fixture.capture(provider)).isNull();
        assertThat(fixture.revisions).hasValue(1);
        assertThat(fixture.snapshots).hasValue(0);
        assertThat(fixture.encodings).hasValue(0);
        fixture.open = true;
        assertThat(fixture.capture(fixture.registration(ignored -> {}, ignored -> {}))).containsExactly(7);
        assertThat(fixture.capture(provider)).isNull();
        assertThat(fixture.snapshots).hasValue(1);
        assertThat(fixture.encodings).hasValue(1);
    }

    @Test
    void revision_storage_unbind_or_replacement_aborts_old_capture_and_retries_on_current_binding() {
        for (DataStorage replacement : new DataStorage[]{null, new DataStorage()}) {
            ProviderFixture fixture = new ProviderFixture();
            assertThat(fixture.capture(fixture.registration(ignored -> fixture.storage = replacement, ignored -> {}))).isNull();
            assertThat(fixture.snapshots).hasValue(0);
            assertThat(fixture.encodings).hasValue(0);
            assertThat(fixture.capture(fixture.registration(ignored -> {}, captured ->
                    assertThat(captured.machine().dataStorageForRuntime()).isSameAs(replacement)))).containsExactly(7);
            assertThat(fixture.snapshots).hasValue(1);
        }
    }

    @Test
    void revision_structure_generation_change_with_same_machine_and_storage_aborts_capture() {
        ProviderFixture fixture = new ProviderFixture();
        assertThat(fixture.capture(fixture.registration(ignored -> fixture.runtime = runtime(
                structure(fixture.runtime.structure().configuredMachine(), null, false, 2), false, List.of()),
                ignored -> {}))).isNull();
        assertThat(fixture.snapshots).hasValue(0);
        assertThat(fixture.encodings).hasValue(0);
        assertThat(fixture.capture(fixture.registration(ignored -> {}, ignored -> {}))).containsExactly(7);
    }

    @Test
    void successful_capture_uses_reacquired_context_and_unchanged_revision_does_not_recapture() {
        ProviderFixture fixture = new ProviderFixture();
        ServerContext[] revisionContext = new ServerContext[1];
        var provider = fixture.registration(context -> revisionContext[0] = context, context -> {
            assertThat(context).isNotSameAs(revisionContext[0]);
            assertThat(context.machine().dataStorageForRuntime()).isSameAs(fixture.storage);
        });
        assertThat(fixture.capture(provider)).containsExactly(7);
        assertThat(fixture.capture(provider)).isNull();
        assertThat(fixture.revisions).hasValue(2);
        assertThat(fixture.snapshots).hasValue(1);
        assertThat(fixture.encodings).hasValue(1);
    }

    @Test
    void snapshot_closure_stops_encoder_and_encoder_closure_discards_encoded_value() {
        ProviderFixture closedInSnapshot = new ProviderFixture();
        assertThat(closedInSnapshot.capture(closedInSnapshot.registration(ignored -> {},
                ignored -> closedInSnapshot.open = false))).isNull();
        assertThat(closedInSnapshot.snapshots).hasValue(1);
        assertThat(closedInSnapshot.encodings).hasValue(0);
        ProviderFixture closedInEncoder = new ProviderFixture();
        closedInEncoder.onEncode = () -> closedInEncoder.open = false;
        assertThat(closedInEncoder.capture(closedInEncoder.registration(ignored -> {}, ignored -> {}))).isNull();
        assertThat(closedInEncoder.encodings).hasValue(1);
    }

    @Test
    void new_viewer_provider_state_captures_its_own_baseline_even_at_the_same_author_revision() {
        ProviderFixture first = new ProviderFixture();
        var registration = first.registration(ignored -> {}, ignored -> {});
        assertThat(first.capture(registration)).containsExactly(7);
        assertThat(first.capture(registration)).isNull();
        ProviderFixture later = new ProviderFixture();
        assertThat(later.capture(registration)).containsExactly(7);
        assertThat(later.capture(registration)).isNull();
        assertThat(first.snapshots).hasValue(2);
        assertThat(first.encodings).hasValue(2);
        first.open = false;
        assertThat(first.capture(registration)).isNull();
        assertThat(first.snapshots).hasValue(2);
        assertThat(first.encodings).hasValue(2);
    }

    @Test
    void snapshot_or_encoder_rebind_without_machine_change_discards_old_value() {
        ProviderFixture changedInSnapshot = new ProviderFixture();
        assertThat(changedInSnapshot.capture(changedInSnapshot.registration(ignored -> {},
                ignored -> changedInSnapshot.storage = new DataStorage()))).isNull();
        assertThat(changedInSnapshot.encodings).hasValue(0);
        assertThat(changedInSnapshot.capture(changedInSnapshot.registration(ignored -> {}, ignored -> {}))).containsExactly(7);
        ProviderFixture changedInEncoder = new ProviderFixture();
        changedInEncoder.onEncode = () -> changedInEncoder.storage = null;
        assertThat(changedInEncoder.capture(changedInEncoder.registration(ignored -> {}, ignored -> {}))).isNull();
        assertThat(changedInEncoder.encodings).hasValue(1);
        changedInEncoder.onEncode = () -> {};
        assertThat(changedInEncoder.capture(changedInEncoder.registration(ignored -> {}, ignored -> {}))).containsExactly(7);
    }

    private static Machine machine(ResourceLocation id) {
        return new DynamicMachine(id, "Server UI", new BlockArray(Map.of()));
    }

    private static StructureSnapshot structure(Machine configured, Machine matched, boolean formed, long version) {
        return new StructureSnapshot(configured, matched, null, null, Direction.SOUTH, Direction.SOUTH,
                0, formed, version, null, null, null, false, true, Set.of());
    }

    private static ControllerRuntimeSnapshot runtime(StructureSnapshot structure, boolean factory,
                                                     List<FactoryRuntime.ThreadSnapshot> lanes) {
        FactorySnapshot factorySnapshot = new FactorySnapshot(false, false, List.of(), Math.max(1, lanes.size()),
                0, 1, false, lanes, "", 0, null, List.of(), 0, 1);
        return new ControllerRuntimeSnapshot(structure, 0, 0, 0, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, CraftingStateSnapshot.empty(structure.version(), 0, 0),
                factorySnapshot, List.of(), List.of(), List.of(), A.toString(), "test.server_ui", 0,
                factory, factory, 0, 0, 1);
    }

    private static FactoryRuntime.ThreadSnapshot lane(int index, String id) {
        return new FactoryRuntime.ThreadSnapshot(index, id, id.equals("base"), false, false, "", 0, 0, 1,
                null, ControllerRecipePresentation.empty());
    }

    private static ControllerUiSnapshotData snapshot(ControllerRuntimeSnapshot runtime) {
        return new ControllerUiSnapshotData.CaptureCache().capture(UUID.randomUUID(), 1, Level.OVERWORLD, BlockPos.ZERO,
                runtime, null, false, List.of(), Map.of());
    }

    /** Controlled callback-boundary fixture; player is an unused identity, and storage/runtime are real values.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ProviderFixture {
        private final ProviderState state = new ProviderState();
        private final AtomicInteger revisions = new AtomicInteger();
        private final AtomicInteger snapshots = new AtomicInteger();
        private final AtomicInteger encodings = new AtomicInteger();
        private boolean open = true;
        private DataStorage storage = new DataStorage();
        private ControllerRuntimeSnapshot runtime = runtime(structure(machine(A), null, false, 1), false, List.of());
        private Runnable onEncode = () -> {};

        private ProviderContext current() {
            if (!open) return null;
            var machine = new MachineBehaviorContext(null, null, BlockPos.ZERO, A, 0,
                    new ControllerScreenTextState(), storage);
            return new ProviderContext(new ServerContext(player, machine, Optional.empty()), runtime);
        }

        private UiProtocolRegistration.StateRegistration<Integer> registration(Consumer<ServerContext> revision,
                                                                               Consumer<ServerContext> capture) {
            StreamCodec<RegistryFriendlyByteBuf, Integer> codec = StreamCodec.of((buffer, value) -> {
                encodings.incrementAndGet();
                onEncode.run();
                buffer.writeByte(value);
            }, buffer -> (int) buffer.readUnsignedByte());
            return new UiProtocolRegistration.StateRegistration<>(new UiProtocolRegistration.StateType<>(A, 1, codec),
                    new UiProtocolRegistration.Provider<>() {
                        public long revision(ServerContext context) {
                            revisions.incrementAndGet();
                            revision.accept(context);
                            return 1;
                        }

                        public Integer snapshot(ServerContext context) {
                            snapshots.incrementAndGet();
                            capture.accept(context);
                            return 7;
                        }
                    });
        }

        private byte[] capture(UiProtocolRegistration.StateRegistration<Integer> registration) {
            return state.capture(registration, this::current, RegistryAccess.EMPTY,
                    (revision, failure) -> { throw new AssertionError(failure); });
        }
    }
}

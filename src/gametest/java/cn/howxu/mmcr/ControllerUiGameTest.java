package cn.howxu.mmcr;

import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.event.ControllerUiEvents;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import cn.howxu.mmcr.internal.network.ui.ControllerUiRequestDispatcher;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiRequestPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiResponsePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiCustomStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiProgressPayload;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.api.facade.ui.UiSnapshotAdapters;
import cn.howxu.mmcr.publicapi.event.RegisterControllerUiProtocolsEvent;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.ui.UiRequestType;
import cn.howxu.mmcr.publicapi.ui.UiStateType;
import cn.howxu.mmcr.publicapi.ui.UiResult;
import cn.howxu.mmcr.publicapi.ui.UiStateProvider;
import cn.howxu.mmcr.publicapi.ui.UiServerContext;
import cn.howxu.mmcr.publicapi.runtime.ItemOutputView;
import cn.howxu.mmcr.publicapi.runtime.FluidOutputView;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.data.DataValue;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestException;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real controller menus, dispatcher, providers and runtime ownership checks.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerUiGameTest {
    static final Identifier MACHINE_ID = MMCR.id("controller_ui_storage_fixture");
    private static final Map<ServerPlayer, Probe> PROBES = new IdentityHashMap<>();
    private static final Map<Integer, Probe> VIEWERS = new HashMap<>();
    private static final ThreadLocal<Probe> DECODING = new ThreadLocal<>();
    private static final StreamCodec<RegistryFriendlyByteBuf, Integer> REQUEST_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeVarInt(value), buffer -> {
                Probe probe = DECODING.get();
                if (probe != null) probe.decodes++;
                return buffer.readVarInt();
            });
    private static final UiRequestType<Integer, Integer> SET_MODE = UiRequestType.of(
            MMCR.id("ui_fixture_set_mode"), 1, REQUEST_CODEC, REQUEST_CODEC);
    private static final UiStateType<ModeState> MODE_STATE = UiStateType.of(
            MMCR.id("ui_fixture_mode_state"), 1, ModeState.CODEC);

    /** Registered only from the GameTest source set's mod-bus subscriber. */
    static void registerProtocols(RegisterControllerUiProtocolsEvent event) {
        event.registrar().request(MACHINE_ID, SET_MODE, (context, mode) -> {
            Probe probe = PROBES.get(context.player());
            if (probe != null) probe.handles++;
            if (probe != null && probe.afterHandle != null) {
                probe.afterHandle.run();
                return UiResult.success(mode);
            }
            var storage = context.machine().dataStorage();
            if (storage == null || mode < 0 || mode > 2) {
                return UiResult.reject(Component.translatable("gui.mmcr.ui.rejected"));
            }
            storage.set("mode", DataKey.of(mode));
            storage.set("mode_revision", DataKey.of(storage.get("mode_revision")
                    .flatMap(DataKey::asLong).orElse(0L) + 1L));
            return UiResult.success(mode);
        });
        for (var machine : List.of(MMCR.id("test_cube"), MMCR.id("data_storage_tick"))) {
            event.registrar().request(machine, SET_MODE, (context, mode) -> {
                Probe probe = PROBES.get(context.player());
                if (probe != null) probe.handles++;
                return UiResult.success(mode);
            });
        }
        event.registrar().state(MACHINE_ID, MODE_STATE, new UiStateProvider<ModeState>() {
            @Override public long revision(UiServerContext context) {
                Probe probe = PROBES.get(context.player());
                if (probe != null) {
                    probe.queries++;
                    if (probe.failRevision) throw new IllegalStateException("Expected fixture revision failure");
                    if (probe.afterRevision != null) {
                        Runnable callback = probe.afterRevision;
                        probe.afterRevision = null;
                        callback.run();
                        return Long.MAX_VALUE;
                    }
                }
                var storage = context.machine().dataStorage();
                return storage == null ? 0L : storage.get("mode_revision").flatMap(DataKey::asLong).orElse(0L);
            }
            @Override public ModeState snapshot(UiServerContext context) {
                Probe probe = PROBES.get(context.player());
                if (probe != null) {
                    probe.captures++;
                    if (probe.failCapture) throw new IllegalStateException("Expected fixture capture failure");
                }
                var storage = context.machine().dataStorage();
                return new ModeState(storage == null ? 0L : storage.get("mode_revision").flatMap(DataKey::asLong).orElse(0L),
                        storage == null ? -1 : storage.get("mode").flatMap(DataKey::asInt).orElse(0),
                        context.player().getId());
            }
        });
    }

    /** Pure owned state with an explicit per-viewer projection.
     * @author howxu <dev@howxu.cn> */
    private record ModeState(long revision, int mode, int viewer) {
        private static final StreamCodec<RegistryFriendlyByteBuf, ModeState> DATA_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, ModeState::revision,
                ByteBufCodecs.VAR_INT, ModeState::mode,
                ByteBufCodecs.VAR_INT, ModeState::viewer, ModeState::new);
        private static final StreamCodec<RegistryFriendlyByteBuf, ModeState> CODEC = StreamCodec.of((buffer, value) -> {
            Probe probe = VIEWERS.get(value.viewer());
            if (probe != null) {
                probe.encodes++;
                if (probe.failEncode) throw new IllegalStateException("Expected fixture encoding failure");
            }
            DATA_CODEC.encode(buffer, value);
        }, DATA_CODEC::decode);
    }

    /** Callback counters belong to one actual viewer, not a process-wide test reset.
     * @author howxu <dev@howxu.cn> */
    private static final class Probe {
        private int decodes;
        private int handles;
        private int queries;
        private int captures;
        private int encodes;
        private boolean failCapture;
        private boolean failRevision;
        private boolean failEncode;
        private Runnable afterHandle;
        private Runnable afterRevision;
    }

    public static void requestStorage(GameTestHelper helper) {
        formedFixture(helper, fixture -> {
            var first = fixture.open(11);
            var second = fixture.open(12);
            Probe probe = PROBES.get(first);
            Probe other = PROBES.get(second);
            helper.assertTrue(probe.captures == 1 && other.captures == 1
                            && lastState(first).viewer() != lastState(second).viewer(),
                    "Open captures independent viewer permission projections");
            var baseline = packets(first, PktControllerUiSnapshotPayload.class).getLast();
            helper.assertTrue(baseline.snapshotData().hasDataStorage() && baseline.snapshotData().formed(),
                    "Open publishes the real bound storage and structure");
            int fullCount = packets(first, PktControllerUiSnapshotPayload.class).size();
            first.containerMenu.broadcastChanges();
            first.containerMenu.broadcastChanges();
            helper.assertTrue(probe.captures == 1 && probe.encodes == 1
                            && packets(first, PktControllerUiSnapshotPayload.class).size() == fullCount,
                    "Unchanged polls query revision but neither capture state nor resend full baselines");

            dispatch(first, request(first, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            var response = packets(first, PktControllerUiResponsePayload.class).getLast();
            helper.assertTrue(response.status() == Status.SUCCESS && response.requestId() == 1
                            && response.sessionId().equals(ui(first).uiOpenData().sessionId())
                            && ControllerUiPayloadCodec.decodeExact(REQUEST_CODEC, response.body(),
                            helper.getLevel().registryAccess(), ControllerUiPayloadCodec.RESPONSE_LIMIT) == 2
                            && fixture.mode() == 2 && fixture.revision() == 1 && probe.handles == 1 && probe.decodes == 1,
                    "Real dispatcher performs one storage write and returns the corresponding typed response");
            second.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).mode() == 2 && lastState(second).mode() == 2
                            && lastState(first).revision() == 1 && lastState(second).revision() == 1
                            && lastState(first).viewer() == first.getId() && lastState(second).viewer() == second.getId(),
                    "Both actual viewers receive updated state with independently projected identity");
            fixture.owner.serverTick();
            first.containerMenu.broadcastChanges();
            second.containerMenu.broadcastChanges();
            helper.assertTrue(packets(first, PktControllerUiSnapshotPayload.class).getLast().snapshotData()
                            .dataStorageValues().get("mode").intValue() == 2
                            && packets(second, PktControllerUiSnapshotPayload.class).getLast().snapshotData()
                            .dataStorageValues().get("mode").intValue() == 2,
                    "Both built-in viewer snapshots include the published real storage mutation");

            for (long id = 2; id <= 16; id++) dispatch(first, request(first, id, SET_MODE.id(), 1, Optional.empty(), new byte[]{1}));
            int queries = probe.queries;
            int captures = probe.captures;
            int encodes = probe.encodes;
            dispatch(first, request(first, 17, SET_MODE.id(), 1, Optional.empty(), new byte[]{0}));
            helper.assertTrue(packets(first, PktControllerUiResponsePayload.class).getLast().status() == Status.BUSY
                            && probe.handles == 16 && probe.decodes == 16 && fixture.revision() == 16
                            && probe.queries == queries && probe.captures == captures && probe.encodes == encodes,
                    "The actual session's 17th request is BUSY before decoder, handler or provider work");
            dispatch(first, request(first, 17, SET_MODE.id(), 1, Optional.empty(), new byte[]{0}));
            helper.assertTrue(packets(first, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST
                            && probe.handles == 16 && fixture.mode() == 1 && fixture.revision() == 16,
                    "The BUSY request ID is consumed and cannot replay business work");

            // Provider failure never removes the last successful state, and is attempted once per revision.
            probe.failCapture = true;
            fixture.storage.storage().set("mode_revision", DataValue.of(17L));
            int states = packets(first, PktControllerUiCustomStatePayload.class).size();
            first.containerMenu.broadcastChanges();
            int attempted = probe.captures;
            first.containerMenu.broadcastChanges();
            helper.assertTrue(probe.captures == attempted && attempted == captures + 1
                            && packets(first, PktControllerUiCustomStatePayload.class).size() == states
                            && lastState(first).revision() == 16,
                    "A failed provider revision is suppressed and retains its last successful owned packet");
            second.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(second).revision() == 17,
                    "One viewer's failed cache does not suppress another viewer's provider");
            probe.failCapture = false;
            fixture.storage.storage().set("mode_revision", DataValue.of(18L));
            first.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).revision() == 18 && probe.captures == attempted + 1,
                    "A new provider revision recovers after capture failure");
            probe.failRevision = true;
            fixture.storage.storage().set("mode_revision", DataValue.of(19L));
            first.containerMenu.broadcastChanges();
            first.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).revision() == 18 && probe.captures == attempted + 1,
                    "Revision exceptions leave the successful value intact without invoking capture");
            probe.failRevision = false;
            first.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).revision() == 19,
                    "Revision query recovery publishes the next state");
            probe.failEncode = true;
            fixture.storage.storage().set("mode_revision", DataValue.of(20L));
            first.containerMenu.broadcastChanges();
            int encodeAttempts = probe.encodes;
            int captureAttempts = probe.captures;
            first.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).revision() == 19 && probe.encodes == encodeAttempts
                            && probe.captures == captureAttempts,
                    "Failed encoding retains the successful packet and suppresses both capture and encode at that revision");
            probe.failEncode = false;
            fixture.storage.storage().set("mode_revision", DataValue.of(21L));
            first.containerMenu.broadcastChanges();
            helper.assertTrue(lastState(first).revision() == 21 && probe.encodes == encodeAttempts + 1,
                    "Encoding resumes exactly once when the provider revision advances");
            var closed = ui(first).uiServerSession();
            first.closeContainer();
            queries = probe.queries;
            captures = probe.captures;
            encodes = probe.encodes;
            closed.broadcastChanges();
            helper.assertTrue(probe.queries == queries && probe.captures == captures && probe.encodes == encodes,
                    "Close releases provider polling and capture work");
            var nextTickReplay = request(second, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{0});
            dispatch(second, nextTickReplay);
            long afterWrite = fixture.revision();
            int otherHandles = other.handles;
            int otherDecodes = other.decodes;
            helper.runAfterDelay(1, () -> {
                try {
                    dispatch(second, nextTickReplay);
                    helper.assertTrue(other.handles == otherHandles && other.decodes == otherDecodes
                                    && fixture.revision() == afterWrite
                                    && packets(second, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                            "A later server turn cannot replay a previously consumed ID");
                    dispatch(second, request(second, 2, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
                    helper.assertTrue(fixture.revision() == afterWrite + 1 && other.handles == otherHandles + 1,
                            "A new request on a later server turn retains usable request budget");
                    providerInvalidation(helper, fixture);
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        });
    }

    public static void reopenStaleRequest(GameTestHelper helper) {
        formedFixture(helper, fixture -> {
            var player = fixture.open(21);
            Probe probe = PROBES.get(player);
            var oldMenu = player.containerMenu;
            var stale = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2});
            player.closeContainer();
            dispatch(player, stale);
            helper.assertTrue(probe.decodes == 0 && probe.handles == 0, "Closed-menu request never reaches author code");
            fixture.reopen(player, oldMenu.containerId);
            dispatch(player, stale);
            oldMenu.removed(player);
            helper.assertTrue(probe.decodes == 0 && ui(player).uiServerSession().validate(),
                    "Same container ID with a new token rejects stale bytes and late old-menu removal");
            var current = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{1});
            dispatch(player, new PktControllerUiRequestPayload(current.containerId() + 1, current.sessionId(), 1,
                    SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            var stranger = fixture.newPlayer();
            stranger.containerMenu = player.containerMenu;
            dispatch(stranger, current);
            stranger.containerMenu = stranger.inventoryMenu;
            helper.assertTrue(probe.decodes == 0 && PROBES.get(stranger).decodes == 0 && fixture.revision() == 0,
                    "Wrong actual player and container ID are rejected before decoding");
            rejectRegistered(helper, fixture, player, 1, Identifier.parse("example:unknown"), 1, Optional.empty(),
                    new byte[]{2}, Status.UNSUPPORTED);
            rejectRegistered(helper, fixture, player, 2, SET_MODE.id(), 2, Optional.empty(), new byte[]{2}, Status.VERSION_MISMATCH);
            rejectRegistered(helper, fixture, player, 3, SET_MODE.id(), 1, Optional.of("removed"), new byte[]{2}, Status.INVALID_REQUEST);
            dispatch(player, request(player, 4, SET_MODE.id(), 1, Optional.empty(), new byte[]{1, 0}));
            helper.assertTrue(probe.decodes == 1 && probe.handles == 0 && fixture.revision() == 0
                            && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                    "Trailing body bytes cannot invoke the handler");
            dispatch(player, request(player, 5, SET_MODE.id(), 1, Optional.empty(), new byte[]{1}));
            dispatch(player, request(player, 5, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            dispatch(player, request(player, 4, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            helper.assertTrue(probe.handles == 1 && probe.decodes == 2 && fixture.mode() == 1 && fixture.revision() == 1,
                    "Duplicate and older request IDs do not repeat business work");

            fixture.reopen(player, 21);
            var distanceRequest = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2});
            player.setPos(fixture.owner.getBlockPos().getX() + 32, fixture.owner.getBlockPos().getY(), fixture.owner.getBlockPos().getZ());
            dispatch(player, distanceRequest);
            helper.assertTrue(player.containerMenu == player.inventoryMenu && probe.decodes == 2 && probe.handles == 1,
                    "Distance invalidation closes the actual container before decoder or handler");
            fixture.reopen(player, 21);
            var dimensionRequest = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2});
            var otherLevel = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(otherLevel != null, "The real second dimension is available");
            setPlayerLevel(player, otherLevel);
            try {
                dispatch(player, dimensionRequest);
            } finally {
                setPlayerLevel(player, helper.getLevel());
            }
            helper.assertTrue(probe.decodes == 2 && probe.handles == 1 && player.containerMenu == player.inventoryMenu,
                    "A different actual ServerLevel rejects the old dimension authorization");
            fixture.reopen(player, 21);
            var machineRequest = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2});
            int queries = probe.queries;
            int captures = probe.captures;
            int encodes = probe.encodes;
            int states = packets(player, PktControllerUiCustomStatePayload.class).size();
            var oldSession = ui(player).uiServerSession();
            probe.afterHandle = () -> {
                fixture.owner.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
                helper.assertTrue(fixture.owner.structureSnapshot().formed()
                                && fixture.owner.structureSnapshot().machine().registryName().equals(MACHINE_ID)
                                && fixture.owner.structureSnapshot().configuredMachine().registryName().equals(MMCR.id("test_cube")),
                        "Handler changes configured B while the real formed match still retains A");
            };
            dispatch(player, machineRequest);
            probe.afterHandle = null;
            helper.assertTrue(probe.decodes == 3 && probe.handles == 2 && fixture.revision() == 1
                            && player.containerMenu == player.inventoryMenu && !oldSession.active()
                            && probe.queries == queries && probe.captures == captures && probe.encodes == encodes
                            && packets(player, PktControllerUiCustomStatePayload.class).size() == states
                            && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == 1
                            && packets(player, PktControllerUiResponsePayload.class).getLast().sessionId().equals(machineRequest.sessionId())
                            && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.SUCCESS,
                    "Handler identity change closes the old session before its after-handler provider work");
            dispatch(player, new PktControllerUiRequestPayload(machineRequest.containerId(), machineRequest.sessionId(),
                    2, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            helper.assertTrue(probe.decodes == 3 && probe.handles == 2,
                    "Increasing request ID cannot reenter the old machine protocol after configured identity changes");
            fixture.owner.setMachine(MachineRegistry.getMachine(MACHINE_ID));
            fixture.reopen(player, 21);
            var ownerRequest = request(player, 1, SET_MODE.id(), 1, Optional.empty(), new byte[]{2});
            var level = helper.getLevel();
            var block = (MachineControllerBlock) fixture.owner.getBlockState().getBlock();
            var replacement = block.newBlockEntity(fixture.owner.getBlockPos(), fixture.owner.getBlockState());
            level.removeBlockEntity(fixture.owner.getBlockPos());
            level.setBlockEntity(replacement);
            dispatch(player, ownerRequest);
            helper.assertTrue(probe.decodes == 3 && probe.handles == 2 && fixture.revision() == 1,
                    "A same-position replacement cannot inherit the original owner authorization");
            fixture.close();
            helper.succeed();
        });
    }

    private static void rejectRegistered(GameTestHelper helper, Fixture fixture, ServerPlayer player, long id,
                                         Identifier message, int version, Optional<String> lane, byte[] bytes, Status status) {
        Probe probe = PROBES.get(player);
        int decodes = probe.decodes;
        int handles = probe.handles;
        dispatch(player, request(player, id, message, version, lane, bytes));
        helper.assertTrue(packets(player, PktControllerUiResponsePayload.class).getLast().status() == status
                        && probe.decodes == decodes && probe.handles == handles && fixture.revision() == 0,
                "Unknown/version/lane rejection precedes every author decoder and handler: " + status);
    }

    private static void providerInvalidation(GameTestHelper helper, Fixture fixture) {
        var closing = fixture.open(61);
        assertProviderBoundary(helper, closing, closing::closeContainer, "closeContainer");
        var unbinding = fixture.open(62);
        assertProviderBoundary(helper, unbinding, fixture.owner::invalidateFormedStructure, "storage unbind");
        helper.assertTrue(fixture.owner.behaviorContext().dataStorage() == null
                        && fixture.mode() == 2,
                "Revision callback genuinely unbinds storage without mutating the former store");
        fixture.owner.requestImmediateStructureCheck();
        fixture.owner.serverTick();
        helper.assertTrue(fixture.owner.structureSnapshot().formed() && fixture.owner.behaviorContext().dataStorage() != null,
                "Real structure check reacquires storage before the owner-removal case");
        var removing = fixture.open(63);
        assertProviderBoundary(helper, removing,
                () -> helper.getLevel().removeBlockEntity(fixture.owner.getBlockPos()), "owner removal");
        helper.assertTrue(fixture.owner.isRemoved()
                        && helper.getLevel().getBlockEntity(fixture.owner.getBlockPos()) != fixture.owner,
                "Revision callback removes the actual bound owner from its level");
    }

    private static void assertProviderBoundary(GameTestHelper helper, ServerPlayer player, Runnable callback, String boundary) {
        Probe probe = PROBES.get(player);
        var session = ui(player).uiServerSession();
        int queries = probe.queries;
        int captures = probe.captures;
        int encodes = probe.encodes;
        int states = packets(player, PktControllerUiCustomStatePayload.class).size();
        probe.afterRevision = callback;
        session.broadcastChanges();
        helper.assertTrue(probe.queries == queries + 1 && probe.afterRevision == null
                        && probe.captures == captures && probe.encodes == encodes
                        && packets(player, PktControllerUiCustomStatePayload.class).size() == states
                        && !session.active() && player.containerMenu == player.inventoryMenu,
                "Provider revision " + boundary + " stops before snapshot, encoder and state delivery");
        session.broadcastChanges();
        helper.assertTrue(probe.queries == queries + 1 && probe.captures == captures && probe.encodes == encodes,
                "Ended provider session never resumes polling after " + boundary);
    }

    public static void laneRemoved(GameTestHelper helper) {
        formedFixture(helper, fixture -> {
            fixture.owner.invalidateFormedStructure();
            var player = fixture.open(31);
            var unformedMenu = player.containerMenu;
            var opening = ui(player).uiOpenData();
            int fullCount = packets(player, PktControllerUiSnapshotPayload.class).size();
            var unformed = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
            helper.assertTrue(!unformed.formed() && unformed.kind() == Kind.NORMAL && opening.kind() == Kind.NORMAL,
                    "A legitimate ordinary menu opens before factory formation");
            fixture.owner.requestImmediateStructureCheck();
            fixture.owner.serverTick();
            unformedMenu.broadcastChanges();
            var formed = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
            helper.assertTrue(player.containerMenu == unformedMenu && ui(player).uiServerSession().validate()
                            && packets(player, PktControllerUiSnapshotPayload.class).size() == fullCount + 1
                            && formed.sessionId().equals(opening.sessionId()) && formed.formed()
                            && formed.hasDataStorage() && formed.kind() == Kind.FACTORY && !formed.laneData().isEmpty(),
                    "Real formation publishes a new FACTORY full baseline on the same NORMAL opening and menu token");
            var factory = runtime(fixture.owner).factoryRuntime();
            var recipe = MachineRecipe.fromCanonical(MMCR.id("ui_lane_reservation"), MACHINE_ID, 20,
                    List.of(), List.of(), List.of(), 0, 2, false, false, false, Set.of());
            var first = factory.reservePatternStart(recipe, 1, List.of());
            var second = factory.reservePatternStart(recipe, 1, List.of());
            helper.assertTrue(first != null && second != null && !first.laneId().equals(second.laneId()),
                    "Real pattern planning reserves separate base and factory execution lanes");
            String lane = second.laneId();
            factory.releasePatternStart(first);
            factory.releasePatternStart(second);
            fixture.owner.onPatternStartCommitted();
            dispatch(player, request(player, 1, SET_MODE.id(), 1, Optional.of(lane), new byte[]{1}));
            helper.assertTrue(fixture.mode() == 1 && PROBES.get(player).handles == 1,
                    "A current factory lane is accepted by the actual dispatcher");
            var late = request(player, 2, SET_MODE.id(), 1, Optional.of(lane), new byte[]{2});
            fixture.owner.factoryScheduler().setThreadLimit(1);
            fixture.owner.onPatternStartCommitted();
            helper.assertTrue(fixture.owner.runtimeSnapshot().factory().presentationLanes().stream()
                            .noneMatch(value -> value.laneId().equals(lane))
                            && fixture.owner.structureSnapshot().formed() && ui(player).uiServerSession().validate()
                            && fixture.owner.behaviorContext().dataStorage() != null,
                    "Capacity trim removes a real idle execution lane while menu, formation and storage remain valid");
            int responses = packets(player, PktControllerUiResponsePayload.class).size();
            dispatch(player, late);
            helper.assertTrue(PROBES.get(player).handles == 1 && PROBES.get(player).decodes == 1 && fixture.mode() == 1
                            && packets(player, PktControllerUiResponsePayload.class).size() == responses + 1
                            && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == 2
                            && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                    "A removed lane is rejected before author decoding even with a still-valid ordinary menu");
            dispatch(player, request(player, 3, SET_MODE.id(), 1, Optional.empty(), new byte[]{2}));
            helper.assertTrue(PROBES.get(player).handles == 2 && fixture.mode() == 2
                            && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == 3
                            && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.SUCCESS,
                    "An untargeted request still writes the actual bound storage after lane removal");
            activeLaneLimitLowering(helper, fixture, player);
            fixture.close();
            helper.succeed();
        });
    }

    private static void activeLaneLimitLowering(GameTestHelper helper, Fixture fixture, ServerPlayer player) {
        var factory = runtime(fixture.owner).factoryRuntime();
        fixture.owner.factoryScheduler().setThreadLimit(2);
        var recipes = List.of(
                MachineRecipe.fromCanonical(MMCR.id("ui_active_base"), MACHINE_ID, 2,
                        List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of()),
                MachineRecipe.fromCanonical(MMCR.id("ui_active_excess"), MACHINE_ID, 2,
                        List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of()));
        var laneIds = new ArrayList<String>();
        for (var recipe : recipes) {
            var reservation = factory.reservePatternStart(recipe, 1, List.of());
            helper.assertTrue(reservation != null && reservation.runtime().commitPatternStart(reservation.preparedStart()),
                    "Real factory pattern commit starts each distinct recipe");
            laneIds.add(reservation.laneId());
            factory.releasePatternStart(reservation);
        }
        fixture.owner.onPatternStartCommitted();
        var menu = player.containerMenu;
        var session = ui(player).uiServerSession();
        menu.broadcastChanges();
        int fullCount = packets(player, PktControllerUiSnapshotPayload.class).size();
        var before = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
        helper.assertTrue(before.activeThreadCount() == 2 && before.laneData().size() == 2
                        && !laneIds.getFirst().equals(laneIds.getLast()),
                "The real factory runs two recipes in separate execution lanes before lowering capacity");
        // Drive recipe work directly within this server turn, without timing-dependent world-tick assertions.
        for (var active : factory.activeRuntimes()) active.tick();
        fixture.owner.factoryScheduler().setThreadLimit(1);
        fixture.owner.onPatternStartCommitted();
        Probe probe = PROBES.get(player);
        int captures = probe.captures;
        long providerRevision = fixture.revision() + 1;
        fixture.storage.storage().set("mode_revision", DataValue.of(providerRevision));
        menu.broadcastChanges();
        var full = packets(player, PktControllerUiSnapshotPayload.class).getLast();
        var wire = ControllerUiPayloadCodec.encodeBounded(PktControllerUiSnapshotPayload.STREAM_CODEC, full,
                helper.getLevel().registryAccess(), ControllerUiPayloadCodec.SNAPSHOT_LIMIT);
        var lowered = ControllerUiPayloadCodec.decodeExact(PktControllerUiSnapshotPayload.STREAM_CODEC, wire,
                helper.getLevel().registryAccess(), ControllerUiPayloadCodec.SNAPSHOT_LIMIT).snapshotData();
        helper.assertTrue(player.containerMenu == menu && session.validate()
                        && lowered.sessionId().equals(before.sessionId()) && !lowered.sameShape(before)
                        && packets(player, PktControllerUiSnapshotPayload.class).size() == fullCount + 1
                        && lowered.threadLimit() == factory.laneLimit() && lowered.threadLimit() == 1
                        && lowered.activeThreadCount() == factory.activeLaneCount() && lowered.activeThreadCount() == 2
                        && lowered.laneData().stream().map(value -> value.id()).toList().equals(laneIds)
                        && probe.captures == captures + 1 && lastState(player).revision() == providerRevision,
                "Lowered capacity sends a faithful new full and advances the provider on the same valid menu session");
        for (int index = 0; index < recipes.size(); index++) {
            var lane = lowered.laneData().get(index);
            var actual = fixture.owner.runtimeSnapshot().factory().presentationLanes().get(index);
            helper.assertTrue(lane.active() && lane.recipeId().orElseThrow().equals(recipes.get(index).id())
                            && lane.tick() == actual.tick() && lane.tick() == 1
                            && lane.totalTick() == actual.totalTick() && lane.recipe().durationTicks() == 2
                            && lane.parallelism() == actual.parallelism(),
                    "Full wire retains each actual active lane's recipe, work progress and presentation");
        }
        // Initial Open must also capture this legal over-limit state, including the dedicated factory menu.
        var viewer = fixture.newPlayer();
        BlockPos pos = fixture.owner.getBlockPos();
        viewer.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var factoryMenu = new FactoryControllerMenu(32, viewer.getInventory(), fixture.owner);
        viewer.containerMenu = factoryMenu;
        ControllerUiEvents.opened(new PlayerContainerEvent.Open(viewer, factoryMenu));
        var reopened = packets(viewer, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
        helper.assertTrue(factoryMenu.uiServerSession().validate() && reopened.threadLimit() == 1
                        && reopened.activeThreadCount() == 2 && reopened.laneData().equals(lowered.laneData())
                        && lastState(viewer).revision() == providerRevision,
                "A dedicated factory menu opened during over-limit work sends its full and initial provider state");
        String excessLane = laneIds.getLast();
        int handles = probe.handles;
        int decodes = probe.decodes;
        dispatch(player, request(player, 4, SET_MODE.id(), 1, Optional.of(excessLane), new byte[]{1}));
        helper.assertTrue(probe.handles == handles + 1 && probe.decodes == decodes + 1 && fixture.mode() == 1
                        && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.SUCCESS
                        && session.laneExists(Optional.of(excessLane)),
                "Still-active excess lane remains an authorized real request target after lowering capacity");
        var late = request(player, 5, SET_MODE.id(), 1, Optional.of(excessLane), new byte[]{2});
        int[] finishes = {0};
        try {
            var workMode = MachineControllerBlockEntity.class.getDeclaredField("activeWorkMode");
            workMode.setAccessible(true);
            var previousMode = workMode.get(fixture.owner);
            try {
                // Drive the real thread completion callbacks synchronously, without changing the global work mode.
                workMode.set(fixture.owner, MachineWorkMode.SYNC);
                int remainingWork = lowered.laneData().stream()
                        .mapToInt(value -> value.totalTick() - value.tick()).max().orElseThrow() + 1;
                for (int step = 1; factory.activeLaneCount() > 0 && step <= remainingWork; step++) {
                    factory.tick(List.of(), 1, () -> finishes[0]++, helper.getLevel().getGameTime() + step);
                }
            } finally {
                workMode.set(fixture.owner, previousMode);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not configure the fixture's synchronous completion", exception);
        }
        helper.assertTrue(finishes[0] == recipes.size() && factory.activeLaneCount() == 0,
                "The real factory scheduler completes both recipes and runs their finish callbacks");
        fixture.owner.onPatternStartCommitted();
        fullCount = packets(player, PktControllerUiSnapshotPayload.class).size();
        menu.broadcastChanges();
        factoryMenu.broadcastChanges();
        var completed = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
        helper.assertTrue(factory.laneCount() == 1 && factory.activeLaneCount() == 0
                        && !completed.sameShape(lowered) && completed.activeThreadCount() == 0
                        && completed.laneData().size() == 1 && completed.laneData().getFirst().id().equals(laneIds.getFirst())
                        && packets(player, PktControllerUiSnapshotPayload.class).size() == fullCount + 1
                        && packets(viewer, PktControllerUiSnapshotPayload.class).getLast().snapshotData().laneData()
                        .equals(completed.laneData()) && session.validate() && factoryMenu.uiServerSession().validate(),
                "Completion trims the actual excess lane and sends the new lane shape to both valid menus"
                        + " (runtime lanes=" + factory.laneCount() + ", active=" + factory.activeLaneCount()
                        + ", ordinary lanes=" + completed.laneData().stream().map(value -> value.id()).toList()
                        + ", ordinary full packets=" + packets(player, PktControllerUiSnapshotPayload.class).size()
                        + ", previous full packets=" + fullCount + ", factory lanes="
                        + packets(viewer, PktControllerUiSnapshotPayload.class).getLast().snapshotData().laneData()
                        .stream().map(value -> value.id()).toList() + ")");
        handles = probe.handles;
        decodes = probe.decodes;
        long revision = fixture.revision();
        dispatch(player, late);
        helper.assertTrue(probe.handles == handles && probe.decodes == decodes && fixture.revision() == revision
                        && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == late.requestId()
                        && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                "The formerly active trimmed lane rejects stale C2S before the author decoder without ending the session");
    }

    public static void slotVisibility(GameTestHelper helper) {
        var player = player(helper);
        var ordinary = new MachineControllerMenu(41, new Inventory(null, null));
        var factory = FactoryControllerMenu.clientOpen(42, new Inventory(null, null));
        for (var menu : List.of(ordinary, factory)) {
            var ui = (ControllerUiMenu) menu;
            var slots = List.copyOf(menu.slots);
            var indexes = menu.slots.stream().map(slot -> slot.index).toList();
            var item = new ItemStack(Items.DIAMOND, 3);
            item.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.test_item"));
            var carried = new ItemStack(Items.GOLD_INGOT, 2);
            menu.slots.getFirst().set(item);
            menu.setCarried(carried);
            ui.setPlayerInventoryVisible(false);
            helper.assertTrue(menu.slots.equals(slots) && menu.slots.stream().map(slot -> slot.index).toList().equals(indexes)
                            && menu.getCarried() == carried && !carried.isEmpty()
                            && menu.slots.stream().noneMatch(slot -> slot.isActive())
                            && menu.slots.getFirst().getItem() == item
                            && item.getCount() == 3 && carried.getCount() == 2
                            && item.get(DataComponents.CUSTOM_NAME).equals(Component.translatable("gui.mmcr.ui.test_item"))
                            && ItemStack.matches(menu.slots.getFirst().getItem(), item),
                    "Hiding deactivates existing slots without replacing indices, contents, components or carried stack");
            ui.setPlayerInventoryVisible(true);
            helper.assertTrue(menu.slots.equals(slots) && menu.slots.stream().allMatch(slot -> slot.isActive())
                            && menu.getCarried() == carried && item.getCount() == 3 && carried.getCount() == 2
                            && item.get(DataComponents.CUSTOM_NAME).equals(Component.translatable("gui.mmcr.ui.test_item"))
                            && ItemStack.matches(menu.slots.getFirst().getItem(), item),
                    "Showing reactivates the same slots and preserves every owned item");
        }
        for (var menu : List.of(new MachineControllerMenu(43, player.getInventory()),
                FactoryControllerMenu.clientOpen(44, player.getInventory()))) {
            var slots = List.copyOf(menu.slots);
            var item = new ItemStack(Items.DIAMOND, 3);
            item.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.test_item"));
            var carried = new ItemStack(Items.GOLD_INGOT, 2);
            menu.slots.getFirst().set(item);
            menu.setCarried(carried);
            ((ControllerUiMenu) menu).setPlayerInventoryVisible(false);
            helper.assertTrue(menu.slots.equals(slots) && menu.slots.stream().allMatch(slot -> slot.isActive())
                            && menu.slots.getFirst().getItem() == item && menu.getCarried() == carried
                            && !carried.isEmpty() && item.getCount() == 3 && carried.getCount() == 2
                            && item.get(DataComponents.CUSTOM_NAME).equals(Component.translatable("gui.mmcr.ui.test_item")),
                    "Real server inventory slots remain active and retain the same component-bearing item and carried objects");
        }
        ((RecordingConnection) player.connection).getConnection().channel().close();
        helper.succeed();
    }

    public static void snapshotOwnership(GameTestHelper helper) {
        try {
            verifySnapshotOwnership(helper);
        } catch (GameTestException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            MMCR.LOG.error("Controller UI snapshot ownership fixture failed", failure);
            throw new IllegalStateException("Controller UI snapshot ownership fixture failed: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage(), failure);
        }
    }

    private static void verifySnapshotOwnership(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 2, 2);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            helper.setBlock(relative.offset(x, y, z), ModBlocks.CASING.get().defaultBlockState());
        }
        helper.setBlock(relative, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var owner = helper.getBlockEntity(relative, MachineControllerBlockEntity.class);
        owner.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        owner.serverTick();
        helper.assertTrue(owner.structureSnapshot().formed(), "Ownership fixture forms the real test cube");
        var player = player(helper);
        BlockPos pos = owner.getBlockPos();
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var menu = new MachineControllerMenu(51, player.getInventory(), owner);
        player.containerMenu = menu;
        ControllerUiEvents.opened(new PlayerContainerEvent.Open(player, menu));
        var name = nestedName();
        var expectedName = nestedName();
        var stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(DataComponents.CUSTOM_NAME, name);
        var fluid = new FluidStack(Fluids.WATER, 750);
        fluid.set(DataComponents.CUSTOM_NAME, name);
        var recipe = MachineRecipe.fromCanonical(MMCR.id("ui_ownership_recipe"), MMCR.id("test_cube"), 20,
                List.of(), List.of(new MachineOutput.ItemOutput(stack, 1F), new MachineOutput.FluidOutput(fluid, 1F)), List.of(),
                0, 1, false, false, false, Set.of());
        var runtime = runtime(owner);
        runtime.craftingRuntime().start(recipe, 1);
        owner.onPatternStartCommitted();
        helper.assertTrue(recipe.id().equals(owner.runtimeSnapshot().crafting().recipeId()),
                "A real recipe start supplies component-bearing runtime output presentation");
        owner.behaviorContext().screenText().append(ControllerScreenTextScope.CONTROLLER, MMCR.id("ui_owned_global"), name);
        owner.recipeScreenText("base").append(ControllerScreenTextScope.OPERATION, MMCR.id("ui_owned_lane"), name);
        menu.broadcastChanges();
        var packet = packets(player, PktControllerUiSnapshotPayload.class).getLast();
        byte[] wire = ControllerUiPayloadCodec.encodeBounded(PktControllerUiSnapshotPayload.STREAM_CODEC, packet,
                helper.getLevel().registryAccess(), ControllerUiPayloadCodec.SNAPSHOT_LIMIT);
        var decoded = ControllerUiPayloadCodec.decodeExact(PktControllerUiSnapshotPayload.STREAM_CODEC, wire,
                helper.getLevel().registryAccess(), ControllerUiPayloadCodec.SNAPSHOT_LIMIT).snapshotData();
        var view = UiSnapshotAdapters.wrap(decoded);
        var expectedMachineName = view.machineName();
        var coreItem = (MachineOutput.ItemOutput) packet.snapshotData().laneData().getFirst().recipe().outputData()
                .getFirst().resource();
        var coreFluid = (MachineOutput.FluidOutput) packet.snapshotData().laneData().getFirst().recipe().outputData()
                .get(1).resource();
        mutateNestedName(coreItem.stack().get(DataComponents.CUSTOM_NAME), "core item resource getter");
        mutateNestedName(coreFluid.stack().get(DataComponents.CUSTOM_NAME), "core fluid resource getter");
        var first = (ItemOutputView) view.lanes().getFirst().recipe().outputs().getFirst().resource();
        var firstFluid = (FluidOutputView) view.lanes().getFirst().recipe().outputs().get(1).resource();
        var retained = first.stack();
        mutateNestedName(retained.get(DataComponents.CUSTOM_NAME), "same public item view first stack getter");
        var retainedFluid = firstFluid.stack();
        mutateNestedName(retainedFluid.get(DataComponents.CUSTOM_NAME), "same public fluid view first stack getter");
        helper.assertTrue(first.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && firstFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && ((MachineOutput.ItemOutput) owner.runtimeSnapshot().recipePresentation().outputs().getFirst().output())
                        .stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && ((MachineOutput.FluidOutput) owner.runtimeSnapshot().recipePresentation().outputs().get(1).output())
                        .stack().get(DataComponents.CUSTOM_NAME).equals(expectedName),
                "Core resource and repeated public stack reads isolate nested names without modifying the source runtime");
        mutateNestedName(name, "original item/fluid input name after capture");
        for (var output : owner.runtimeSnapshot().recipePresentation().outputs()) {
            switch (output.output()) {
                case MachineOutput.ItemOutput item -> mutateNestedName(item.stack().get(DataComponents.CUSTOM_NAME), "source runtime item output after capture");
                case MachineOutput.FluidOutput liquid -> mutateNestedName(liquid.stack().get(DataComponents.CUSTOM_NAME), "source runtime fluid output after capture");
                default -> throw new AssertionError("Fixture only uses built-in item and fluid outputs");
            }
        }
        stack.setCount(1);
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.closed"));
        fluid.setAmount(1);
        fluid.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.closed"));
        retained.setCount(1);
        retained.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.closed"));
        retainedFluid.setAmount(1);
        retainedFluid.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.mmcr.ui.closed"));
        ((MutableComponent) view.machineName()).append(Component.translatable("gui.mmcr.ui.closed"));
        ((MutableComponent) view.lines().getFirst().text()).append(Component.translatable("gui.mmcr.ui.closed"));
        ((MutableComponent) view.lanes().getFirst().lines().getFirst().text()).append(Component.translatable("gui.mmcr.ui.closed"));
        var second = (ItemOutputView) view.lanes().getFirst().recipe().outputs().getFirst().resource();
        var secondFluid = (FluidOutputView) view.lanes().getFirst().recipe().outputs().get(1).resource();
        helper.assertTrue(second.stack().getCount() == 3 && first.stack().getCount() == 3
                        && second.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && firstFluid.stack().getAmount() == 750 && secondFluid.stack().getAmount() == 750
                        && secondFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && ((MachineOutput.ItemOutput) packet.snapshotData().laneData().getFirst().recipe().outputData()
                        .getFirst().resource()).stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && ((MachineOutput.FluidOutput) packet.snapshotData().laneData().getFirst().recipe().outputData()
                        .get(1).resource()).stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && view.machineName().equals(expectedMachineName)
                        && view.lines().getFirst().text().equals(expectedName)
                        && view.lanes().getFirst().lines().getFirst().text().equals(expectedName)
                        && view.lanes().getFirst().recipe().outputs().getFirst().amount() == 3L
                        && view.lanes().getFirst().recipe().outputs().get(1).amount() == 750L,
                "Source capture, wire and public facade own deep item/fluid components, text and scaled output amounts");
        var workerFailure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {
                var workerOutput = (ItemOutputView) view.lanes().getFirst().recipe().outputs().getFirst().resource();
                var workerFluid = (FluidOutputView) view.lanes().getFirst().recipe().outputs().get(1).resource();
                if (workerOutput.stack().getCount() != 3
                        || !workerOutput.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        || !workerFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        || !view.lines().getFirst().text().equals(expectedName)) {
                    throw new AssertionError("Worker observed mutated snapshot-owned data");
                }
                mutateNestedName(workerOutput.stack().get(DataComponents.CUSTOM_NAME), "worker item stack getter");
                mutateNestedName(workerFluid.stack().get(DataComponents.CUSTOM_NAME), "worker fluid stack getter");
            } catch (Throwable failure) {
                workerFailure.set(failure);
            }
        }, "controller-ui-owned-snapshot");
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Ownership reader was interrupted", interrupted);
        }
        if (workerFailure.get() != null) {
            throw new IllegalStateException("Controller UI snapshot ownership worker read/mutation failed", workerFailure.get());
        }
        helper.assertTrue(second.stack().getCount() == 3
                        && first.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && firstFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName),
                "A foreign UI thread can read owned components and mutate its own copy without affecting another reader");
        // Exercise production full/progress routing using the actual server session's published runtime.
        menu.broadcastChanges();
        var full = packets(player, PktControllerUiSnapshotPayload.class).getLast();
        int fullCount = packets(player, PktControllerUiSnapshotPayload.class).size();
        runtime.craftingRuntime().tick();
        owner.onPatternStartCommitted();
        menu.broadcastChanges();
        var progress = packets(player, PktControllerUiProgressPayload.class).getLast();
        helper.assertTrue(progress.fullBaselineRevision() == full.revision()
                        && progress.progress().getFirst().tick() == 1
                        && packets(player, PktControllerUiSnapshotPayload.class).size() == fullCount,
                "Actual unchanged-shape tick change sends a delta tied to the sent full baseline");
        var merged = UiSnapshotAdapters.wrap(decoded.withProgress(progress.revision(), progress.progress()));
        var mergedItem = (ItemOutputView) merged.lanes().getFirst().recipe().outputs().getFirst().resource();
        var mergedFluid = (FluidOutputView) merged.lanes().getFirst().recipe().outputs().get(1).resource();
        mutateNestedName(mergedItem.stack().get(DataComponents.CUSTOM_NAME), "progress-merged item view stack getter");
        mutateNestedName(mergedFluid.stack().get(DataComponents.CUSTOM_NAME), "progress-merged fluid view stack getter");
        helper.assertTrue(merged.lanes().getFirst().tick() == 1
                        && mergedItem.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && mergedFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && first.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName)
                        && firstFluid.stack().get(DataComponents.CUSTOM_NAME).equals(expectedName),
                "Progress reuses stable owned recipe data without sharing mutable item/fluid names with readers");
        owner.recipeScreenText("base").append(ControllerScreenTextScope.OPERATION,
                MMCR.id("ui_shape"), Component.translatable("gui.mmcr.ui.applied"));
        menu.broadcastChanges();
        helper.assertTrue(packets(player, PktControllerUiSnapshotPayload.class).size() == fullCount + 1,
                "A lane presentation shape change sends a new full baseline");
        player.closeContainer();
        ((RecordingConnection) player.connection).getConnection().channel().close();
        helper.succeed();
    }

    private static MutableComponent nestedName() {
        return Component.translatable("gui.mmcr.ui.test_item", Component.translatable("gui.mmcr.ui.applied"))
                .append(Component.translatable("gui.mmcr.ui.applied"));
    }

    private static void mutateNestedName(Component name, String stage) {
        try {
            var sibling = (MutableComponent) name.getSiblings().getFirst();
            var argument = (MutableComponent) ((TranslatableContents) name.getContents()).getArgs()[0];
            // Canonical decoding may retain immutable sibling lists; style remains mutable on these actual nested nodes.
            sibling.setStyle(sibling.getStyle().withBold(!sibling.getStyle().isBold()));
            argument.setStyle(argument.getStyle().withItalic(!argument.getStyle().isItalic()));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Snapshot ownership nested name mutation failed at " + stage
                    + " (" + failure.getClass().getSimpleName() + ")", failure);
        }
    }

    private static void formedFixture(GameTestHelper helper, Consumer<Fixture> test) {
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
        helper.setBlock(relative.west(), ModBlocks.DATA_STORAGE.get().defaultBlockState());
        helper.setBlock(relative.east(), ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        var owner = helper.getBlockEntity(relative, MachineControllerBlockEntity.class);
        owner.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        var storage = helper.getBlockEntity(relative.west(), DataStorageBlockEntity.class);
        storage.storage().set("mode", DataValue.of(0));
        storage.storage().set("mode_revision", DataValue.of(0L));
        var machine = MachineRegistry.getMachine(MACHINE_ID);
        helper.assertTrue(machine.hasFactory() && machine.portRequirements().requirements().isEmpty(),
                "Registered fixture enables runtime factories and requires no undeclared I/O ports");
        helper.assertTrue(StructureMatcher.matchesRotated(BlockArrayCache.get(machine.pattern(), Direction.SOUTH),
                        helper.getLevel(), owner.getBlockPos()),
                "SCF descriptor matches storage west, controller center and factory east at SOUTH orientation");
        owner.serverTick();
        helper.assertTrue(owner.structureSnapshot().formed() && owner.behaviorContext().dataStorage() != null
                        && owner.runtimeSnapshot().factoryControllerPresent(),
                "Correct descriptor forms and binds real storage and factory components synchronously");
        var fixture = new Fixture(helper, owner, storage);
        try {
            test.accept(fixture);
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /** Actual owner/storage/player fixture; no alternate dispatcher or protocol registry.
     * @author howxu <dev@howxu.cn> */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final MachineControllerBlockEntity owner;
        private final DataStorageBlockEntity storage;
        private final List<ServerPlayer> players = new ArrayList<>();
        private Fixture(GameTestHelper helper, MachineControllerBlockEntity owner, DataStorageBlockEntity storage) {
            this.helper = helper;
            this.owner = owner;
            this.storage = storage;
        }
        private ServerPlayer newPlayer() {
            var player = player(helper);
            players.add(player);
            var probe = new Probe();
            PROBES.put(player, probe);
            VIEWERS.put(player.getId(), probe);
            return player;
        }
        private ServerPlayer open(int containerId) {
            var player = newPlayer();
            reopen(player, containerId);
            return player;
        }
        private void reopen(ServerPlayer player, int containerId) {
            player.closeContainer();
            BlockPos pos = owner.getBlockPos();
            player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            var menu = new MachineControllerMenu(containerId, player.getInventory(), owner);
            player.containerMenu = menu;
            ControllerUiEvents.opened(new PlayerContainerEvent.Open(player, menu));
            helper.assertTrue(menu.uiServerSession().validate(), "The actual menu session is installed and activated");
        }
        private int mode() { return storage.storage().get("mode").flatMap(DataValue::asInt).orElse(-1); }
        private long revision() { return storage.storage().get("mode_revision").flatMap(DataValue::asLong).orElse(-1L); }
        @Override public void close() {
            for (var player : players) {
                player.closeContainer();
                ((RecordingConnection) player.connection).getConnection().channel().close();
                PROBES.remove(player);
                VIEWERS.remove(player.getId());
            }
        }
    }

    private static ControllerUiMenu ui(ServerPlayer player) { return (ControllerUiMenu) player.containerMenu; }
    private static ModeState lastState(ServerPlayer player) {
        var packet = packets(player, PktControllerUiCustomStatePayload.class).getLast();
        return ControllerUiPayloadCodec.decodeExact(ModeState.CODEC, packet.body(), player.level().registryAccess(),
                ControllerUiPayloadCodec.STATE_LIMIT);
    }
    private static PktControllerUiRequestPayload request(ServerPlayer player, long id, Identifier message,
                                                        int version, Optional<String> lane, byte[] body) {
        return new PktControllerUiRequestPayload(player.containerMenu.containerId, ui(player).uiOpenData().sessionId(),
                id, message, version, lane, body);
    }
    private static void dispatch(ServerPlayer player, PktControllerUiRequestPayload packet) {
        DECODING.set(PROBES.get(player));
        try {
            if (!player.level().getServer().isSameThread()) throw new AssertionError("GameTest must dispatch on the server thread");
            ControllerUiRequestDispatcher.dispatch(player, packet);
        } finally {
            DECODING.remove();
        }
    }
    private static void setPlayerLevel(ServerPlayer player, Level level) {
        try {
            var field = Entity.class.getDeclaredField("level");
            field.setAccessible(true);
            field.set(player, level);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot switch the fixture player's actual level", exception);
        }
    }

    private static MachineControllerRuntime runtime(MachineControllerBlockEntity owner) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect the real recipe runtime", exception);
        }
    }
    public void openMetadataAndLifecycle(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        var block = (MachineControllerBlock) ModBlocks.controllerFor(MMCR.id("test_cube")).get();
        helper.setBlock(relativePos, block.defaultBlockState());
        BlockPos pos = helper.absolutePos(relativePos);
        var level = helper.getLevel();
        var owner = (MachineControllerBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(owner != null, "A real controller block entity is installed");
        var player = player(helper);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var provider = block.getMenuProvider(owner.getBlockState(), level, pos);
        var pending = provider.createMenu(7, player.getInventory(), player);
        var pendingUi = (ControllerUiMenu) pending;
        var pendingSession = pendingUi.uiServerSession();
        helper.assertTrue(pendingSession != null && pendingSession.pollSnapshot().isEmpty(),
                "Construction does not activate or produce a snapshot");
        helper.assertTrue(((RecordingConnection) player.connection).packets.isEmpty(),
                "Construction does not send a premature controller baseline");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            provider.writeClientSideData(pending, buffer);
            helper.assertTrue(ControllerMenuOpenData.read(buffer).equals(pendingUi.uiOpenData()) && !buffer.isReadable(),
                    "Provider writes metadata from its actual menu while the inventory menu is still installed");
        } finally {
            buffer.release();
        }
        pendingSession.close();
        helper.assertTrue(player.openMenu(provider).isPresent(), "Native open installs the controller menu");
        var firstMenu = player.containerMenu;
        var first = (ControllerUiMenu) firstMenu;
        var session = first.uiServerSession();
        var opening = packets(player, AdvancedOpenScreenPayload.class).getLast();
        var nativeBuffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(opening.additionalData()), level.registryAccess());
        try {
            helper.assertTrue(opening.windowId() == firstMenu.containerId && opening.menuType() == firstMenu.getType()
                            && ControllerMenuOpenData.read(nativeBuffer).equals(first.uiOpenData()) && !nativeBuffer.isReadable(),
                    "Native open carries metadata written from the actual installed menu through AdvancedOpenScreenPayload");
        } finally {
            nativeBuffer.release();
        }
        helper.assertTrue(session != null && session.menu() == firstMenu && session.owner() == owner,
                "Server session binds the actual menu and owner objects");
        var baseline = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
        helper.assertTrue(baseline.ready() && !baseline.formed() && baseline.kind() == Kind.NORMAL
                        && baseline.machineId().equals(MMCR.id("test_cube"))
                        && baseline.sessionId().equals(first.uiOpenData().sessionId()),
                "Unformed menu publishes a ready baseline with its physical/configured identity");
        helper.assertTrue(session.pollSnapshot().isEmpty(), "Unchanged state does not produce a second baseline");
        Probe normalProbe = new Probe();
        PROBES.put(player, normalProbe);
        helper.assertTrue(baseline.laneData().getFirst().id().equals("base"), "Actual NORMAL snapshot publishes base");
        dispatch(player, request(player, 1, SET_MODE.id(), 1, Optional.of("base"), new byte[]{1}));
        helper.assertTrue(normalProbe.decodes == 1 && normalProbe.handles == 1
                        && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.SUCCESS,
                "Actual NORMAL base target reaches the registered handler");
        dispatch(player, request(player, 2, SET_MODE.id(), 1, Optional.of("idle-0"), new byte[]{1}));
        helper.assertTrue(normalProbe.decodes == 1 && normalProbe.handles == 1
                        && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == 2
                        && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                "NORMAL rejects factory placeholders before decoding");
        var stranger = player(helper);
        helper.assertTrue(!session.validate(stranger, firstMenu.containerId, first.uiOpenData().sessionId()),
                "A different player cannot use the bound session token");
        helper.assertTrue(!session.validate(player, firstMenu.containerId, UUID.randomUUID())
                        && session.validate(player, firstMenu.containerId, first.uiOpenData().sessionId()),
                "Authorization requires the menu UUID, not just the container ID");
        owner.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        owner.behaviorContext().screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("ui_global"), Component.literal("global"));
        owner.recipeScreenText("base").append(ControllerScreenTextScope.OPERATION,
                MMCR.id("ui_lane"), Component.literal("lane"));
        var textUpdate = session.pollSnapshot().orElseThrow(() -> new AssertionError("Changed text must be captured"));
        helper.assertTrue(textUpdate.revision() > baseline.revision() && textUpdate.lines().size() == 1
                        && textUpdate.laneData().getFirst().lines().size() == 1 && session.pollSnapshot().isEmpty(),
                "Global and lane text are captured once per revision and unchanged text is suppressed");
        player.closeContainer();
        session.close();
        session.close();
        session.activate(player);
        helper.assertTrue(session.pollSnapshot().isEmpty(), "Close is idempotent and cannot reactivate");

        // Deliberately reuse the container ID to exercise the UUID/actual-object boundary.
        AbstractContainerMenu secondMenu = provider.createMenu(firstMenu.containerId, player.getInventory(), player);
        var second = (ControllerUiMenu) secondMenu;
        var secondSession = second.uiServerSession();
        helper.assertTrue(!first.uiOpenData().sessionId().equals(second.uiOpenData().sessionId()),
                "Reused container IDs receive a new session UUID");
        player.containerMenu = secondMenu;
        ControllerUiEvents.opened(new PlayerContainerEvent.Open(player, secondMenu));
        firstMenu.removed(player);
        helper.assertTrue(secondSession.validate() && packets(player, PktControllerUiSnapshotPayload.class).getLast()
                        .sessionId().equals(second.uiOpenData().sessionId()),
                "Late removal of an old menu cannot close the new session or its delivered baseline");
        owner.setMachine(MachineRegistry.getMachine(MMCR.id("data_storage_tick")));
        helper.assertTrue(secondSession.pollSnapshot().isEmpty() && player.containerMenu == player.inventoryMenu,
                "A changed machine identity invalidates and closes the actual container");

        owner.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        player.openMenu(provider);
        var replacedSession = ((ControllerUiMenu) player.containerMenu).uiServerSession();
        var replacement = block.newBlockEntity(pos, owner.getBlockState());
        level.removeBlockEntity(pos);
        level.setBlockEntity(replacement);
        helper.assertTrue(replacedSession.pollSnapshot().isEmpty() && player.containerMenu == player.inventoryMenu,
                "Replacement at the same position cannot inherit the original owner authorization");
        player.openMenu(owner.getBlockState().getMenuProvider(level, pos));
        var distantSession = ((ControllerUiMenu) player.containerMenu).uiServerSession();
        player.setPos(pos.getX() + 16, pos.getY(), pos.getZ());
        helper.assertTrue(distantSession.pollSnapshot().isEmpty() && player.containerMenu == player.inventoryMenu,
                "Menu stillValid is enforced before capturing a snapshot");
        helper.setBlock(relativePos, ModBlocks.controllerFor(MMCR.id("data_storage_tick")).get().defaultBlockState());
        helper.setBlock(relativePos.west(), ModBlocks.DATA_STORAGE.get().defaultBlockState());
        var tickOwner = helper.getBlockEntity(relativePos, MachineControllerBlockEntity.class);
        tickOwner.setMachine(MachineRegistry.getMachine(MMCR.id("data_storage_tick")));
        tickOwner.serverTick();
        helper.assertTrue(tickOwner.structureSnapshot().formed(), "TICK fixture forms the working controller/storage pattern");
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        player.openMenu(tickOwner.getBlockState().getMenuProvider(level, pos));
        var tickBaseline = packets(player, PktControllerUiSnapshotPayload.class).getLast().snapshotData();
        helper.assertTrue(tickBaseline.kind() == Kind.TICK && tickBaseline.laneData().isEmpty(),
                "Actual TICK snapshot has no target lanes");
        long id = 1;
        for (String lane : List.of("base", "idle-0", "factory-0", "arbitrary")) {
            dispatch(player, request(player, id, SET_MODE.id(), 1, Optional.of(lane), new byte[]{1}));
            helper.assertTrue(normalProbe.decodes == 1 && normalProbe.handles == 1
                            && packets(player, PktControllerUiResponsePayload.class).getLast().requestId() == id
                            && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.INVALID_REQUEST,
                    "TICK rejects every supplied lane before author decoding: " + lane);
            id++;
        }
        dispatch(player, request(player, id, SET_MODE.id(), 1, Optional.empty(), new byte[]{1}));
        helper.assertTrue(normalProbe.decodes == 2 && normalProbe.handles == 2
                        && packets(player, PktControllerUiResponsePayload.class).getLast().status() == Status.SUCCESS,
                "Untargeted TICK request remains usable with the same registered protocol");
        player.closeContainer();
        PROBES.remove(player);
        ((RecordingConnection) player.connection).getConnection().channel().close();
        ((RecordingConnection) stranger.connection).getConnection().channel().close();
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "mmcr-ui"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static <T> List<T> packets(ServerPlayer player, Class<T> type) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(type::isInstance).map(type::cast).toList();
    }

    /** Native connection fixture for real menu open/close without a client.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(MinecraftServer server, ServerPlayer player) {
            super(server, connected(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        private static Connection connected() {
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);
            return connection;
        }

        @Override public void send(Packet<?> packet) { packets.add(packet); }
    }
}

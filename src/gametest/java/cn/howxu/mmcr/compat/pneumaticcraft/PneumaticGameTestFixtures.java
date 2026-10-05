package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.type.CapabilityCreationContext;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortBlockEntity;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.common.block.entity.compressor.AirCompressorBlockEntity;
import me.desht.pneumaticcraft.common.block.entity.tube.PressureTubeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Test-local native fixtures with explicit synchronous stepping. @author howxu <dev@howxu.cn> */
public final class PneumaticGameTestFixtures {
    private PneumaticGameTestFixtures() {}

    public static AirPortBlockEntity port(GameTestHelper helper, BlockPos pos, IOType io) {
        String id = io == IOType.INPUT ? PneumaticIds.INPUT : PneumaticIds.OUTPUT;
        helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
        AirPortBlockEntity port = helper.getBlockEntity(pos);
        port.onLoad();
        return port;
    }

    public static PressureTubeBlockEntity tube(GameTestHelper helper, BlockPos pos, Direction... openFaces) {
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(PneumaticIds.ADVANCED_PRESSURE_TUBE).defaultBlockState());
        PressureTubeBlockEntity tube = helper.getBlockEntity(pos);
        tube.onLoad();
        EnumSet<Direction> open = EnumSet.noneOf(Direction.class);
        open.addAll(List.of(openFaces));
        for (Direction side : Direction.values()) tube.setSideClosed(side, !open.contains(side));
        tube.initializeHullAirHandlers();
        air(helper, tube, null).setSideLeaking(null);
        return tube;
    }

    public static AirCompressorBlockEntity compressor(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pneumaticcraft:air_compressor"))
                .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        helper.setBlock(pos, state);
        AirCompressorBlockEntity compressor = helper.getBlockEntity(pos);
        compressor.onLoad();
        air(helper, compressor, null).setSideLeaking(null);
        return compressor;
    }

    public static IAirHandlerMachine air(GameTestHelper helper, BlockEntity entity, Direction side) {
        var handler = helper.getLevel().getCapability(PNCCapabilities.AIR_HANDLER_MACHINE, entity.getBlockPos(), side);
        helper.assertTrue(handler != null, "Real native air capability is registered");
        return handler;
    }

    /** Sets exact native test state, including NBT-only extrema which addAir would floor/overflow. */
    public static void setAir(IAirHandlerMachine handler, int amount) {
        CompoundTag state = ((CompoundTag) handler.serializeNBT()).copy();
        state.putInt("Air", amount);
        handler.deserializeNBT(state);
    }

    @SuppressWarnings("unchecked")
    public static void tickNative(GameTestHelper helper, BlockEntity entity) {
        var ticker = entity.getBlockState().getTicker(helper.getLevel(), (BlockEntityType<BlockEntity>) entity.getType());
        helper.assertTrue(ticker != null, "Real block exposes a server ticker");
        ticker.tick(helper.getLevel(), entity.getBlockPos(), entity.getBlockState(), entity);
    }

    public static MachineCapability bindingCapability(AirPortBlockEntity port) {
        return port.kind().bindings().getFirst().factory().create(new CapabilityCreationContext() {
            @Override public CapabilityHost host() { return port; }
            @Override public IOType ioType() { return port.ioType(); }
            @Override public <T> Optional<T> service(Class<T> type) {
                return type.isInstance(port) ? Optional.of(type.cast(port)) : Optional.empty();
            }
            @Override public Runnable onChanged() { return port::observeAirChanges; }
        });
    }

    public static RecordingController recordingController(GameTestHelper helper, BlockPos pos) {
        BlockState state = ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState();
        helper.setBlock(pos, state);
        helper.getLevel().removeBlockEntity(helper.absolutePos(pos));
        RecordingController controller = new RecordingController(helper.absolutePos(pos), state);
        helper.getLevel().setBlockEntity(controller);
        return controller;
    }

    public static TrackingObserver trackingObserver(GameTestHelper helper, BlockPos absolutePos) {
        return new TrackingObserver(helper, absolutePos);
    }

    /** Flushes the actual vanilla chunk holder to a tracking connection without waiting on world ticks.
     * @author howxu <dev@howxu.cn>
     */
    public static final class TrackingObserver implements AutoCloseable {
        private final GameTestHelper helper;
        private final ChunkPos chunkPos;
        private final PlayerMap players;
        private final ServerPlayer player;
        private final ServerPlayer nonTrackingPlayer;

        private TrackingObserver(GameTestHelper helper, BlockPos pos) {
            this.helper = helper;
            chunkPos = new ChunkPos(pos);
            var server = helper.getLevel().getServer();
            player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "mmcr-air-tracker"),
                    ClientInformation.createDefault());
            player.connection = new RecordingConnection(player);
            player.setChunkTrackingView(ChunkTrackingView.of(chunkPos, 2));
            nonTrackingPlayer = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "mmcr-air-outsider"),
                    ClientInformation.createDefault());
            nonTrackingPlayer.connection = new RecordingConnection(nonTrackingPlayer);
            try {
                var field = ChunkMap.class.getDeclaredField("playerMap");
                field.setAccessible(true);
                players = (PlayerMap) field.get(helper.getLevel().getChunkSource().chunkMap);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Unable to inspect vanilla chunk tracking", exception);
            }
            players.addPlayer(player, false);
            players.addPlayer(nonTrackingPlayer, false);
            helper.assertTrue(helper.getLevel().getChunkSource().chunkMap.getPlayers(chunkPos, false).contains(player)
                            && !helper.getLevel().getChunkSource().chunkMap.getPlayers(chunkPos, false).contains(nonTrackingPlayer),
                    "Only the observer tracks the actual port chunk, without opening a menu");
            flush();
            clear();
        }

        public void flush() {
            var holder = helper.getLevel().getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos.toLong());
            helper.assertTrue(holder != null && holder.getTickingChunk() != null, "Port has a real ticking chunk holder");
            holder.broadcastChanges(helper.getLevel().getChunk(chunkPos.x, chunkPos.z));
            helper.assertTrue(((RecordingConnection) nonTrackingPlayer.connection).packets.isEmpty(),
                    "Non-tracking connection receives no block entity updates");
        }

        public List<ClientboundBlockEntityDataPacket> updates(BlockPos pos) {
            return ((RecordingConnection) player.connection).packets.stream()
                    .filter(ClientboundBlockEntityDataPacket.class::isInstance)
                    .map(ClientboundBlockEntityDataPacket.class::cast)
                    .filter(packet -> packet.getPos().equals(pos)).toList();
        }

        public void clear() {
            ((RecordingConnection) player.connection).packets.clear();
        }

        @Override
        public void close() {
            players.removePlayer(player);
            players.removePlayer(nonTrackingPlayer);
        }
    }

    /** Records vanilla packets delivered to the real chunk tracking subscriber.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(ServerPlayer player) {
            super(player.getServer(), new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }

    /** Records actual port-to-controller notifications on a real ServerLevel. @author howxu <dev@howxu.cn> */
    public static final class RecordingController extends MachineControllerBlockEntity {
        public final List<Wakeup> wakeups = new ArrayList<>();

        private RecordingController(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public void notifyResourceAvailability(ResourceAvailabilityNotifier.Reason reason, Object resource, BlockPos sourcePos) {
            wakeups.add(new Wakeup(reason, resource, sourcePos));
            super.notifyResourceAvailability(reason, resource, sourcePos);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public record Wakeup(ResourceAvailabilityNotifier.Reason reason, Object resource, BlockPos sourcePos) {}
}

package cn.howxu.mmcr;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.minecraft.world.item.ItemStack;
import cn.howxu.mmcr.internal.storage.LongItemStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real registry-backed port baselines, shared snapshots and native wire round trips.
 * @author howxu <dev@howxu.cn>
 */
public final class PortStorageSyncGameTest {
    public static void openingRegistersFreshBaselineAndViewersShareFinalPayload(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        BlockState state = ModBlocks.BLOCKS.get(PortKinds.EXTENDED_ITEM_INPUT.id()).get().defaultBlockState();
        helper.setBlock(relative, state);
        var port = new CountingPort(helper.absolutePos(relative), state);
        helper.getLevel().setBlockEntity(port);
        port.capabilitySnapshot(); // Bind the native handler before measuring subsequent snapshot reads.
        var first = player(helper);
        var second = player(helper);
        var replica = new ExtendedItemMenu(99, new Inventory(first), port.getBlockPos(), port.kind().id(),
                port.nativeItemHandler().getSlots());
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        try {
            insert(helper, port, iron, 7L);
            open(helper, port, first);
            open(helper, port, second);
            helper.assertTrue(packets(first).size() == 1 && packets(second).size() == 1,
                    "Each actual menu open delivers one native item baseline");
            var initial = packets(first).getFirst();
            assertContents(helper, initial, port, replica, iron, 7L);
            assertContents(helper, packets(second).getFirst(), port, replica, iron, 7L);

            insert(helper, port, iron, 10L);
            insert(helper, port, iron, 10L);
            helper.assertTrue(packets(first).size() == 1 && packets(second).size() == 1,
                    "Committed mutations defer delivery until flush");
            port.captures = 0;
            port.nativeSnapshots = 0;
            port.flushStorageChanges();
            helper.assertTrue(port.captures == 1 && port.nativeSnapshots == 1
                            && packets(first).size() == 2 && packets(second).size() == 2,
                    "Two viewers share one native payload snapshot after the availability capture");
            var shared = packets(first).getLast();
            helper.assertTrue(shared == packets(second).getLast() && shared != initial,
                    "Both viewer deliveries reuse the same final payload object");
            assertContents(helper, shared, port, replica, iron, 27L);
            assertContents(helper, initial, port, replica, iron, 7L);
            port.flushStorageChanges();
            helper.assertTrue(port.captures == 1 && port.nativeSnapshots == 1
                            && packets(first).size() == 2 && packets(second).size() == 2,
                    "A clean repeated flush does no snapshot or delivery work");

            first.closeContainer();
            second.closeContainer();
            insert(helper, port, iron, 10L);
            port.captures = 0;
            port.nativeSnapshots = 0;
            port.flushStorageChanges();
            helper.assertTrue(port.captures == 1 && port.nativeSnapshots == 0
                            && packets(first).size() == 2 && packets(second).size() == 2,
                    "Closed viewers are pruned; viewerless flush only captures availability without a native payload snapshot");

            open(helper, port, first);
            helper.assertTrue(port.nativeSnapshots > 0
                            && packets(first).size() == 3 && packets(second).size() == 2
                            && packets(first).getLast() != shared,
                    "Late reopen captures a fresh native baseline after viewerless publication");
            assertContents(helper, packets(first).getLast(), port, replica, iron, 37L);
            assertContents(helper, shared, port, replica, iron, 27L);
        } finally {
            first.closeContainer();
            second.closeContainer();
            PktPortStorageSyncPayload.sendToViewers(port);
            ((RecordingConnection) first.connection).getConnection().channel().close();
            ((RecordingConnection) second.connection).getConnection().channel().close();
        }
        helper.succeed();
    }

    private static void insert(GameTestHelper helper, CountingPort port, ItemStack resource, long amount) {
        helper.assertTrue(((LongItemStorage) port.itemHandler()).forceInsert(0, resource, amount, false) == amount,
                "The actual extended item port commits the requested items");
    }

    private static void open(GameTestHelper helper, CountingPort port, ServerPlayer player) {
        BlockPos pos = port.getBlockPos();
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        port.getBlockState().useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(player.containerMenu instanceof ExtendedItemMenu menu && menu.owner() == port,
                "Actual block interaction opens the registered extended item menu for this port");
    }

    private static void assertContents(GameTestHelper helper, PktPortStorageSyncPayload payload, CountingPort port,
                                       ExtendedItemMenu replica, ItemStack resource, long amount) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PktPortStorageSyncPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = PktPortStorageSyncPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0 && decoded.pos().equals(port.getBlockPos())
                            && decoded.kind().equals(port.kind().id()) && decoded.itemEntries().size() == 1
                            && decoded.fluidEntries().isEmpty(),
                    "Recorded native port payload round-trips its target and single item entry");
            replica.applySnapshot(decoded);
            var entry = replica.entries().getFirst();
            helper.assertTrue(entry.slot() == 0 && ItemStack.isSameItemSameComponents(resource, entry.resource())
                            && entry.amount() == amount && entry.capacity() >= entry.amount(),
                    "Production menu applies the real registered resource and committed long amount " + amount);
        } finally {
            buffer.release();
        }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "mmcr-port-sync"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static List<PktPortStorageSyncPayload> packets(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(PktPortStorageSyncPayload.class::isInstance).map(PktPortStorageSyncPayload.class::cast).toList();
    }

    /** Counts availability capture separately from actual native payload reads.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CountingPort extends ExtendedItemBusBlockEntity {
        private int captures;
        private int nativeSnapshots;

        private CountingPort(BlockPos pos, BlockState state) { super(pos, state); }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            captures++;
            return super.capabilitySnapshot();
        }
        @Override public LongItemStorage nativeItemHandler() {
            nativeSnapshots++;
            return super.nativeItemHandler();
        }
    }

    /** Native connected player fixture, following the existing controller UI GameTest pattern.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(MinecraftServer server, ServerPlayer player) {
            super(server, connected(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
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

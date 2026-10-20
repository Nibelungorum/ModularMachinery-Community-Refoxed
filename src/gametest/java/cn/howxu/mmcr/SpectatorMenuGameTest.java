package cn.howxu.mmcr;

import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedflux.AppliedFluxBridge;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import cn.howxu.mmcr.internal.block.UpgradeBusBlock;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exercises native interaction and the registered menu factories without a client.
 * @author howxu <dev@howxu.cn>
 */
public class SpectatorMenuGameTest {
    public static void allBlockMenusCarryOpeningData(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        BlockPos pos = helper.absolutePos(relativePos);
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "mmcr-menu"),
                ClientInformation.createDefault());
        var connection = new RecordingConnection(level.getServer(), player);
        player.connection = connection;
        player.setPos(Vec3.atCenterOf(pos));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        try {
            for (var entry : ModBlocks.BLOCKS.entrySet()) {
                var block = entry.getValue().get();
                if (!(block instanceof IOPortBlock || block instanceof UpgradeBusBlock
                        || block instanceof SmartInterfaceBlock || block instanceof FactorySchedulerBlock)) continue;
                boolean unsupported = block instanceof IOPortBlock port
                        && (AE2Bridge.get().isPort(port.kind().id()) || AppliedFluxBridge.get().isPort(port.kind().id()));
                boolean nativeMenu = entry.getKey().equals(FluxNetworksIds.INPUT) || entry.getKey().equals(FluxNetworksIds.OUTPUT);
                // Synchronous reuse keeps every registered variant inside this test's footprint.
                helper.setBlock(relativePos, block.defaultBlockState());
                for (GameType mode : List.of(GameType.SPECTATOR, GameType.SURVIVAL)) {
                    if (unsupported && mode != GameType.SPECTATOR) continue;
                    player.setGameMode(mode);
                    connection.packets.clear();
                    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
                    String route = entry.getKey() + " / " + mode;
                    var openings = payloads(connection, AdvancedOpenScreenPayload.class);
                    if (nativeMenu) {
                        helper.assertTrue(openings.size() == 1
                                        && connection.packets.stream().noneMatch(ClientboundOpenScreenPacket.class::isInstance)
                                        && player.containerMenu != player.inventoryMenu
                                        && openings.getFirst().menuType() == player.containerMenu.getType(),
                                "Native Flux menu retains its data-bearing opening protocol: " + route);
                        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(openings.getFirst().additionalData()), level.registryAccess());
                        try {
                            helper.assertTrue(buffer.readBoolean() && buffer.readBlockPos().equals(pos)
                                            && buffer.readNbt() != null && !buffer.isReadable(),
                                    "Native Flux opening carries the device marker, position and update tag: " + route);
                        } finally {
                            buffer.release();
                        }
                        player.closeContainer();
                        continue;
                    }
                    helper.assertTrue(connection.packets.stream().noneMatch(ClientboundOpenScreenPacket.class::isInstance),
                            "No initialization-free opening packet: " + route);
                    if (unsupported) {
                        helper.assertTrue(player.containerMenu == player.inventoryMenu && openings.isEmpty(),
                                "Native spectator path safely rejects independently opened interfaces: " + route);
                        continue;
                    }
                    helper.assertTrue(openings.size() == 1 && player.containerMenu != player.inventoryMenu,
                            "Native interaction sends one data-bearing opening: " + route);
                    var opening = openings.getFirst();
                    var serverMenu = player.containerMenu;
                    helper.assertTrue(opening.menuType() == serverMenu.getType(), "Opening selects the server type: " + route);
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(opening.additionalData()), level.registryAccess());
                    try {
                        int start = buffer.readerIndex();
                        helper.assertTrue(buffer.readBlockPos().equals(pos), "Opening retains the target position: " + route);
                        buffer.readerIndex(start);
                        var clientMenu = opening.menuType().create(opening.windowId(), player.getInventory(), buffer);
                        helper.assertTrue(clientMenu.getClass() == serverMenu.getClass() && !buffer.isReadable(),
                                "Registered factory consumes exactly one matching initialization: " + route);
                        assertSlotLayout(helper, serverMenu, clientMenu, route);
                    } finally {
                        buffer.release();
                    }
                    if (serverMenu instanceof ExtendedItemMenu || serverMenu instanceof ExtendedFluidMenu
                            || serverMenu instanceof CombinedPortMenu || serverMenu instanceof ExtendedCombinedMenu) {
                        var baseline = payloads(connection, PktPortStorageSyncPayload.class);
                        helper.assertTrue(baseline.size() == 1 && baseline.getFirst().pos().equals(pos)
                                        && baseline.getFirst().kind().equals(entry.getKey()),
                                "Storage baseline is sent once after opening: " + route);
                        var openingPacket = connection.packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload() == opening).findFirst().orElseThrow();
                        var baselinePacket = connection.packets.stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload() == baseline.getFirst()).findFirst().orElseThrow();
                        helper.assertTrue(connection.packets.indexOf(openingPacket) < connection.packets.indexOf(baselinePacket),
                                "Storage baseline arrives after the menu opening: " + route);
                        connection.packets.clear();
                        PktPortStorageSyncPayload.sendToViewers((IOPortBlockEntity) level.getBlockEntity(pos));
                        helper.assertTrue(payloads(connection, PktPortStorageSyncPayload.class).size() == 1,
                                "Opening subscribes the viewer to later storage updates: " + route);
                    }
                    player.closeContainer();
                }
                if (block instanceof IOPortBlock) {
                    var provider = level.getBlockState(pos).getMenuProvider(level, pos);
                    helper.setBlock(relativePos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(player.openMenu(provider).isEmpty(), "Removed block refuses a retained provider: " + entry.getKey());
                    helper.setBlock(relativePos, Blocks.CHEST.defaultBlockState());
                    helper.assertTrue(player.openMenu(provider).isEmpty(), "Wrong target type refuses a retained provider: " + entry.getKey());
                    String replacement = entry.getKey().equals("item_input_bus") ? "item_output_bus" : "item_input_bus";
                    helper.setBlock(relativePos, ModBlocks.BLOCKS.get(replacement).get().defaultBlockState());
                    helper.assertTrue(player.openMenu(provider).isEmpty(), "Wrong port kind refuses a retained provider: " + entry.getKey());
                }
            }
        } finally {
            player.closeContainer();
            connection.getConnection().channel().close();
        }
        helper.succeed();
    }

    private static void assertSlotLayout(GameTestHelper helper, AbstractContainerMenu serverMenu,
                                         AbstractContainerMenu clientMenu, String route) {
        switch (clientMenu) {
            case ExtendedItemMenu client -> {
                var server = (ExtendedItemMenu) serverMenu;
                helper.assertTrue(client.kind().equals(server.kind()) && client.slotCount() == server.slotCount(),
                        "Extended item display dimensions agree: " + route);
            }
            case ExtendedFluidMenu client -> {
                var server = (ExtendedFluidMenu) serverMenu;
                helper.assertTrue(client.kind().equals(server.kind()) && client.tankCount() == server.tankCount(),
                        "Extended fluid display dimensions agree: " + route);
            }
            case CombinedPortMenu client -> {
                var server = (CombinedPortMenu) serverMenu;
                helper.assertTrue(client.kind().equals(server.kind()) && client.itemSlotCount() == server.itemSlotCount()
                                && client.fluidTankCount() == server.fluidTankCount(),
                        "Combined display dimensions agree: " + route);
            }
            case ExtendedCombinedMenu client -> {
                var server = (ExtendedCombinedMenu) serverMenu;
                helper.assertTrue(client.kind().equals(server.kind()) && client.itemSlotCount() == server.itemSlotCount()
                                && client.fluidTankCount() == server.fluidTankCount(),
                        "Extended combined display dimensions agree: " + route);
            }
            default -> { }
        }
        helper.assertTrue(clientMenu.slots.size() == serverMenu.slots.size(), "Client/server slot counts agree: " + route);
        for (int i = 0; i < serverMenu.slots.size(); i++) {
            var serverSlot = serverMenu.slots.get(i);
            var clientSlot = clientMenu.slots.get(i);
            helper.assertTrue(clientSlot.x == serverSlot.x && clientSlot.y == serverSlot.y,
                    "Client/server slot positions agree for slot " + i + ": " + route);
        }
    }

    private static <T> List<T> payloads(RecordingConnection connection, Class<T> type) {
        return connection.packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload())
                .filter(type::isInstance).map(type::cast).toList();
    }

    /** Captures actual server packets while retaining the native menu lifecycle.
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

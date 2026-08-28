package cn.howxu.mmcr;

import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.tile.DataStorageBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DataStorageGameTest {
    private static final Identifier MACHINE_ID = MMCR.id("data_storage_tick");

    public void pureTickWritesBoundStorage(GameTestHelper helper) {
        BlockPos controllerBlockPos = new BlockPos(1, 1, 1);
        BlockPos storageBlockPos = controllerBlockPos.west();
        helper.setBlock(controllerBlockPos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState());
        helper.setBlock(storageBlockPos, ModBlocks.DATA_STORAGE.get().defaultBlockState());

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerBlockPos, MachineControllerBlockEntity.class);
        DataStorageBlockEntity storage = helper.getBlockEntity(storageBlockPos, DataStorageBlockEntity.class);
        BlockPos controllerPos = controller.getBlockPos();
        storage.storage().set("ticks", DataValue.of(0L));
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        ControllerScreenTextCache.clear(controllerPos);
        ServerPlayer observer = observer(helper);
        observer.containerMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        helper.getLevel().players().add(observer);

        helper.runAtTickTime(80, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Pure-tick structure formed");
            helper.assertTrue(controller.structureSnapshot().machine().behavior().kind() == MachineBehavior.Kind.TICK,
                    "Pure-tick machine keeps its TickBehavior");
            helper.assertTrue(controller.behaviorContext().dataStorages().containsKey(storage.getBlockPos()),
                    "Pure-tick behavior context exposes the bound storage");
            long ticks = storage.storage().get("ticks").flatMap(DataValue::asLong).orElse(-1L);
            helper.assertTrue(ticks >= 1L && ticks <= 5L,
                    "Tick behavior writes at a 20-tick period, actual=" + ticks);
            PktControllerScreenTextPayload textPayload = lastScreenTextPacket(observer);
            helper.assertTrue(textPayload != null && hasDynamicText(textPayload.lines()),
                    "Pure-tick callback text is sent in the server payload");
            ControllerScreenTextCache.replace(textPayload.controllerPos(), textPayload.revision(), textPayload.lines());
            ControllerScreenTextCache.clear(controllerPos);
            MachineControllerMenu reopenedMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
            observer.containerMenu = reopenedMenu;
            controller.sendControllerScreenText(observer);
            PktControllerScreenTextPayload reopenedTextPayload = lastScreenTextPacket(observer);
            helper.assertTrue(reopenedTextPayload != null && hasDynamicText(reopenedTextPayload.lines()),
                    "Reopened controller receives the current dynamic text payload");
            helper.assertTrue(ControllerScreenTextCache.replace(reopenedTextPayload.controllerPos(),
                            reopenedTextPayload.revision(), reopenedTextPayload.lines()),
                    "Reopened controller payload replaces the cache snapshot");
            helper.assertTrue(hasDynamicText(ControllerScreenTextCache.linesAt(reopenedMenu.controllerPos())),
                    "Reopened controller cache exposes the dynamic text");
            helper.assertTrue(controller.runtimeSnapshot().crafting().status().getStatus()
                            == CraftingStatus.Status.IDLE,
                    "Pure-tick controller does not start recipe crafting");
            helper.assertTrue(new ControllerSyncRuntime().machineState(controller.runtimeSnapshot()).active(),
                    "Pure-tick controller projects active state");
            helper.assertTrue(controller.getBlockState().getValue(MachineControllerBlock.ACTIVE),
                    "Pure-tick controller block remains active");
            PktMachineStatePayload payload = PktMachineStatePayload.from(controllerPos, controller.runtimeSnapshot());
            helper.assertTrue(payload.active(), "Pure-tick state packet remains active");
            MachineControllerMenu reopened = new MachineControllerMenu(1, new Inventory(null, null), controller);
            helper.assertTrue(reopened.hasActiveRecipe(), "Reopened controller menu reads active state");
            MachineControllerMenu clientMenu = new MachineControllerMenu(1, new Inventory(null, null));
            clientMenu.applyClientSnapshot(payload);
            helper.assertTrue(clientMenu.hasActiveRecipe(), "Client controller menu keeps active state");
            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                    "Pure-tick behavior does not start recipe runtime");
            helper.getLevel().players().remove(observer);
            ControllerScreenTextCache.clear(controllerPos);
            helper.succeed();
        });
    }

    private static boolean hasDynamicText(List<ControllerScreenTextSnapshot.Line> lines) {
        return lines.stream().anyMatch(line -> line.lineId().equals(MMCR.id("data_storage_tick_status"))
                && line.text().getString().startsWith("ticks="));
    }

    private static ServerPlayer observer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-data-storage-observer"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static PktControllerScreenTextPayload lastScreenTextPacket(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .map(packet -> (PktControllerScreenTextPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    /**
     * Captures controller screen text payloads without requiring a network client.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),
                            "mmcr-data-storage-connection"), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}

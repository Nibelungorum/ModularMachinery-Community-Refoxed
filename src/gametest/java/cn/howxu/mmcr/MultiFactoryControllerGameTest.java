package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktControllerScreenTextPayload;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Covers live multi-factory formation and claim cleanup.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MultiFactoryControllerGameTest {

    public void formsWithTwoFactoryControllersAndReformsAfterRelease(GameTestHelper helper) {
        Identifier machineId = MMCR.id("multi_factory_game_test_runtime");
        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        pattern.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        Machine machine = new DynamicMachine(machineId, "Multi Factory GameTest", new BlockArray(pattern),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 4);
        BlockPos controllerPos = new BlockPos(3, 3, 3);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);

        BlockPos firstPos = controllerPos.offset(1, 0, 0);
        BlockPos secondPos = controllerPos.offset(2, 0, 0);
        placeFactory(helper, firstPos);
        placeFactory(helper, secondPos);
        controller.serverTick();

        helper.assertTrue(controller.structureSnapshot().formed(), "two factory controllers should form");
        helper.assertTrue(factoryComponentCount(controller) == 2, "both factory capacities should be aggregated");
        helper.assertTrue(controller.factorySchedulerThreadCount() == 2, "both factory capacities should contribute threads");

        helper.setBlock(secondPos, Blocks.AIR.defaultBlockState());
        controller.onStructureBlockChanged(helper.absolutePos(secondPos));
        for (int tick = 0; tick < 25; tick++) controller.serverTick();
        helper.assertTrue(!controller.structureSnapshot().formed(), "breaking a factory controller should release the structure");
        helper.assertTrue(factoryComponentCount(controller) == 0, "released structure should have no stale capacities");

        placeFactory(helper, secondPos);
        controller.onStructureBlockChanged(helper.absolutePos(secondPos));
        for (int tick = 0; tick < 25; tick++) controller.serverTick();
        helper.assertTrue(controller.structureSnapshot().formed(), "replacing the factory controller should reform");
        helper.assertTrue(factoryComponentCount(controller) == 2, "reformed structure should reacquire both capacities");

        ServerPlayer ordinary = observer(helper, "mmcr-multi-factory-ordinary");
        MachineControllerMenu ordinaryMenu = new MachineControllerMenu(1, new Inventory(null, null), controller);
        ordinary.containerMenu = ordinaryMenu;
        ServerPlayer factory = observer(helper, "mmcr-multi-factory-factory");
        FactoryControllerMenu factoryMenu = new FactoryControllerMenu(2, new Inventory(null, null), controller, factory);
        factory.containerMenu = factoryMenu;
        helper.getLevel().players().addAll(List.of(ordinary, factory));
        helper.assertTrue(ordinary.containerMenu == ordinaryMenu && factory.containerMenu == factoryMenu,
                "ordinary and factory controller menus are active on their players");

        runtime(controller).runtimeContext().screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("factory_status"), Component.literal("factory ready"));
        controller.serverTick();
        ControllerScreenTextSnapshot first = screenTextSnapshot(controller);
        helper.assertTrue(ordinaryMenu.controllerPos().equals(controller.getBlockPos())
                        && factoryMenu.controllerPos().equals(controller.getBlockPos())
                        && first.lines().size() == 1
                        && first.lines().getFirst().text().getString().equals("factory ready"),
                "factory menu uses the controller external snapshot");
        helper.assertTrue(lastScreenTextPacket(ordinary).lines().size() == 1
                        && lastScreenTextPacket(factory).lines().size() == 1,
                "ordinary and factory active menus receive the controller text snapshot");

        runtime(controller).runtimeContext().screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("factory_status"), Component.literal("factory updated"));
        int ordinaryPackets = screenTextPackets(ordinary);
        int factoryPackets = screenTextPackets(factory);
        controller.serverTick();
        ControllerScreenTextSnapshot updated = screenTextSnapshot(controller);
        helper.assertTrue(updated.lines().size() == 1
                        && updated.lines().getFirst().text().getString().equals("factory updated"),
                "factory runtime propagates a keyed text update");
        helper.assertTrue(screenTextPackets(ordinary) == ordinaryPackets + 1
                        && screenTextPackets(factory) == factoryPackets + 1,
                "ordinary and factory active menus receive the updated snapshot");

        ordinaryPackets = screenTextPackets(ordinary);
        factoryPackets = screenTextPackets(factory);
        controller.invalidateFormedStructure();
        helper.assertTrue(screenTextSnapshot(controller).lines().isEmpty()
                        && screenTextPackets(ordinary) == ordinaryPackets + 1
                        && screenTextPackets(factory) == factoryPackets + 2
                        && lastScreenTextPacket(ordinary).lines().isEmpty()
                        && lastScreenTextPacket(factory).lines().isEmpty(),
                "factory controller reset clears and synchronizes external text");
        helper.getLevel().players().removeAll(List.of(ordinary, factory));
        helper.succeed();
    }

    private static long factoryComponentCount(MachineControllerBlockEntity controller) {
        return controller.componentRuntime().components().stream()
                .filter(component -> component.getContainer() instanceof FactorySchedulerBlockEntity)
                .count();
    }

    private static void placeFactory(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        helper.getBlockEntity(pos, FactorySchedulerBlockEntity.class);
    }

    private static ControllerScreenTextSnapshot screenTextSnapshot(MachineControllerBlockEntity controller) {
        return runtime(controller).screenText().snapshot();
    }

    private static MachineControllerRuntime runtime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect controller text snapshot", exception);
        }
    }

    private static ServerPlayer observer(GameTestHelper helper, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static int screenTextPackets(ServerPlayer player) {
        return (int) ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .count();
    }

    private static PktControllerScreenTextPayload lastScreenTextPacket(ServerPlayer player) {
        return ((RecordingConnection) player.connection).packets.stream()
                .filter(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof PktControllerScreenTextPayload)
                .map(packet -> (PktControllerScreenTextPayload) ((ClientboundCustomPayloadPacket) packet).payload())
                .reduce((first, second) -> second)
                .orElseThrow();
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
                    CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mmcr-multi-factory-connection"), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }
    }
}

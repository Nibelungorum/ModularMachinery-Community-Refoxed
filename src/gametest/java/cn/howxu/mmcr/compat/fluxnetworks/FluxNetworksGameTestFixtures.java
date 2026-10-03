package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.FluxNetworkData;
import sonar.fluxnetworks.common.device.TileFluxDevice;
import sonar.fluxnetworks.common.device.TileFluxStorage;
import sonar.fluxnetworks.register.RegistryBlocks;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Real registered devices and test-local native networks, driven on the server thread.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksGameTestFixtures {
    public static final BlockPos CONTROLLER = new BlockPos(2, 1, 1);
    public static final BlockPos STORAGE = new BlockPos(1, 1, 3);

    private FluxNetworksGameTestFixtures() {}

    public static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-flux-test"), ClientInformation.createDefault());
        player.connection = new TestConnection(player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        return player;
    }

    public static FluxNetwork createNetwork(GameTestHelper helper, ServerPlayer owner) {
        FluxNetwork network = FluxNetworkData.getInstance().createNetwork(owner, "mmcr_gametest",
                0x66AAFF, SecurityLevel.PUBLIC, "");
        helper.assertTrue(network != null && network.isValid(), "Native network manager creates the test's network");
        return network;
    }

    public static FluxNetworkInputBlockEntity input(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get().defaultBlockState());
        FluxNetworkInputBlockEntity input = helper.getBlockEntity(pos);
        helper.assertTrue(input.getType() == ModBlockEntities.BES.get(FluxNetworksIds.INPUT).get()
                        && input.getType().isValid(input.getBlockState()),
                "Registered Point block creates its own valid MMCR block entity type");
        return input;
    }

    public static FluxNetworkOutputBlockEntity output(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get().defaultBlockState());
        FluxNetworkOutputBlockEntity output = helper.getBlockEntity(pos);
        helper.assertTrue(output.getType() == ModBlockEntities.BES.get(FluxNetworksIds.OUTPUT).get()
                        && output.getType().isValid(output.getBlockState()),
                "Registered Plug block creates its own valid MMCR block entity type");
        return output;
    }

    public static TileFluxStorage storage(GameTestHelper helper, BlockPos pos, long energy) {
        return storage(helper.getLevel(), helper.absolutePos(pos), energy);
    }

    public static TileFluxStorage storage(ServerLevel level, BlockPos pos, long energy) {
        level.setBlock(pos, RegistryBlocks.BASIC_FLUX_STORAGE.get().defaultBlockState(), 3);
        TileFluxStorage storage = (TileFluxStorage) level.getBlockEntity(pos);
        storage.getTransferHandler().setLimit(1000L);
        storage.getTransferHandler().addToBuffer(energy);
        tickDevice(storage);
        return storage;
    }

    public static void tickDevice(TileFluxDevice device) {
        TileFluxDevice.<TileFluxDevice>getTicker(device.getLevel()).tick(device.getLevel(),
                device.getBlockPos(), device.getBlockState(), device);
    }

    public static void connectAndCycle(TileFluxDevice device, FluxNetwork network) {
        tickDevice(device);
        if (!device.connect(network)) throw new AssertionError("Native connect rejected a test device");
        network.onEndServerTick();
    }

    public static void closeNetwork(FluxNetwork network) {
        // Flush pending additions before removal: upstream only removes devices already in ANY.
        network.onEndServerTick();
        for (TileFluxDevice device : List.copyOf(network.getLogicalDevices(FluxNetwork.ANY))) {
            device.disconnect();
            network.enqueueConnectionRemoval(device, false);
        }
        network.onEndServerTick();
        FluxNetworkData.getInstance().deleteNetwork(network);
    }

    public static MachineRig machine(GameTestHelper helper) {
        FluxNetworkInputBlockEntity input = input(helper, CONTROLLER.west());
        FluxNetworkOutputBlockEntity output = output(helper, CONTROLLER.east());
        input.getTransferHandler().setLimit(100L);
        output.getTransferHandler().setLimit(100L);
        helper.setBlock(CONTROLLER.above(), ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
        ParallelControllerBlockEntity parallel = helper.getBlockEntity(CONTROLLER.above());
        parallel.setCurrentParallelism(2);
        helper.setBlock(CONTROLLER, ModBlocks.controllerFor(FluxNetworksRecipeGameTest.MACHINE_ID).get()
                .defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        controller.setMachine(MachineRegistry.getMachine(FluxNetworksRecipeGameTest.MACHINE_ID));
        return new MachineRig(controller, input, output);
    }

    public static void form(GameTestHelper helper, MachineControllerBlockEntity controller) {
        controller.requestImmediateStructureCheck();
        controller.tickStructure(helper.getLevel(), controller.getBlockPos());
        helper.assertTrue(controller.currentStructureSnapshot().formed()
                        && controller.runtimeSnapshot().linkedPortPositions().contains(controller.getBlockPos().west())
                        && controller.runtimeSnapshot().linkedPortPositions().contains(controller.getBlockPos().east()),
                "Startup-registered structure forms and discovers both actual Flux capabilities");
    }

    public static FluxNetworkInputBlockEntity reloadInput(GameTestHelper helper, FluxNetworkInputBlockEntity previous,
                                                         CompoundTag saved, FluxNetwork network) {
        helper.getLevel().removeBlockEntity(previous.getBlockPos());
        network.onEndServerTick();
        BlockEntity loaded = BlockEntity.loadStatic(previous.getBlockPos(), previous.getBlockState(),
                saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof FluxNetworkInputBlockEntity && loaded.getType() == previous.getType(),
                "Saved Point reloads through its actual registered block entity factory");
        FluxNetworkInputBlockEntity input = (FluxNetworkInputBlockEntity) loaded;
        helper.getLevel().setBlockEntity(input);
        input.onLoad();
        helper.assertTrue(!input.getNetwork().isValid(), "Reload does not bypass native first-tick connection");
        // The caller rebinds components and restores/rejects the runtime owner before ticking this new BE.
        return input;
    }

    public static MachineRig reloadMachine(GameTestHelper helper, MachineRig previous, CompoundTag savedController,
                                           CompoundTag savedPoint, FluxNetwork network, boolean pointFirst) {
        previous.controller().invalidateFormedStructure();
        helper.getLevel().removeBlockEntity(previous.controller().getBlockPos());
        FluxNetworkInputBlockEntity input = reloadInput(helper, previous.input(), savedPoint, network);
        BlockEntity loaded = BlockEntity.loadStatic(previous.controller().getBlockPos(), previous.controller().getBlockState(),
                savedController, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof MachineControllerBlockEntity, "Full controller metadata resolves its registered type");
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) loaded;
        helper.getLevel().setBlockEntity(controller);
        controller.onLoad();
        helper.assertTrue(controller.componentRuntime().capabilities().isEmpty() && !controller.runtimeRestorationComplete(),
                "Cold controller starts with no discovered components; loading NBT must not consume its pending recipe");
        long reserved = input.getTransferHandler().reserved();
        if (pointFirst) {
            tickDevice(input);
            helper.assertTrue(input.getTransferHandler().reserved() == reserved,
                    "Point first tick keeps its paid reservation while controller topology discovery is pending");
        }
        // tickStructure performs default machine binding, structure matching and component discovery.
        form(helper, controller);
        helper.assertTrue(controller.runtimeRestorationComplete(), "All normal/factory restore decisions finish after actual discovery");
        tickDevice(input);
        return new MachineRig(controller, input, previous.output());
    }

    public static MachineControllerRuntime runtime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access real controller runtime", exception);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    public record MachineRig(MachineControllerBlockEntity controller, FluxNetworkInputBlockEntity input,
                             FluxNetworkOutputBlockEntity output) {}

    /** Transport-free player observer; menu creation and native payload negotiation remain real.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestConnection extends ServerGamePacketListenerImpl {
        private TestConnection(ServerPlayer player) {
            super(player.getServer(), mockConnection(), player,
                    new CommonListenerCookie(player.getGameProfile(), 0, ClientInformation.createDefault(), false,
                            ConnectionType.NEOFORGE));
        }

        private static Connection mockConnection() {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            return connection;
        }

        @Override public void send(Packet<?> packet) {}
    }
}

package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.common.block.tile.RelayTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.lang.reflect.Field;
import java.util.UUID;

/** Runtime-backed, test-local native Ars fixtures. @author howxu <dev@howxu.cn> */
public final class ArsSourceGameTestFixtures {
    private ArsSourceGameTestFixtures() {}

    public static SourcePortBlockEntity port(GameTestHelper helper, BlockPos pos, IOType io) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(io == IOType.INPUT ? ArsSourceIds.INPUT : ArsSourceIds.OUTPUT)
                .get().defaultBlockState());
        SourcePortBlockEntity port = helper.getBlockEntity(pos);
        port.onLoad();
        return port;
    }

    public static SourceJarTile jar(GameTestHelper helper, BlockPos pos, int amount) {
        helper.setBlock(pos, BlockRegistry.SOURCE_JAR.get().defaultBlockState());
        SourceJarTile jar = helper.getBlockEntity(pos);
        jar.setSource(amount);
        return jar;
    }

    public static RelayTile relay(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BlockRegistry.RELAY.get().defaultBlockState());
        return helper.getBlockEntity(pos);
    }

    public static ISpecialSourceProvider provider(GameTestHelper helper, SourcePortBlockEntity port) {
        return SourceManager.INSTANCE.getSetForLevel(helper.getLevel()).stream()
                .filter(candidate -> candidate.getCurrentPos().equals(port.getBlockPos()) && candidate.isValid())
                .findFirst().orElseThrow(() -> new AssertionError("Source port registers a valid native provider"));
    }

    public static ServerPlayer wandPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-ars-observer"), ClientInformation.createDefault());
        player.connection = new TestConnection(player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsRegistry.DOMINION_ROD.get()));
        return player;
    }

    public static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.setPos(Vec3.atCenterOf(pos).add(0, 1, 0));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    public static MachineControllerRuntime runtime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access the real controller runtime", exception);
        }
    }

    /** Network-free observer with native mock payload negotiation for real menu synchronization. @author howxu <dev@howxu.cn> */
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

        @Override
        public void send(Packet<?> packet) {}
    }
}

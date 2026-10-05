package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkHelper;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.block_entity.mana.ManaSpreaderBlockEntity;
import vazkii.botania.common.entity.ManaSparkEntity;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.item.ManaSparkItem;

import java.util.UUID;

/** Test-local fixtures using real registries, item components and native attachment. @author howxu <dev@howxu.cn> */
public final class BotaniaManaGameTestFixtures {
    private BotaniaManaGameTestFixtures() {}

    public static ManaPortBlockEntity port(GameTestHelper helper, BlockPos pos, IOType io) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(io == IOType.INPUT ? BotaniaManaIds.INPUT : BotaniaManaIds.OUTPUT)
                .get().defaultBlockState());
        ManaPortBlockEntity port = helper.getBlockEntity(pos);
        port.onLoad();
        return port;
    }

    public static ManaPool nativePool(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BotaniaBlocks.MANA_POOL.defaultBlockState());
        return (ManaPool) ManaReceiver.LOOKUP.find(helper.getLevel(), helper.absolutePos(pos), null);
    }

    public static ManaSparkEntity spark(GameTestHelper helper, BlockPos pos, ItemStack offhand) {
        BlockPos absolute = helper.absolutePos(pos);
        helper.assertTrue(ManaSparkItem.attachSpark(helper.getLevel(), absolute,
                new ItemStack(BotaniaItems.MANA_SPARK), offhand), "Native spark item attaches to the pool");
        return (ManaSparkEntity) ManaSparkHelper.getAttachedSpark(helper.getLevel(), absolute);
    }

    public static ManaSpreaderBlockEntity spreader(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BotaniaBlocks.MANA_SPREADER.defaultBlockState());
        return helper.getBlockEntity(pos);
    }

    public static ItemEntity item(GameTestHelper helper, BlockPos pos, ItemStack stack, int amount) {
        ManaItem mana = ManaItem.LOOKUP.find(stack);
        if (mana != null && amount > 0) mana.addMana(amount);
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(pos));
        ItemEntity entity = new ItemEntity(helper.getLevel(), center.x, center.y - 0.25, center.z, stack);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    public static ServerPlayer wandPlayer(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mmcr-mana-observer"), ClientInformation.createDefault());
        player.connection = new TestConnection(player);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.WAND_OF_THE_FOREST));
        return player;
    }

    public static void use(GameTestHelper helper, ServerPlayer player, BlockPos relativePos) {
        BlockPos pos = helper.absolutePos(relativePos);
        player.setPos(Vec3.atCenterOf(pos).add(0, 1, 0));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** Network-free player connection for native item interaction. @author howxu <dev@howxu.cn> */
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

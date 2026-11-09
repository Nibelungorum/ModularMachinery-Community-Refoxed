package cn.howxu.mmcr.compat.extendedae;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.compat.appliedenergistics2.InterfaceScreenTitles;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.glodblock.github.extendedae.container.ContainerRenamer;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.PacketSendListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Exercises ExtendedAE's real renamer against ordinary and extended MMCR pattern hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
public class PatternInterfaceRenameGameTest {
    public void renameAndPersistence(GameTestHelper helper) {
        for (String id : List.of("ae2_me_pattern_interface", "eae_me_extended_pattern_interface")) {
            helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
            PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
            var player = player(helper);
            player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_KNIFE.stack());
            var event = click(player, host.getBlockPos(), InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled() && event.getCancellationResult() == InteractionResult.SUCCESS
                            && player.containerMenu instanceof ContainerRenamer,
                    "Quartz knife opens the native ExtendedAE renamer for " + id);
            var menu = (ContainerRenamer) player.containerMenu;
            helper.assertTrue(menu.isValidMenu(), "Native setter accepts the MMCR host");
            menu.setName("精密装配 / Assembly");
            Component expected = Component.literal("精密装配 / Assembly");
            helper.assertTrue(expected.equals(host.getCustomName()) && expected.equals(host.getName())
                            && expected.equals(host.getTerminalGroup().name())
                            && expected.equals(InterfaceScreenTitles.titleFor(host, Component.empty())),
                    "Menu writes a shared custom name for the title and AE2 terminal");

            var restored = new PatternInterfaceBlockEntity(host.getBlockPos(), host.getBlockState(), host.kind());
            restored.loadWithComponents(host.saveWithoutMetadata(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(expected.equals(restored.getCustomName()), "Custom name survives save/load");
            var client = new PatternInterfaceBlockEntity(host.getBlockPos(), host.getBlockState(), host.kind());
            client.handleUpdateTag(host.getUpdateTag(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertTrue(expected.equals(client.getCustomName()), "Block update data carries the name");
            ItemStack carrier = new ItemStack(host.getBlockState().getBlock());
            carrier.applyComponents(host.collectComponents());
            var placed = new PatternInterfaceBlockEntity(host.getBlockPos(), host.getBlockState(), host.kind());
            placed.applyComponentsFromItemStack(carrier);
            helper.assertTrue(expected.equals(placed.getCustomName()), "Item components restore the name on placement");
            var drops = host.getBlockState().getDrops(new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(host.getBlockPos()))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_ENTITY, host));
            helper.assertTrue(drops.size() == 1 && expected.equals(drops.getFirst().get(DataComponents.CUSTOM_NAME)),
                    "Generated loot preserves the custom name");

            menu.setName("   ");
            helper.assertFalse(host.hasCustomName(), "Whitespace clears the name");
            helper.assertTrue(Component.translatable("container.mmcr." + id).equals(host.getName())
                            && host.getName().equals(InterfaceScreenTitles.titleFor(host, Component.empty())),
                    "Clearing restores the translated title");
            client.handleUpdateTag(host.getUpdateTag(helper.getLevel().registryAccess()),
                    helper.getLevel().registryAccess());
            helper.assertFalse(client.hasCustomName(), "Update data also clears a previously synchronized name");
            menu.setName("second");
            menu.setName("");
            helper.assertFalse(host.hasCustomName(), "Empty input clears the name");
            player.closeContainer();
            player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.NETHER_QUARTZ_KNIFE.stack());
            var netherEvent = click(player, host.getBlockPos(), InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(netherEvent);
            helper.assertTrue(netherEvent.isCanceled() && player.containerMenu instanceof ContainerRenamer,
                    "Nether quartz knife also opens the native renamer for " + id);
            ((ContainerRenamer) player.containerMenu).setName("nether knife");
            helper.assertTrue(Component.literal("nether knife").equals(host.getCustomName()),
                    "Both quartz knife variants can rename the interface");
            player.closeContainer();
        }
        helper.succeed();
    }

    public void interactionFilters(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_KNIFE.stack());
        player.setShiftKeyDown(true);
        assertPass(helper, click(player, host.getBlockPos(), InteractionHand.MAIN_HAND), "Sneaking is not intercepted");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.OFF_HAND, AEItems.CERTUS_QUARTZ_KNIFE.stack());
        assertPass(helper, click(player, host.getBlockPos(), InteractionHand.OFF_HAND), "Offhand is not intercepted");
        player.setGameMode(GameType.SPECTATOR);
        assertPass(helper, click(player, host.getBlockPos(), InteractionHand.MAIN_HAND), "Spectator is not intercepted");
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        assertPass(helper, click(player, host.getBlockPos(), InteractionHand.MAIN_HAND), "Other items are not intercepted");
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_KNIFE.stack());
        var canceled = click(player, host.getBlockPos(), InteractionHand.MAIN_HAND);
        canceled.setCanceled(true);
        NeoForge.EVENT_BUS.post(canceled);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Canceled events cannot open the renamer");

        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        assertPass(helper, click(player, host.getBlockPos(), InteractionHand.MAIN_HAND), "Other MMCR interfaces are unchanged");
        if (ModBlocks.BLOCKS.containsKey("eaep_me_mirror_pattern_interface")) {
            helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eaep_me_mirror_pattern_interface").get().defaultBlockState());
            PatternInterfaceBlockEntity mirror = helper.getBlockEntity(BlockPos.ZERO);
            assertPass(helper, click(player, mirror.getBlockPos(), InteractionHand.MAIN_HAND), "Mirror is excluded");
            mirror.setCustomName(Component.literal("not allowed"));
            helper.assertFalse(mirror.hasCustomName(), "Mirror rejects direct name writes as well");
        }

        helper.setBlock(BlockPos.ZERO, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        NeoForge.EVENT_BUS.post(click(player, host.getBlockPos(), InteractionHand.MAIN_HAND));
        helper.assertTrue(player.containerMenu instanceof ContainerRenamer, "Native AE2 provider still opens its renamer");
        ((ContainerRenamer) player.containerMenu).setName("native provider");
        helper.assertTrue(Component.literal("native provider").equals(
                        ((Nameable) helper.getLevel().getBlockEntity(host.getBlockPos())).getCustomName()),
                "Native ExtendedAE setter remains unchanged");
        player.closeContainer();
        helper.succeed();
    }

    private static void assertPass(GameTestHelper helper, PlayerInteractEvent.RightClickBlock event, String message) {
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(!event.isCanceled() && event.getEntity().containerMenu == event.getEntity().inventoryMenu, message);
    }

    private static PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        return new PlayerInteractEvent.RightClickBlock(player, hand, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "rename-test");
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault());
        player.connection = new MenuConnection(player, profile);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
        return player;
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class MenuConnection extends ServerGamePacketListenerImpl {
        private MenuConnection(ServerPlayer player, GameProfile profile) {
            super(player.level().getServer(), connected(), player, CommonListenerCookie.createInitial(profile, false));
        }

        private static Connection connected() {
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            ChannelAttributes.setConnectionType(connection, ConnectionType.NEOFORGE);
            return connection;
        }

        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
    }
}

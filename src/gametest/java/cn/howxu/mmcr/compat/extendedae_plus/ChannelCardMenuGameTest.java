package cn.howxu.mmcr.compat.extendedae_plus;

import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.IPriorityHost;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.ChannelCardHostSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.extendedae_plus.ae.wireless.WirelessMasterLink;
import com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl;
import com.extendedae_plus.init.ModItems;
import com.extendedae_plus.items.materials.ChannelCardItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exercises real channel-card upgrade slots, wireless nodes and submenu returns for each ME profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ChannelCardMenuGameTest {
    public void channelCardsSurviveOwnMenusAndReconnect(GameTestHelper helper) {
        List<String> ids = List.of("ae2_me_input_interface", "ae2_me_stocking_input_interface",
                "ae2_me_output_interface", "ae2_me_async_output_interface", "ae2_me_pattern_interface",
                "eae_me_extended_input_interface", "eae_me_extended_stocking_input_interface",
                "eae_me_extended_output_interface", "eae_me_oversize_input_interface",
                "eae_me_oversize_output_interface", "eae_me_extended_pattern_interface");
        List<String> profiles = new ArrayList<>(ids);
        if (ModBlocks.BLOCKS.containsKey("eae_me_oversize_stocking_input_interface")) {
            profiles.add("eae_me_oversize_stocking_input_interface");
        }
        List<IOPortBlockEntity> hosts = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            BlockPos pos = new BlockPos(0, i * 2, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(profiles.get(i)).get().defaultBlockState());
            hosts.add(helper.getBlockEntity(pos));
        }
        BlockPos energyPos = new BlockPos(0, profiles.size() * 2 + 2, 0);
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(energy.getMainNode().getNode() != null, "Wireless master node initializes");
            for (IOPortBlockEntity host : hosts) {
                helper.assertTrue(node(host) != null, "Actual interface work node initializes: " + host.kind().id());
            }
        }).thenExecute(() -> {
            UUID owner = UUID.randomUUID();
            var master = new WirelessMasterLink(new GenericNodeEndpointImpl(() -> energy, energy.getMainNode()::getNode));
            master.setPlacerId(owner);
            master.setFrequency(37L);
            try {
                for (IOPortBlockEntity host : hosts) exerciseMenu(helper, host, energy, owner);
            } finally {
                for (IOPortBlockEntity host : hosts) ChannelCardHostSupport.unloaded(host);
                master.onUnloadOrRemove();
            }
        }).thenSucceed();
    }

    private static void exerciseMenu(GameTestHelper helper, IOPortBlockEntity host,
                                     CreativeEnergyCellBlockEntity energy, UUID owner) {
        String id = host.kind().id();
        var profile = new GameProfile(UUID.randomUUID(), "channel-menu-test");
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault());
        player.connection = new MenuConnection(player, profile);
        helper.assertTrue(AE2Bridge.get().openMenu(player, helper.getLevel(), host.getBlockPos()),
                "Bridge opens the own menu for " + id);
        var menu = (AEBaseMenu) player.containerMenu;
        helper.assertTrue(menu.getType() == AE2MenuTypes.typeFor(host.kind()), "Correct menu profile for " + id);
        if (menu instanceof ExtendedInterfaceMenu extended) extended.setPage(1);
        var upgrade = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        ItemStack card = ModItems.CHANNEL_CARD.get().getDefaultInstance();
        CompoundTag data = new CompoundTag();
        data.putLong("channel", 37L);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ChannelCardItem.setOwnerUUID(card, owner);
        ItemStack original = card.copy();
        menu.setCarried(card);
        menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(original, upgrade.getItem()),
                "Actual upgrade click installs the bound card with components intact for " + id);
        var bridge = ChannelCardHostSupport.wirelessLogic(host);
        helper.assertTrue(bridge != null, "Native logic exposes the channel-card controller for " + id);
        var controller = bridge.eap$getChannelCardController();
        controller.tick();
        assertConnected(helper, host, energy, id);
        ChannelCardHostSupport.unloaded(host);
        helper.assertFalse(controller.isConnected(), "Unload disconnects the actual wireless edge for " + id);
        controller.tick();
        helper.assertFalse(controller.isConnected(), "Unloaded controller cannot recreate the edge for " + id);
        ChannelCardHostSupport.loaded(host);
        controller.tick();
        assertConnected(helper, host, energy, id);
        ChannelCardHostSupport.unloaded(host);
        helper.assertFalse(controller.isConnected(), "Reload registers the controller for a second unload for " + id);
        ChannelCardHostSupport.loaded(host);

        MenuOpener.open(PriorityMenu.TYPE, player, MenuLocators.forBlockEntity(host));
        ((IPriorityHost) host).returnToMainMenu(player, (PriorityMenu) player.containerMenu);
        menu = (AEBaseMenu) player.containerMenu;
        helper.assertTrue(menu.getType() == AE2MenuTypes.typeFor(host.kind())
                        && (!(menu instanceof ExtendedInterfaceMenu extended) || extended.getPage() == 1),
                "Priority return retains the own profile and page for " + id);
        upgrade = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(ItemStack.matches(original, menu.getCarried()) && !upgrade.hasItem()
                        && !controller.isConnected() && !controller.shouldKeepTicking(),
                "Removal returns the same card and stops its wireless connection for " + id);
        player.getInventory().setItem(0, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty() && ItemStack.matches(original, upgrade.getItem()),
                "Shift installation still reaches the real upgrades for " + id);
        controller.tick();
        assertConnected(helper, host, energy, id);
        menu.quickMoveStack(player, upgrade.index);
        helper.assertTrue(!upgrade.hasItem() && !controller.isConnected()
                        && player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, original))
                        .mapToInt(ItemStack::getCount).sum() == 1,
                "Shift removal conserves the bound card and disconnects for " + id);
    }

    private static IGridNode node(IOPortBlockEntity host) {
        return ((IGridConnectedBlockEntity) host).getMainNode().getNode();
    }

    private static void assertConnected(GameTestHelper helper, IOPortBlockEntity host,
                                         CreativeEnergyCellBlockEntity energy, String id) {
        var node = node(host);
        helper.assertTrue(ChannelCardHostSupport.wirelessLogic(host).eap$getChannelCardController().isConnected()
                        && node.getConnections().stream().anyMatch(connection -> connection.getOtherSide(node) == energy.getMainNode().getNode()),
                "Channel card connects the real work node, including Stocking, for " + id);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class MenuConnection extends ServerGamePacketListenerImpl {
        private MenuConnection(ServerPlayer player, GameProfile profile) {
            super(player.level().getServer(), new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(profile, false));
        }

        @Override public void send(Packet<?> packet) { }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { }
    }
}

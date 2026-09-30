package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.internal.assembly.StructureItemStorage;
import cn.howxu.mmcr.internal.assembly.StructureItemStorageResolver;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.item.TerminalInventoryMode;
import cn.howxu.mmcr.internal.item.TerminalService;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Verifies terminal material transfers through an AE2 wireless access point.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2TerminalGameTest {
    public void boundAccessPointTransfersBuildAndDemolishMaterials(GameTestHelper helper) {
        BlockPos accessPointPos = new BlockPos(0, 0, 0);
        BlockPos chestPos = new BlockPos(2, 0, 0);
        BlockPos energyPos = new BlockPos(0, 0, 2);
        helper.setBlock(accessPointPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            WirelessAccessPointBlockEntity accessPoint = helper.getBlockEntity(accessPointPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(accessPoint.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                            && energy.getMainNode().getNode() != null,
                    "Wireless access point, ME chest, and energy cell initialize their grid nodes");
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(accessPoint.getMainNode().getNode(), energy.getMainNode().getNode());
        });

        helper.runAtTickTime(4, () -> {
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 1L, Actionable.MODULATE,
                    IActionSource.empty());
            ServerPlayer player = player(helper);
            GlobalPos accessPoint = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(accessPointPos));
            ItemStack containerTerminal = new ItemStack(ModItems.TERMINAL.get());
            containerTerminal.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT
                    .withInventoryMode(TerminalInventoryMode.CONTAINER));
            player.setItemInHand(InteractionHand.MAIN_HAND, containerTerminal);
            helper.assertFalse(TerminalService.bindContainer(player, containerTerminal, accessPoint).accepted(),
                    "Wireless access points cannot be bound as ordinary containers");
            ItemStack terminal = new ItemStack(ModItems.TERMINAL.get());
            terminal.set(ModDataComponents.TERMINAL_DATA.get(), TerminalData.DEFAULT
                    .withInventoryMode(TerminalInventoryMode.AE2));
            player.setItemInHand(InteractionHand.MAIN_HAND, terminal);

            helper.assertTrue(TerminalService.bindAe2AccessPoint(player, terminal, accessPoint).accepted(),
                    "Shift-right-click binding accepts a powered wireless access point");
            StructureItemStorage storage = StructureItemStorageResolver.resolve(player, TerminalData.from(terminal))
                    .orElseThrow(() -> new AssertionError("Bound ME network resolves to terminal storage"));
            helper.assertTrue(storage.source().extractAll(List.of(new ItemStack(Items.IRON_INGOT))),
                    "Terminal build source extracts the requested material from the ME network");
            helper.assertTrue(storage.sink().accept(new ItemStack(Items.STONE)),
                    "Terminal demolish sink returns a complete material stack to the ME network");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == 0L,
                    "Build extraction removes the material from the ME network");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.STONE), 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == 1L,
                    "Demolish insertion returns the material to the ME network");
            helper.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("mmcr-ae2-terminal".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-ae2-terminal"),
                ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message) {
                // The isolated GameTest player has no packet connection.
            }
        };
    }
}

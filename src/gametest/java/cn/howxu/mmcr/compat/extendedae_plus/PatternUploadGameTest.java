package cn.howxu.mmcr.compat.extendedae_plus;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Exercises the actual EAEP upload candidate list and terminal identity on powered MMCR hosts.
 *
 * @author howxu <dev@howxu.cn>
 */
public class PatternUploadGameTest {
    public void interfacesKeepOwnUploadIdentity(GameTestHelper helper) {
        List<String> ids = List.of("ae2_me_pattern_interface", "eae_me_extended_pattern_interface",
                "eaep_me_mirror_pattern_interface");
        List<BlockPos> positions = List.of(new BlockPos(1, 2, 1), new BlockPos(4, 2, 1), new BlockPos(7, 2, 1));
        for (int i = 0; i < ids.size(); i++) {
            helper.setBlock(positions.get(i), ModBlocks.BLOCKS.get(ids.get(i)).get().defaultBlockState());
            helper.setBlock(positions.get(i).south(), ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        }
        BlockPos energyPos = new BlockPos(1, 0, 1);
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        List<PatternInterfaceBlockEntity> hosts = positions.stream()
                .map(pos -> helper.<PatternInterfaceBlockEntity>getBlockEntity(pos)).toList();
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(energy.getMainNode().getNode() != null, "Energy node initializes");
            for (var host : hosts) {
                helper.assertTrue(host.getMainNode().getNode() != null, "Pattern interface node initializes");
            }
        }).thenExecute(() -> {
            for (var host : hosts) {
                GridHelper.createConnection(host.getMainNode().getNode(), energy.getMainNode().getNode());
            }
        }).thenWaitUntil(() -> {
            for (var host : hosts) helper.assertTrue(host.getMainNode().isActive(), "Pattern interface is powered");
        }).thenExecute(() -> {
            var grid = energy.getMainNode().getGrid();
            for (var host : hosts.subList(0, 2)) {
                Component defaultName = Component.translatable("container.mmcr." + host.kind().id());
                helper.assertTrue(ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(grid).contains(host),
                        "Writable interface is an actual EAEP upload candidate");
                helper.assertTrue(defaultName.equals(host.getTerminalGroup().name())
                                && defaultName.equals(ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(host))
                                && AEItemKey.of(host.getBlockState().getBlock().asItem()).equals(host.getTerminalGroup().icon()),
                        "Neighboring bus cannot replace the interface's translated name or icon");
                Component customName = Component.literal("upload / " + host.kind().id());
                host.setCustomName(customName);
                helper.assertTrue(customName.equals(ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(host)),
                        "EAEP reads the renamed interface immediately");
                host.getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.NO);
                helper.assertFalse(ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(grid).contains(host),
                        "Renaming does not bypass the terminal visibility setting");
                host.getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.YES);
                helper.assertTrue(ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(grid).contains(host),
                        "Restoring visibility restores the upload candidate");
                ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                        List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L)));
                helper.assertTrue(ExtendedAEPatternUploadUtil.insertIntoAccessiblePatternSlots(host, pattern, null).isEmpty()
                                && ItemStack.matches(pattern, host.getTerminalPatternInventory().getStackInSlot(0)),
                        "EAEP upload insertion still reaches the interface's real pattern inventory");
                host.setCustomName(null);
                helper.assertTrue(defaultName.equals(ExtendedAEPatternUploadUtil.getProviderDisplayNameComponent(host)),
                        "Clearing the custom name restores the interface name rather than the neighboring bus");
            }
            var mirror = hosts.getLast();
            helper.assertFalse(ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(grid).contains(mirror),
                    "Read-only mirror stays hidden in EAEP's upload candidate list");
            helper.assertTrue(AEItemKey.of(AEBlocks.PATTERN_PROVIDER.stack()).equals(mirror.getTerminalIcon()),
                    "Mirror retains its existing terminal icon");
        }).thenSucceed();
    }
}

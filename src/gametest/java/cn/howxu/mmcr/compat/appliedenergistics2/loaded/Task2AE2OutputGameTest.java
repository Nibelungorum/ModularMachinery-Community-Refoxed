package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Minimal world-level coverage for the Task 2 AE2 output lifecycle and wake-up.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class Task2AE2OutputGameTest {
    public void outputWakeUpAndActiveNodeLifecycle(GameTestHelper helper) {
        BlockPos outputPos = new BlockPos(0, 1, 0);
        BlockPos chestPos = new BlockPos(3, 1, 0);
        BlockPos energyPos = new BlockPos(3, 1, 2);
        OutputInterfaceBlockEntity output = placeOutput(helper, outputPos);
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        chest.setCell(AEItems.ITEM_CELL_1K.stack());

        helper.runAtTickTime(2, () -> {
            IGridNode outputNode = output.getMainNode().getNode();
            helper.assertTrue(outputNode != null, "Output interface created its grid node before connection");
            helper.assertTrue(chest.getMainNode().getNode() != null, "ME Chest created its grid node");
            helper.assertTrue(energy.getMainNode().getNode() != null, "Creative energy cell created its grid node");
        });

        helper.runAtTickTime(3, () -> {
            output.getStorage().setStack(0,
                    new GenericStack(AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 8L));
            IGridNode outputNode = output.getMainNode().getNode();
            if (outputNode != null) {
                GridHelper.createConnection(outputNode, Objects.requireNonNull(chest.getMainNode().getNode()));
            }
            if (outputNode != null) {
                GridHelper.createConnection(outputNode, Objects.requireNonNull(energy.getMainNode().getNode()));
            }
        });

        helper.runAtTickTime(12, () -> {
            IGridNode activeNode = output.getMainNode().getNode();
            helper.assertTrue(activeNode != null && activeNode.isActive(),
                    "Output interface is active before lifecycle teardown");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT),
                            8L, Actionable.SIMULATE, appeng.api.networking.security.IActionSource.empty()) == 8L,
                    "Network wake-up flushes the cached output into the ME Chest");
            helper.assertTrue(output.getStorage().isEmpty(), "Output cache is empty after the network wake-up flush");
        });

        helper.runAtTickTime(24, () -> {
            IGridNode activeNode = output.getMainNode().getNode();
            helper.assertTrue(activeNode != null && activeNode.isActive(),
                    "Output interface is still active before removal at tick 24");
            output.setRemoved();
            helper.assertTrue(output.isRemoved(), "Removing the output host marks it removed");
            helper.assertTrue(output.getMainNode().getNode() == null,
                    "Removing destroys the active output node");
            helper.succeed();
        });
    }

    private static OutputInterfaceBlockEntity placeOutput(GameTestHelper helper, BlockPos pos) {
        BlockState state = ModBlocks.BLOCKS.get(PortKinds.ITEM_OUTPUT.id()).get().defaultBlockState();
        helper.setBlock(pos, state);
        helper.getLevel().removeBlockEntity(helper.absolutePos(pos));
        OutputInterfaceBlockEntity output = new OutputInterfaceBlockEntity(
                helper.absolutePos(pos), state, PortKinds.ITEM_OUTPUT);
        helper.getLevel().setBlockEntity(output);
        return output;
    }
}

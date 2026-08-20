package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Covers expandable structure stage selection in live GameTest worlds.
 *
 * @author howxu <dev@howxu.cn>
 */
public class ExpandableStructureGameTest {

    public void upgradesAndDowngradesHighestAvailableStage(GameTestHelper helper) {
        Identifier machineId = MMCR.id("expandable_structure_stages");
        Machine machine = MachineRegistry.getMachine(machineId);
        BlockPos controllerPos = new BlockPos(3, 3, 3);
        MachineControllerBlockEntity controller = placeController(helper, controllerPos, Direction.SOUTH, Direction.NORTH, machine);

        helper.setBlock(controllerPos.offset(1, 0, 0), ModBlocks.CASING.get().defaultBlockState());
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "stage 1 should form");
        helper.assertTrue(controller.getMatchedStructureStage() == 1, "matched stage should be 1");

        BlockPos stage2Pos = controllerPos.offset(1, 1, 0);
        helper.setBlock(stage2Pos, ModBlocks.CASING.get().defaultBlockState());
        controller.onStructureBlockChanged(helper.absolutePos(stage2Pos));
        controller.serverTick();
        helper.assertTrue(controller.getMatchedStructureStage() == 2, "matched stage should upgrade to 2");

        BlockPos stage3Pos = controllerPos.offset(2, 0, 0);
        helper.setBlock(stage3Pos, ModBlocks.CASING.get().defaultBlockState());
        controller.onStructureBlockChanged(helper.absolutePos(stage3Pos));
        controller.serverTick();
        helper.assertTrue(controller.getMatchedStructureStage() == 3, "matched stage should upgrade to 3");

        helper.destroyBlock(stage3Pos);
        controller.onStructureBlockChanged(helper.absolutePos(stage3Pos));
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "stage 2 should remain formed after stage 3 breaks");
        helper.assertTrue(controller.getMatchedStructureStage() == 2, "matched stage should downgrade to 2");
        helper.succeed();
    }

    public void verticalNonDefaultRollUsesStageSelection(GameTestHelper helper) {
        Identifier machineId = MMCR.id("expandable_structure_vertical_roll");
        Machine machine = MachineRegistry.getMachine(machineId);
        BlockPos controllerPos = new BlockPos(3, 3, 3);
        Direction rollFacing = Direction.WEST;
        MachineControllerBlockEntity controller = placeController(helper, controllerPos, Direction.UP, rollFacing, machine);

        for (BlockPos pos : machine.structureStages().get(2).pattern().pattern().keySet()) {
            if (pos.equals(BlockPos.ZERO)) continue;
            helper.setBlock(controllerPos.offset(BlockRotator.rotateSouthTo(pos, Direction.UP, rollFacing)),
                    ModBlocks.CASING.get().defaultBlockState());
        }

        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "vertical stage 3 should form");
        helper.assertTrue(controller.getMatchedStructureStage() == 3, "vertical roll should select stage 3");
        helper.assertTrue(controller.getFoundPattern().pattern().containsKey(
                BlockRotator.rotateSouthTo(new BlockPos(2, 0, 0), Direction.UP, rollFacing)),
                "vertical found pattern should use non-default roll");
        helper.succeed();
    }

    private static MachineControllerBlockEntity placeController(GameTestHelper helper, BlockPos pos, Direction facing,
                                                               Direction rollFacing, Machine machine) {
        helper.setBlock(pos, ModBlocks.controllerFor(machine.registryName()).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, facing)
                .setValue(MachineControllerBlock.ROLL_FACING, rollFacing));
        MachineControllerBlockEntity controller = helper.getBlockEntity(pos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        return controller;
    }
}

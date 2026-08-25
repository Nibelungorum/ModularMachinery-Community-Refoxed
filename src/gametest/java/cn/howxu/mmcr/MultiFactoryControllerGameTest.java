package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}

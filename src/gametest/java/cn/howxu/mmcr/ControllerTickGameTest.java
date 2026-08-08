package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class ControllerTickGameTest {

    public void structureForms3x3Casing(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("controller_tick")).get().defaultBlockState());

        var machine = MachineRegistry.getMachine(MMCR.id("controller_tick"));
        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.serverTick();
        helper.assertTrue(controller.isFormed(), "Structure formed");
        helper.succeed();
    }

    public void scansRegisteredMachineWhenDefaultBindingIsEmpty(GameTestHelper helper) {
        MachineRegistry.clearForTesting();

        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.COBBLESTONE.defaultBlockState());

        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("controller_tick")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));

        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) pattern.put(new BlockPos(x, 0, z),
                    new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        pattern.put(new BlockPos(-1, 0, -1), new BlockPredicate.OfBlock(Blocks.COBBLESTONE));

        var machine = new DynamicMachine(Identifier.fromNamespaceAndPath(MMCR.MODID, "scanned_controller_tick"),
                "Scanned Controller Tick", new BlockArray(pattern));
        MachineRegistry.register(machine);

        try {
            var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
            controller.serverTick();

            helper.assertTrue(controller.isFormed(), "Structure formed from registry scan");
            helper.assertTrue(controller.getMachine() == machine, "Controller bound scanned machine");
            helper.succeed();
        } finally {
            MMCR.registerRuntimeBuiltins();
        }
    }
}

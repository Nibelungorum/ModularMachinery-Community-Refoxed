package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public class BlockArrayMatchGameTest {

    public void structureForms3x3Casing(GameTestHelper helper) {
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos ctrlPos = new BlockPos(1, 1, 1);
        helper.setBlock(ctrlPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());

        var machine = MachineRegistry.getMachine(MMCR.id("test_cube"));

        var be = helper.getBlockEntity(ctrlPos, MachineControllerBlockEntity.class);
        be.setMachine(machine);
        be.serverTick();

        helper.assertTrue(be.isFormed(), "Structure formed");
        helper.succeed();
    }
}

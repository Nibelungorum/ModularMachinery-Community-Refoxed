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
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.structureSnapshot().formed(), "Structure formed after bounded scan");
            helper.assertTrue(be.behaviorContext().countStructureBlocks(ModBlocks.CASING.get()) == 8,
                    "Formed structure records the eight casing blocks");
            helper.assertTrue(be.behaviorContext().countStructureBlocks("mmcr:basic_casing") == 8,
                    "Registry-name lookup matches the Block lookup");
            boolean rejected = false;
            try {
                be.behaviorContext().countStructureBlocks("missingmod:not_a_block");
            } catch (IllegalArgumentException ignored) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Unknown block registry names are rejected");
            be.invalidateFormedStructure();
            helper.assertTrue(be.behaviorContext().countStructureBlocks(ModBlocks.CASING.get()) == 0,
                    "Unformed structures do not retain block counts");
            helper.succeed();
        });
    }
}

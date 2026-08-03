package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.Map;

@GameTestHolder(MMCR.MODID)
public class BlockArrayMatchGameTest {

    @GameTest(template = "minecraft:empty")
    public void structureForms3x3Casing(GameTestHelper helper) {
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos ctrlPos = new BlockPos(1, 1, 1);
        helper.setBlock(ctrlPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());

        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                if (x != 0 || z != 0)
                    pattern.put(new BlockPos(x, 0, z),
                            new BlockPredicate.OfBlock(ModBlocks.CASING.get()));
        var machine = new DynamicMachine(
                Identifier.fromNamespaceAndPath(MMCR.MODID, "test_cube"),
                "Test",
                new BlockArray(pattern));
        MachineRegistry.register(machine);

        var be = helper.getBlockEntity(ctrlPos, MachineControllerBlockEntity.class);
        be.setMachine(machine);
        be.serverTick();

        helper.assertTrue(be.isFormed(), "Structure formed");
        helper.succeed();
    }
}

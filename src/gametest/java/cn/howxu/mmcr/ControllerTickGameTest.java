package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.gametest.framework.GameTestAssert.assertTrue;

@GameTestHolder(MMCR.MODID)
public class ControllerTickGameTest {

    @GameTest(template = "minecraft:empty")
    public void structureForms3x3Casing(LevelAccessor accessor) {
        Level level = (Level) accessor;
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            level.setBlock(new BlockPos(x, 1, z), MMCRRegistries.CASING_BLOCK.get().defaultBlockState(), 3);

        BlockPos controllerPos = new BlockPos(1, 1, 1);
        level.setBlock(controllerPos, MMCRRegistries.CONTROLLER_BLOCK.get().defaultBlockState(), 3);
        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) pattern.put(new BlockPos(x, 0, z),
                    new BlockPredicate.OfBlock(MMCRRegistries.CASING_BLOCK.get()));

        var machine = new DynamicMachine(Identifier.fromNamespaceAndPath(MMCR.MODID, "controller_tick"),
                "Controller Tick", new BlockArray(pattern));
        MachineRegistry.register(machine);
        var controller = (MachineControllerBlockEntity) level.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.serverTick();
        assertTrue(controller.isFormed(), "Structure formed");
    }
}

package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end smart interface binding and recipe write coverage.
 *
 * @author howxu <dev@howxu.cn>
 */
public class SmartInterfaceGameTest {

    public void bindsDefaultValueAndWritesRecipeOutput(GameTestHelper helper) {
        ServerConfig.MACHINE_WORK_MODE.clearCache();
        ServerConfig.MACHINE_WORK_MODE.set(MachineWorkMode.SYNC);
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos interfacePos = controllerPos.offset(1, 0, 0);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(interfacePos, ModBlocks.SMART_INTERFACE.get().defaultBlockState());

        String type = "temperature";
        var machineId = MMCR.id("smart_interface_test");
        DynamicMachine machine = new DynamicMachine(MMCR.id("smart_interface_test"), "Smart Interface Test",
                new BlockArray(Map.of(interfacePos.subtract(controllerPos),
                        new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()))));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        SmartInterfaceBlockEntity smartInterface = helper.getBlockEntity(interfacePos);
        BlockPos controllerWorldPos = controller.getBlockPos();
        controller.setMachine(machine);

        controller.serverTick();
        helper.assertTrue(controller.structureSnapshot().formed(), "Structure formed with smart interface");
        helper.assertTrue(smartInterface.bindingFor(controllerWorldPos).map(binding -> binding.value() == 12F).orElse(false),
                "Smart interface received its default binding");
        helper.assertTrue(smartInterface.setValue(0, 15F), "Smart interface accepts range-compatible input value");

        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id("smart_interface_output"), machine.registryName(), 20,
                List.of(SmartInterfaceRequirement.input(type, 10F, 20F),
                        SmartInterfaceRequirement.output(type, 42F)), List.of(), List.of(), 0, 1, false, false,
                false, Set.of()));
        controller.serverTick();

        helper.assertTrue(smartInterface.bindingFor(controllerWorldPos).map(binding -> binding.value() == 15F).orElse(false),
                "Recipe output does not write the smart interface value when it starts");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(smartInterface.bindingFor(controllerWorldPos)
                                .map(binding -> binding.value() == 42F).orElse(false),
                        "Recipe output writes the smart interface value when it finishes"))
                .thenExecute(helper::succeed);
    }
}

package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class ControllerTickGameTest {

    public void structureForms3x3Casing(GameTestHelper helper) {
        Identifier machineId = MMCR.id("controller_tick");
        helper.assertTrue(MachineRegistry.getMachine(machineId) != null,
                "GameTest startup installs the machine registry entry");
        helper.assertTrue(cn.howxu.mmcr.api.machine.MachineStructureRegistry.effectiveSnapshot().containsKey(machineId),
                "GameTest startup installs the effective structure");
        helper.assertTrue(!MachineRegistry.getCompiledStages(machineId).isEmpty(),
                "GameTest startup compiles the effective structure");

        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());

        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState());

        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.serverTick();
        helper.assertTrue(controller.boundMachine().isPresent(), "Controller binds the startup machine");
        controller.setMachine(MachineRegistry.getMachine(machineId));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(), "Structure formed after bounded scan");
            helper.succeed();
        });
    }

    public void redstonePausesAndResumesControllerRecipeProgress(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos inputPos = controllerPos.offset(1, 0, 0);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("controller_tick")).get().defaultBlockState());
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        Identifier recipeId = MMCR.id("controller_tick_redstone_pause");
        RecipeRegistry.register(new MachineRecipe(recipeId, MMCR.id("controller_tick"), 20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)), List.of()));

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("controller_tick")));
        controller.serverTick();
        controller.serverTick();
        int progressBeforePause = controller.getActive().getTick();
        helper.assertTrue(progressBeforePause > 0, "Recipe started before redstone pause");

        helper.setBlock(controllerPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (int tick = 0; tick < 4; tick++) controller.serverTick();
        helper.assertTrue(controller.isRedstonePaused(), "Direct redstone signal pauses controller");
        helper.assertTrue(controller.getActive() == null, "Paused recipe leaves the active slot");

        helper.setBlock(controllerPos.above(), Blocks.AIR.defaultBlockState());
        controller.serverTick();
        helper.assertTrue(controller.getActive() != null, "Removing redstone resumes the paused recipe");
        helper.assertTrue(controller.getActive().getTick() > progressBeforePause, "Recipe resumes from paused progress");
        helper.succeed();
    }
}

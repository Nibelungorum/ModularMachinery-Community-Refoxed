package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.MMCRBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.gametest.framework.GameTestAssert.assertTrue;

@GameTestHolder(MMCR.MODID)
public class E2ERecipeRunGameTest {

    @GameTest(template = "minecraft:empty", timeoutTicks = 200)
    public void ironCompressorRuns(LevelAccessor accessor) {
        Level level = (Level) accessor;
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            level.setBlock(new BlockPos(x, 1, z), MMCRBlocks.CASING.get().defaultBlockState(), 3);
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        level.setBlock(controllerPos, MMCRBlocks.CONTROLLER.get().defaultBlockState(), 3);
        BlockPos inputPos = new BlockPos(1, 2, 0);
        level.setBlock(inputPos, MMCRBlocks.BLOCKS.get("io_port_item_basic").get().defaultBlockState(), 3);
        ((ItemBusBlockEntity) level.getBlockEntity(inputPos)).getItemHandler(null)
                .insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
        BlockPos outputPos = new BlockPos(1, 2, 2);
        level.setBlock(outputPos, MMCRBlocks.BLOCKS.get("io_port_item_basic").get().defaultBlockState()
                .setValue(IOPortBlock.IO_TYPE, cn.howxu.mmcr.util.IOType.OUTPUT), 3);
        BlockPos energyPos = new BlockPos(2, 2, 1);
        level.setBlock(energyPos, MMCRBlocks.BLOCKS.get("io_port_energy_basic").get().defaultBlockState(), 3);
        ((EnergyHatchBlockEntity) level.getBlockEntity(energyPos)).getEnergyStorage(null).receiveEnergy(10000, false);

        Map<BlockPos, BlockPredicate> pattern = new HashMap<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) pattern.put(new BlockPos(x, 0, z),
                    new BlockPredicate.OfBlock(MMCRBlocks.CASING.get()));
        Identifier machineId = Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor");
        var machine = new DynamicMachine(machineId, "Iron Compressor", new BlockArray(pattern));
        MachineRegistry.register(machine);
        RecipeRegistry.register(new MachineRecipe(Identifier.fromNamespaceAndPath(MMCR.MODID, "iron_compressor_recipe"),
                machineId, 40,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2),
                        new MachineIngredient.EnergyIngredient(80)),
                List.of(new ItemStack(Items.IRON_NUGGET))));

        var controller = (MachineControllerBlockEntity) level.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.serverTick();
        assertTrue(controller.isFormed(), "Structure formed");
        for (int tick = 0; tick < 40; tick++) controller.serverTick();
        ItemStack output = ((ItemBusBlockEntity) level.getBlockEntity(outputPos)).getItemHandler(null).getStackInSlot(0);
        assertTrue(output.is(Items.IRON_NUGGET), "Output is iron nugget");
    }
}

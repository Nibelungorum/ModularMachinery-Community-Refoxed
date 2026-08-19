package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.item.Item;

/**
 * End-to-end data-pack machine recipe regressions.
 *
 * @author howxu <dev@howxu.cn>
 */
public class DataPackRecipeGameTest {
    private static final Identifier RUN_RECIPE = Identifier.parse("mmcr_test:datapack_iron_compressor_run");
    private static final Identifier OVERRIDE_RECIPE = Identifier.parse("mmcr_test:datapack_static_override");
    private static final Identifier ISOLATION_RECIPE = Identifier.parse("mmcr_test:datapack_valid_after_malformed");
    private static final Identifier IRON_COMPRESSOR = MMCR.id("iron_compressor");

    public void dataPackRecipeRunsOnMachine(GameTestHelper helper) {
        MachineRecipe recipe = RecipeRegistry.getRecipe(RUN_RECIPE);
        helper.assertTrue(recipe != null, "data-pack recipe should be loaded");
        helper.assertTrue(recipe.machineId().equals(IRON_COMPRESSOR), "data-pack recipe machine id");
        helper.assertTrue(RecipeRegistry.dataPackSnapshot().containsKey(RUN_RECIPE), "data-pack snapshot contains run recipe");
        helper.assertTrue(RecipeRegistry.byMachineId(IRON_COMPRESSOR).contains(recipe), "machine index exposes data-pack recipe");

        Fixture fixture = placeIronCompressor(helper, Items.COPPER_INGOT);
        for (int tick = 1; tick <= 40; tick++) {
            helper.runAtTickTime(tick, fixture.controller()::serverTick);
        }
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(fixture.controller().isFormed(), "iron compressor formed");
            ItemStack input = fixture.input().getItemHandler(null).getStackInSlot(0);
            ItemStack output = fixture.output().getItemHandler(null).getStackInSlot(0);
            helper.assertTrue(input.isEmpty(), "data-pack recipe consumed copper input");
            helper.assertTrue(output.is(Items.IRON_NUGGET), "data-pack recipe produced iron nugget");
            helper.succeed();
        });
    }

    public void dataPackRecipeOverridesStaticRecipe(GameTestHelper helper) {
        MachineRecipe effective = RecipeRegistry.getRecipe(OVERRIDE_RECIPE);
        MachineRecipe staticRecipe = RecipeRegistry.staticSnapshot().get(OVERRIDE_RECIPE);
        MachineRecipe dataPackRecipe = RecipeRegistry.dataPackSnapshot().get(OVERRIDE_RECIPE);

        helper.assertTrue(staticRecipe != null, "static game-test recipe remains registered");
        helper.assertTrue(dataPackRecipe != null, "data-pack override recipe loaded");
        helper.assertTrue(effective == dataPackRecipe, "effective recipe comes from data-pack layer");
        helper.assertTrue(staticRecipe != dataPackRecipe, "static snapshot retains original recipe object");
        helper.assertTrue(effective.machineId().equals(IRON_COMPRESSOR), "override machine id remains the test machine");
        helper.assertTrue(effective.tickTime() == 7, "effective recipe uses data-pack tick time");
        helper.assertTrue(staticRecipe.tickTime() == 20, "static recipe tick time is unchanged");
        helper.succeed();
    }

    public void malformedDataPackRecipeDoesNotBlockValidRecipe(GameTestHelper helper) {
        MachineRecipe recipe = RecipeRegistry.getRecipe(ISOLATION_RECIPE);

        helper.assertTrue(recipe != null, "valid data-pack recipe loaded despite malformed sibling");
        helper.assertTrue(recipe.machineId().equals(IRON_COMPRESSOR), "valid isolated recipe machine id");
        helper.assertTrue(RecipeRegistry.dataPackSnapshot().containsKey(ISOLATION_RECIPE), "snapshot contains isolated valid recipe");
        helper.succeed();
    }

    private static Fixture placeIronCompressor(GameTestHelper helper, Item inputItem) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());
        }
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(IRON_COMPRESSOR).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        BlockPos inputPos = new BlockPos(1, 2, 0);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemInputBusBlockEntity input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class);
        input.getItemHandler(null).insertItem(0, new ItemStack(inputItem), false);
        BlockPos outputPos = new BlockPos(1, 2, 2);
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        ItemOutputBusBlockEntity output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class);
        BlockPos energyPos = new BlockPos(2, 2, 1);
        helper.setBlock(energyPos, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());
        EnergyInputHatchBlockEntity energy = helper.getBlockEntity(energyPos, EnergyInputHatchBlockEntity.class);
        while (energy.getMutableEnergyStorage().forceInsert(10000, false) > 0) {}

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(IRON_COMPRESSOR));
        controller.serverTick();
        return new Fixture(controller, input, output);
    }

    private record Fixture(MachineControllerBlockEntity controller, ItemInputBusBlockEntity input,
                           ItemOutputBusBlockEntity output) {
    }
}

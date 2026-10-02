package cn.howxu.mmcr.compat.appmek;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Processing-pattern chemical routing, exact output matching and material ownership.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekPatternGameTest {
    public void mixedPatternReturnsProductsAndExcessInputs(GameTestHelper helper) {
        mixedPattern(helper, "ae2_me_pattern_interface", 0);
    }

    public void extendedPatternLastSlotProcessesChemicals(GameTestHelper helper) {
        mixedPattern(helper, "eae_me_extended_pattern_interface", 35);
    }

    public void radioactivePatternReturnsProductsThroughWasteBarrelStorageBus(GameTestHelper helper) {
        PatternInterfaceBlockEntity host = chemicalMachine(helper, "appmek_radioactive_pattern", false, true);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        MekanismKey waste = MekanismKey.of(AppMekGameTestFixtures.chemical("nuclear_waste", 1L));
        MekanismKey polonium = MekanismKey.of(AppMekGameTestFixtures.chemical("polonium", 1L));
        var network = AppMekGameTestFixtures.createWasteBarrelNetwork(helper, new BlockPos(0, 1, 0));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                        "Radioactive chemical pattern controller forms"))
                .thenExecute(() -> {
                    IPatternDetails pattern = installChemicalPattern(host, waste, polonium);
                    KeyCounter[] holders = holders(pattern, 1L);
                    helper.assertTrue(host.craftingMachine().pushBatchPattern(pattern, holders, 1L, Direction.NORTH),
                            "Pattern accepts radioactive input holders and radioactive output");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Accepted radioactive holders transfer ownership once");
                }).thenWaitUntil(() -> helper.assertTrue(amount(host.getLogic().getReturnInv(), polonium) == 600L
                                && amount(host.getLogic().getReturnInv(), waste) == 500L,
                        "Pattern preserves radioactive output and returns excess radioactive input"))
                .thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenWaitUntil(() -> helper.assertTrue(network.barrel().getChemicalTank().getStored() == 601L
                                && amount(host.getLogic().getReturnInv(), polonium) == 0L,
                        "Native ME storage bus transfers radioactive pattern output into the waste barrel"))
                .thenExecute(() -> helper.assertTrue(amount(host.getLogic().getReturnInv(), waste) == 500L,
                        "The polonium barrel does not consume unmatched nuclear waste"))
                .thenSucceed();
    }

    private void mixedPattern(GameTestHelper helper, String portId, int patternSlot) {
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos portPos = controllerPos.above();
        BlockPos ordinaryPos = controllerPos.below();
        ResourceLocation machineId = MMCR.id("appmek_mixed_" + patternSlot);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get(portId).get().defaultBlockState());
        helper.setBlock(ordinaryPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        DynamicMachine machine = new DynamicMachine(machineId, "AppMek pattern test", new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get(portId).get()),
                new BlockPos(0, -1, 0), new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        List<MachineRequirement> requirements = new ArrayList<>(List.of(
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 1_000)),
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 500L))));
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 250), 1F),
                new LoadedChemicalOutput(hydrogen.getId(), 600L, 1F));
        requirements.add(MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT)));
        requirements.add(MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, 250)));
        requirements.add(LoadedChemicalRequirement.output(hydrogen.getId(), 600L, 1F));
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id("appmek_mixed_recipe_" + patternSlot),
                machineId, 2, requirements, outputs, List.of(), 0, 1, false, false, false, Set.of()));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        ItemBusBlockEntity ordinary = helper.getBlockEntity(ordinaryPos);
        ordinary.nativeItemHandler().insertItem(0, new ItemStack(Items.COAL), false);
        PatternInterfaceBlockEntity host = helper.getBlockEntity(portPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                        "Pattern controller forms before a request is submitted"))
                .thenExecute(() -> {
                    ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(
                            new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L),
                            new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L), new GenericStack(oxygen, 1_000L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                                    new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L), new GenericStack(hydrogen, 600L)));
                    host.getLogic().getPatternInv().setItemDirect(patternSlot, encoded);
                    IPatternDetails pattern = host.getLogic().getAvailablePatterns().getFirst();
                    KeyCounter[] holders = holders(pattern, 1L);
                    helper.assertTrue(host.craftingMachine().pushBatchPattern(pattern, holders, 1L, Direction.NORTH),
                            "MMCR accepts a mixed item, fluid and chemical processing pattern");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Accepted pattern transfers holder ownership once");
                }).thenWaitUntil(() -> {
                    GenericStackInv returns = host.getLogic().getReturnInv();
                    helper.assertTrue(amount(returns, AEItemKey.of(Items.GOLD_INGOT)) == 1L,
                            "Item product is delivered to pattern return inventory");
                    helper.assertTrue(amount(returns, AEFluidKey.of(Fluids.LAVA)) == 250L,
                            "Fluid product is delivered to pattern return inventory");
                    helper.assertTrue(amount(returns, hydrogen) == 600L && amount(returns, oxygen) == 500L,
                            "Chemical product and excess chemical input return without loss or duplication");
                    helper.assertTrue(ordinary.nativeItemHandler().getStackInSlot(0).isEmpty(), "Ordinary remaining recipe input was consumed");
                }).thenSucceed();
    }

    public void rejectedPatternPreservesInputHolders(GameTestHelper helper) {
        PatternInterfaceBlockEntity host = chemicalMachine(helper, "appmek_rejected_pattern", false);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(), "Chemical pattern controller forms"))
                .thenExecute(() -> {
                    GenericStackInv returns = host.getLogic().getReturnInv();
                    for (int slot = 0; slot < returns.size(); slot++) {
                        returns.setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 64L));
                    }
                    IPatternDetails pattern = installChemicalPattern(host, oxygen, hydrogen);
                    KeyCounter[] holders = holders(pattern, 1L);
                    helper.assertTrue(!host.craftingMachine().pushBatchPattern(pattern, holders, 1L, Direction.NORTH),
                            "Full pattern return inventory rejects the request");
                    helper.assertTrue(holders[0].get(oxygen) == 1_000L, "Rejected request retains its input holders");
                    helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                            "Rejected request does not start the controller");
                }).thenSucceed();
    }

    public void batchPatternAccountsForChemicalInputsAcrossLanes(GameTestHelper helper) {
        PatternInterfaceBlockEntity host = chemicalMachine(helper, "appmek_batch_pattern", true);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(), "Factory pattern controller forms"))
                .thenExecute(() -> {
                    IPatternDetails pattern = installChemicalPattern(host, oxygen, hydrogen);
                    KeyCounter[] holders = holders(pattern, 2L);
                    helper.assertTrue(host.craftingMachine().pushBatchPattern(pattern, holders, 2L, Direction.NORTH),
                            "Chemical batch reserves two independent factory lanes");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Batch request releases holder ownership once");
                }).thenWaitUntil(() -> helper.assertTrue(amount(host.getLogic().getReturnInv(), hydrogen) == 1_200L
                                && amount(host.getLogic().getReturnInv(), oxygen) == 1_000L,
                        "Two lanes produce two batches and return only excess chemical inputs"))
                .thenSucceed();
    }

    public void chemicalPatternOutputMatchingPreservesLongAmounts(GameTestHelper helper) {
        PatternInterfaceBlockEntity host = chemicalMachine(helper, "appmek_long_pattern", false);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        long amount = (long) Integer.MAX_VALUE + 5_000L;
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        MachineOutput output = AppMekBridge.get().patternOutput(hydrogen, amount).orElseThrow();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("appmek_long_match_recipe"),
                MMCR.id("appmek_long_pattern"), 1, List.of(LoadedChemicalRequirement.output(hydrogen.getId(), amount, 1F)), List.of(output), List.of(), 0, 1,
                false, false, false, Set.of());
        helper.assertTrue(controller.patternOutputsMatch(recipe, controller.runtimeSnapshot(), List.of(output)),
                "Chemical pattern output conversion and matching preserve long quantities");
        helper.assertTrue(!controller.patternOutputsMatch(recipe, controller.runtimeSnapshot(),
                        List.of(new LoadedChemicalOutput(hydrogen.getId(), amount - 1L, 1F))),
                "Different chemical output quantity does not match");
        helper.assertTrue(!controller.patternOutputsMatch(recipe, controller.runtimeSnapshot(),
                        List.of(new LoadedChemicalOutput(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), amount, 1F))),
                "Different chemical output identity does not match");
        helper.succeed();
    }

    private PatternInterfaceBlockEntity chemicalMachine(GameTestHelper helper, String name, boolean factory) {
        return chemicalMachine(helper, name, factory, false);
    }

    private PatternInterfaceBlockEntity chemicalMachine(GameTestHelper helper, String name, boolean factory, boolean radioactive) {
        ResourceLocation machineId = MMCR.id(name);
        BlockPos portPos = new BlockPos(0, 1, 0);
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();
        blocks.put(portPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()));
        if (factory) {
            for (int index = 1; index <= 2; index++) {
                BlockPos pos = new BlockPos(index, 0, 0);
                helper.setBlock(pos, ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
                blocks.put(pos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
            }
        }
        DynamicMachine machine = factory ? new DynamicMachine(machineId, "AppMek factory pattern test", new BlockArray(blocks),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 2)
                : new DynamicMachine(machineId, "AppMek chemical pattern test", new BlockArray(blocks));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        ResourceLocation oxygen = ResourceLocation.fromNamespaceAndPath("mekanism", radioactive ? "nuclear_waste" : "oxygen");
        ResourceLocation hydrogen = ResourceLocation.fromNamespaceAndPath("mekanism", radioactive ? "polonium" : "hydrogen");
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id(name + "_recipe"), machineId, 2,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen, 500L)),
                        LoadedChemicalRequirement.output(hydrogen, 600L, 1F)),
                List.of(new LoadedChemicalOutput(hydrogen, 600L, 1F)), List.of(), 0, 2,
                false, false, false, Set.of()));
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        return helper.getBlockEntity(portPos);
    }

    private IPatternDetails installChemicalPattern(PatternInterfaceBlockEntity host, MekanismKey oxygen, MekanismKey hydrogen) {
        host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(oxygen, 1_000L)), List.of(new GenericStack(hydrogen, 600L))));
        return host.getLogic().getAvailablePatterns().getFirst();
    }

    private KeyCounter[] holders(IPatternDetails pattern, long batches) {
        KeyCounter[] holders = new KeyCounter[pattern.getInputs().length];
        for (int index = 0; index < holders.length; index++) {
            var input = pattern.getInputs()[index];
            GenericStack stack = input.getPossibleInputs()[0];
            holders[index] = new KeyCounter();
            holders[index].add(stack.what(), Math.multiplyExact(Math.multiplyExact(stack.amount(), input.getMultiplier()), batches));
        }
        return holders;
    }

    private long amount(GenericStackInv inventory, AEKey key) {
        long amount = 0L;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (key.equals(inventory.getKey(slot))) amount += inventory.getAmount(slot);
        }
        return amount;
    }
}

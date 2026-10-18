package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.jade.RecipeOutputCodec;
import cn.howxu.mmcr.compat.jade.RecipeOutputDataProvider;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import snownee.jade.impl.BlockAccessorImpl;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Verifies CPU admission and settlement for independently stored output components.
 * @author howxu <dev@howxu.cn>
 */
public class AE2PatternOutputComponentGameTest {
    public void craftingCpuSettlesLargeInputsOutputsAndLeftovers(GameTestHelper helper) {
        var machineId = MMCR.id("ae2_large_pattern_return_test");
        var recipeId = MMCR.id("ae2_large_pattern_return_recipe");
        BlockPos portPos = new BlockPos(0, 1, 0);
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        BlockPos cpuPos = new BlockPos(3, 0, 4);
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        var machine = new DynamicMachine(machineId, "AE2 Large Pattern Return Test", new BlockArray(Map.of(
                portPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        ItemStack output = new ItemStack(Items.GOLD_INGOT, 20_000);
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 1,
                    List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                            MachineRequirement.itemOutput(output)), List.of(new MachineOutput.ItemOutput(output, 1F)),
                    List.of(), 0, 1, false, false, false, Set.of()));
        }
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        PatternInterfaceBlockEntity port = helper.getBlockEntity(portPos);
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setCell(AEItems.ITEM_CELL_64K.stack());
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        CraftingBlockEntity cpuBlock = helper.getBlockEntity(cpuPos);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(port.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                            && energy.getMainNode().getNode() != null && cpuBlock.getMainNode().getNode() != null,
                    "Large pattern test waits for actual AE2 node initialization");
        }).thenExecute(() -> {
            GridHelper.createConnection(port.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), cpuBlock.getMainNode().getNode());
            controller.requestImmediateStructureCheck();
        }).thenWaitUntil(() -> {
            helper.assertTrue(controller.structureSnapshot().formed() && port.getMainNode().isActive()
                            && cpuBlock.getCluster() != null && cpuBlock.getCluster().isActive(),
                    "Actual controller and CPU are ready for admission");
        }).thenExecute(() -> {
            port.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(iron, 10_000L)), List.of(new GenericStack(gold, 20_000L))));
            port.getLogic().updatePatterns();
            IPatternDetails pattern = port.getLogic().getAvailablePatterns().getFirst();
            var grid = port.getMainNode().getNode().getGrid();
            KeyCounter usedItems = new KeyCounter();
            usedItems.add(iron, 10_000L);
            helper.assertTrue(grid.getStorageService().getInventory().insert(iron, 10_000L,
                    Actionable.MODULATE, new BaseActionSource()) == 10_000L, "Network stores all ten thousand input items");
            ICraftingPlan plan = new ICraftingPlan() {
                @Override public GenericStack finalOutput() { return new GenericStack(gold, 20_000L); }
                @Override public long bytes() { return 1L; }
                @Override public boolean simulation() { return false; }
                @Override public boolean multiplePaths() { return false; }
                @Override public KeyCounter usedItems() { return usedItems; }
                @Override public KeyCounter emittedItems() { return new KeyCounter(); }
                @Override public KeyCounter missingItems() { return new KeyCounter(); }
                @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(pattern, 1L); }
            };
            var cpu = cpuBlock.getCluster().craftingLogic;
            helper.assertTrue(cpu.trySubmitJob(grid, plan, new BaseActionSource(), null).successful(),
                    "CPU accepts the large encoded processing plan");
            helper.assertTrue(cpu.executeCrafting(1, (CraftingService) grid.getCraftingService(),
                            grid.getEnergyService(), helper.getLevel()) == 1 && cpu.getWaitingFor(gold) == 20_000L,
                    "Real CPU dispatch admits large leftovers and reserves the full output");
             helper.assertTrue(port.pendingPatterns().isEmpty()
                             && controller.runtimeSnapshot().crafting().recipeId() != null,
                     "Standard CPU dispatch synchronously starts exactly one actual recipe");
            helper.assertTrue(port.getLogic().getReturnInv().getAvailableStacks().get(iron) == 9_999L,
                    "Exactly one item is consumed and 9999 leftovers are safely owned by the return inventory");
        }).thenWaitUntil(() -> {
            helper.assertTrue(chest.getInventory().extract(gold, 20_000L, Actionable.SIMULATE, new BaseActionSource()) == 20_000L
                            && chest.getInventory().extract(iron, 9_999L, Actionable.SIMULATE, new BaseActionSource()) == 9_999L,
                    "Native return work drains both large outputs and leftover inputs exactly");
            helper.assertTrue(!cpuBlock.getCluster().craftingLogic.hasJob(), "AE2 CPU settles the large output");
        }).thenSucceed();
    }

    public void craftingCpuPushesResolvedEnchantmentOutput(GameTestHelper helper) {
        var machineId = MMCR.id("ae2_pattern_output_components_test");
        var recipeId = MMCR.id("ae2_pattern_output_components_recipe");
        BlockPos portPos = new BlockPos(0, 1, 0);
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        BlockPos cpuPos = new BlockPos(3, 0, 4);
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());

        var machine = new DynamicMachine(machineId, "AE2 Pattern Output Components Test", new BlockArray(Map.of(
                portPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        JsonObject enchantments = new JsonObject();
        JsonObject levels = new JsonObject();
        levels.addProperty("minecraft:sharpness", 4);
        enchantments.add("levels", levels);
        ItemStack base = new ItemStack(Items.GOLD_INGOT, 9);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("What a magic recipe"));
        var output = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, base, 1F,
                new DataComponentPredicateSet(Map.of(DataComponents.ENCHANTMENTS,
                        ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, enchantments)))), 1F);
        if (!RecipeRegistry.containsStatic(recipeId)) {
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 1,
                    List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                            output), List.of(OutputRegistry.fromRequirement(output)),
                    List.of(), 0, 1, false, false, false, Set.of()));
        }
        ItemStack expected = base.copy();
        expected.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 4);
        AEItemKey outputKey = AEItemKey.of(expected);
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        PatternInterfaceBlockEntity port = helper.getBlockEntity(portPos);
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        CraftingBlockEntity cpuBlock = helper.getBlockEntity(cpuPos);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(port.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                            && energy.getMainNode().getNode() != null && cpuBlock.getMainNode().getNode() != null,
                    "AE2 nodes initialize before connecting the crafting network");
        }).thenExecute(() -> {
            GridHelper.createConnection(port.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), energy.getMainNode().getNode());
            GridHelper.createConnection(port.getMainNode().getNode(), cpuBlock.getMainNode().getNode());
            controller.requestImmediateStructureCheck();
        }).thenWaitUntil(() -> {
            helper.assertTrue(controller.structureSnapshot().formed() && port.getMainNode().isActive()
                            && cpuBlock.getCluster() != null && cpuBlock.getCluster().isActive(),
                    "Controller forms and the crafting network becomes active");
        }).thenExecute(() -> {
            helper.assertTrue(port.maxRecipeBatchSize(List.of(new MachineOutput.ItemOutput(expected, 1F))) > 0L,
                    "Component-bearing pattern has a nonzero recipe admission capacity");
            ItemStack wrongLevel = expected.copy();
            wrongLevel.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS), 5);
            helper.assertTrue(port.maxRecipeBatchSize(List.of(new MachineOutput.ItemOutput(wrongLevel, 1F))) == 0L,
                    "Sharpness V pattern is rejected before CPU dispatch");
            port.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)),
                    List.of(new GenericStack(outputKey, 9L))));
            port.getLogic().updatePatterns();
            IPatternDetails pattern = port.getLogic().getAvailablePatterns().getFirst();
            var grid = port.getMainNode().getNode().getGrid();
            KeyCounter usedItems = new KeyCounter();
            usedItems.add(AEItemKey.of(Items.IRON_INGOT), 1L);
            helper.assertTrue(grid.getStorageService().getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 1L,
                            Actionable.MODULATE, new BaseActionSource()) == 1L,
                    "Crafting network stores the recipe input");
            ICraftingPlan plan = new ICraftingPlan() {
                @Override public GenericStack finalOutput() { return new GenericStack(outputKey, 9L); }
                @Override public long bytes() { return 1L; }
                @Override public boolean simulation() { return false; }
                @Override public boolean multiplePaths() { return false; }
                @Override public KeyCounter usedItems() { return usedItems; }
                @Override public KeyCounter emittedItems() { return new KeyCounter(); }
                @Override public KeyCounter missingItems() { return new KeyCounter(); }
                @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(pattern, 1L); }
            };
            var cpu = cpuBlock.getCluster().craftingLogic;
            helper.assertTrue(cpu.trySubmitJob(grid, plan, new BaseActionSource(), null).successful(),
                    "CPU accepts the component-bearing processing job");
            helper.assertTrue(cpu.executeCrafting(1, (CraftingService) grid.getCraftingService(),
                            grid.getEnergyService(), helper.getLevel()) == 1,
                    "CPU pushes the pattern whose recipe enchantment is stored independently");
            helper.assertTrue(cpu.getWaitingFor(outputKey) == 9L,
                    "CPU waits for the exact enchanted and named output");
             helper.assertTrue(port.pendingPatterns().isEmpty()
                             && controller.runtimeSnapshot().crafting().recipeId() != null,
                     "Component-bearing output starts synchronously on the CPU push");
            var accessor = new BlockAccessorImpl.Builder().level(helper.getLevel())
                    .blockState(controller.getBlockState()).blockEntity(() -> controller).build();
            CompoundTag jadeData = new CompoundTag();
            RecipeOutputDataProvider.INSTANCE.appendServerData(jadeData, accessor);
            var jadeOutputs = RecipeOutputCodec.read(jadeData, accessor.nbtOps());
            helper.assertTrue(jadeOutputs.size() == 1,
                     "Jade receives the active enchanted output after synchronous ME pattern execution");
            helper.assertTrue(jadeOutputs.getFirst().amount() == 9L
                            && ItemStack.isSameItemSameComponents(expected,
                            ((MachineOutput.ItemOutput) jadeOutputs.getFirst().output()).resolvedStack()),
                    "ME pattern output reaches Jade with the exact custom name and enchantments");
        }).thenWaitUntil(() -> {
            helper.assertTrue(chest.getInventory().extract(outputKey, 9L, Actionable.SIMULATE,
                            new BaseActionSource()) == 9L,
                    "All nine outputs return with the pattern's exact enchantment and custom name");
            helper.assertTrue(!cpuBlock.getCluster().craftingLogic.hasJob(),
                    "CPU settles the job after receiving the matching component-bearing output");
        }).thenSucceed();
    }
}

package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;

/** Mixed native patterns exercise real controller admission and shared batch material ownership.
 * @author howxu <dev@howxu.cn> */
public final class LightningPatternGameTest {
    public void mixedPatternAdmissionAndPersistence(GameTestHelper helper) { mixedPattern(helper, false); }
    public void mixedBatchPreservesOneMaterialOwner(GameTestHelper helper) { mixedPattern(helper, true); }

    private void mixedPattern(GameTestHelper helper, boolean factory) {
        var machineId = MMCR.id(factory ? "lightning_factory_pattern_test" : "lightning_pattern_test");
        var recipeId = MMCR.id(machineId.getPath() + "_recipe");
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos portPos = controllerPos.above();
        var portBlock = ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get();
        helper.setBlock(portPos, portBlock.defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(portBlock));
        if (factory) for (int index = 1; index <= 2; index++) {
            var factoryBlock = ModBlocks.BLOCKS.get("factory_controller").get();
            helper.setBlock(controllerPos.offset(index, 0, 0), factoryBlock.defaultBlockState());
            blocks.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(factoryBlock));
        }
        var machine = factory ? new DynamicMachine(machineId, "Lightning factory pattern test", new BlockArray(blocks),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 2)
                : new DynamicMachine(machineId, "Lightning pattern test", new BlockArray(blocks));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F),
                new LightningOutput(HIGH_VOLTAGE, 4L, 1F), new LightningOutput(EXTREME_HIGH_VOLTAGE, 5L, 1F));
        List<MachineRequirement> requirements = new ArrayList<>(List.of(
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)),
                LightningRequirement.input(HIGH_VOLTAGE, 2L), LightningRequirement.input(EXTREME_HIGH_VOLTAGE, 3L)));
        outputs.forEach(output -> requirements.add(OutputRegistry.toRequirement(output, List.of())));
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 2, requirements, outputs,
                List.of(), 0, factory ? 2 : 1, false, false, false, Set.of()));
        PatternInterfaceBlockEntity host = helper.getBlockEntity(portPos);
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        long batches = factory ? 2L : 1L;
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(), "Mixed lightning pattern controller forms"))
                .thenExecute(() -> {
                    host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(iron, 3L), new GenericStack(water, 1_000L),
                                    new GenericStack(high, 5L), new GenericStack(extreme, 7L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                                    new GenericStack(high, 4L), new GenericStack(extreme, 5L))));
                    var pattern = host.getLogic().getAvailablePatterns().getFirst();
                    var holders = new KeyCounter();
                    holders.add(iron, 3L * batches);
                    holders.add(water, 1_000L * batches);
                    holders.add(high, 5L * batches);
                    holders.add(extreme, 7L * batches);
                    helper.assertTrue(host.craftingMachine().pushBatchPattern(pattern, new KeyCounter[]{holders}, batches, Direction.NORTH),
                            "Native mixed pattern reaches recipe admission with both lightning tiers");
                    helper.assertTrue(holders.isEmpty(), "Successful batch claims original holders exactly once");
                    if (!factory) {
                        CompoundTag paid = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
                        controller.loadWithComponents(paid, helper.getLevel().registryAccess());
                        controller.requestImmediateStructureCheck();
                    }
                }).thenWaitUntil(() -> {
                    helper.assertTrue(amount(host, AEItemKey.of(Items.GOLD_INGOT)) == batches && amount(host, iron) == 2L * batches
                                    && amount(host, water) == 750L * batches && amount(host, high) == 7L * batches && amount(host, extreme) == 9L * batches,
                            "Products plus exact leftovers settle once for every lane and family, including a paid controller reload");
                }).thenExecute(() -> {
                    CompoundTag saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
                    host.getLogic().getReturnInv().clear();
                    host.loadWithComponents(saved, helper.getLevel().registryAccess());
                    helper.assertTrue(amount(host, high) == 7L * batches && amount(host, extreme) == 9L * batches,
                            "Native return inventory NBT preserves both tier balances");
                    host.craftingMachine().flushPendingReturns();
                    helper.assertTrue(amount(host, high) == 7L * batches, "Repeating return settlement cannot duplicate paid lightning");
                }).thenSucceed();
    }

    private static long amount(PatternInterfaceBlockEntity host, AEKey key) {
        long amount = 0L;
        var inventory = host.getLogic().getReturnInv();
        for (int slot = 0; slot < inventory.size(); slot++) if (key.equals(inventory.getKey(slot))) amount += inventory.getAmount(slot);
        return amount;
    }
}

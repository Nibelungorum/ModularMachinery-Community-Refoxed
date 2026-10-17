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
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternInterfaceCraftingMachine;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongSlotStorage;
import cn.howxu.mmcr.registry.ModBlocks;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import mekanism.api.chemical.attribute.ChemicalAttributes;
import mekanism.api.radiation.IRadiationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
                    KeyCounter[] holders = holders(pattern);
                    helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, Direction.NORTH),
                            "Pattern accepts radioactive input holders and radioactive output");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Accepted radioactive holders transfer ownership once");
                    host.pendingScheduler().tick(helper.getLevel().getGameTime() + 5L);
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
                    KeyCounter[] holders = holders(pattern);
                    helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, Direction.NORTH),
                            "MMCR accepts a mixed item, fluid and chemical processing pattern");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Accepted pattern transfers holder ownership once");
                    host.pendingScheduler().tick(helper.getLevel().getGameTime() + 5L);
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
                    KeyCounter[] malformed = holders(pattern);
                    malformed[0].add(oxygen, 1L);
                    helper.assertFalse(host.craftingMachine().pushPattern(pattern, malformed, Direction.NORTH),
                            "A nonstandard per-copy amount is rejected");
                    helper.assertTrue(malformed[0].get(oxygen) == 1_001L, "Rejected request retains its input holders");
                    KeyCounter[] holders = holders(pattern);
                    helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, Direction.NORTH),
                            "Full returns can still admit one standard request into the queue");
                    for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Queued request takes holder ownership");
                    long now = helper.getLevel().getGameTime();
                    host.pendingScheduler().tick(now + 5L);
                    helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                            "Full returns block execution rather than losing the accepted request");
                    helper.assertTrue(queuedAmount(host, oxygen) == 1_000L, "Blocked queue retains all accepted chemicals");
                    returns.clear();
                    host.pendingScheduler().tick(now + 6L);
                    helper.assertTrue(host.pendingPatterns().isEmpty()
                                    && controller.runtimeSnapshot().crafting().recipeId() != null,
                            "Freeing return capacity starts the retained request without another provider payment");
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
                    long submitted = 0L;
                    long now = helper.getLevel().getGameTime();
                    for (int copy = 0; copy < 2; copy++) {
                        KeyCounter[] holders = holders(pattern);
                        submitted += holders[0].get(oxygen);
                        helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, Direction.NORTH),
                                "Each chemical request admits exactly one standard copy");
                        for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(), "Each request releases holder ownership once");
                    }
                    helper.assertTrue(queuedAmount(host, oxygen) == submitted && submitted == 2_000L,
                            "The queue owns the accumulated material of both independent callers");
                    host.pendingScheduler().tick(now);
                    helper.assertTrue(controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                            "Same-tick admission never starts a factory lane");
                    host.pendingScheduler().tick(now + 5L);
                    host.pendingScheduler().tick(now + 6L);
                }).thenWaitUntil(() -> helper.assertTrue(amount(host.getLogic().getReturnInv(), hydrogen) == 1_200L
                                && amount(host.getLogic().getReturnInv(), oxygen) == 1_000L,
                        "Two lanes produce two batches and return only excess chemical inputs"))
                .thenSucceed();
    }

    public void nativeBatchNotificationBoundary(GameTestHelper helper, boolean throwing) {
        nativeMixedBatchBoundary(helper, throwing ? NativeBoundary.THROW : NativeBoundary.CANCEL, false);
    }

    public void nativeBatchPendingCheckpoint(GameTestHelper helper, boolean drop) {
        nativeMixedBatchBoundary(helper, NativeBoundary.CAPACITY, drop);
    }

    public void nativeBatchReturnNotificationCheckpoint(GameTestHelper helper) {
        nativeMixedBatchBoundary(helper, NativeBoundary.RETURN_NOTIFICATION, false);
    }

    /** Faults enter through real native bus/return-inventory listeners, not a replacement crafting machine.
     * @author howxu <dev@howxu.cn> */
    private enum NativeBoundary { CANCEL, THROW, CAPACITY, RETURN_NOTIFICATION }

    private void nativeMixedBatchBoundary(GameTestHelper helper, NativeBoundary boundary, boolean drop) {
        String name = "appmek_native_boundary_" + boundary.name().toLowerCase(Locale.ROOT) + "_" + drop;
        ResourceLocation machineId = MMCR.id(name);
        BlockPos portPos = new BlockPos(0, 1, 0);
        BlockPos ordinaryPos = new BlockPos(0, 0, 1);
        helper.setBlock(BlockPos.ZERO, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        helper.setBlock(ordinaryPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();
        blocks.put(portPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()));
        blocks.put(ordinaryPos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()));
        for (int index = 1; index <= 2; index++) {
            BlockPos pos = new BlockPos(index, 0, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
            blocks.put(pos, new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()));
        }
        DynamicMachine machine = new DynamicMachine(machineId, "Native pattern ownership test", new BlockArray(blocks),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 2);
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical(drop ? "nuclear_waste" : "oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        List<MachineRequirement> requirements = List.of(
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)),
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 500L)),
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT)),
                MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, 250)),
                LoadedChemicalRequirement.output(hydrogen.getId(), 600L, 1F));
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 250), 1F),
                new LoadedChemicalOutput(hydrogen.getId(), 600L, 1F));
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id(name + "_recipe"), machineId, 200,
                requirements, outputs, List.of(), 0, 2, false, false, false, Set.of()));
        MachineControllerBlockEntity controller = helper.getBlockEntity(BlockPos.ZERO);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        ItemBusBlockEntity ordinary = helper.getBlockEntity(ordinaryPos);
        ordinary.nativeItemHandler().setContents(0, new ItemStack(Items.COAL, 2), 2L);
        PatternInterfaceBlockEntity host = helper.getBlockEntity(portPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                        "Real mixed-resource factory forms before standard pattern admission"))
                .thenExecute(() -> {
                    host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(List.of(
                                    new GenericStack(iron, 2L), new GenericStack(water, 1_000L), new GenericStack(oxygen, 1_000L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                                    new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L), new GenericStack(hydrogen, 600L))));
                    IPatternDetails pattern = host.getLogic().getAvailablePatterns().getFirst();
                    // Separate callers each pay one unchanged pattern; proposals own only the dispatched prefix.
                    KeyCounter[][] requests = {holders(pattern), holders(pattern)};
                    KeyCounter[] holders = Arrays.stream(requests).flatMap(Arrays::stream).toArray(KeyCounter[]::new);
                    long submittedIron = 0L, submittedWater = 0L, submittedOxygen = 0L;
                    for (KeyCounter holder : holders) {
                        submittedIron += holder.get(iron);
                        submittedWater += holder.get(water);
                        submittedOxygen += holder.get(oxygen);
                    }
                    for (KeyCounter[] request : requests) {
                        helper.assertTrue(host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                                "A standard mixed-resource caller transfers exactly one copy into the queue");
                    }
                    helper.assertTrue(queuedAmount(host, iron) == submittedIron && submittedIron == 4L
                                    && queuedAmount(host, water) == submittedWater && submittedWater == 2_000L
                                    && queuedAmount(host, oxygen) == submittedOxygen && submittedOxygen == 2_000L,
                            "Queued totals equal the material independently supplied by both callers");
                    long now = helper.getLevel().getGameTime();
                    host.pendingScheduler().tick(now);
                    helper.assertTrue(ordinary.nativeItemHandler().amount(0) == 2L,
                            "First-tick admission does not pay ordinary inputs");
                    AtomicInteger notifications = new AtomicInteger();
                    AtomicReference<CompoundTag> insideCheckpoint = new AtomicReference<>();
                    Runnable fault = () -> {
                        notifications.incrementAndGet();
                        insideCheckpoint.set(host.saveWithoutMetadata(helper.getLevel().registryAccess()));
                        for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(),
                                "Native checkpoint observes that admission already transferred the original holders");
                        KeyCounter[] reentrant = holders(pattern);
                        KeyCounter[] expected = holders(pattern);
                        helper.assertFalse(host.craftingMachine().pushPattern(pattern, reentrant, Direction.NORTH),
                                "Native payment and return notifications cannot reenter standard admission");
                        for (int index = 0; index < reentrant.length; index++) {
                            for (var entry : expected[index]) {
                                helper.assertTrue(reentrant[index].get(entry.getKey()) == entry.getLongValue(),
                                        "Rejected reentrant callers retain their independent material");
                            }
                        }
                        // A later provider deposit proves outer acceptance never clears these holders a second time.
                        holders[0].add(iron, 1L);
                        switch (boundary) {
                            case CANCEL -> factoryRuntime(controller).cancelAsyncState();
                            case THROW -> throw new IllegalStateException("real ordinary input notification");
                            case CAPACITY -> fillReturns(host.getLogic().getReturnInv());
                            case RETURN_NOTIFICATION -> throw new IllegalStateException("real return endBatch notification");
                        }
                    };
                    Object listenerOwner = boundary == NativeBoundary.RETURN_NOTIFICATION ? host.getLogic().getReturnInv()
                            : fieldValue(ordinary.nativeItemHandler(), LongItemStorage.class, "storage");
                    Runnable restoreListener = observeOnce(listenerOwner,
                            boundary == NativeBoundary.RETURN_NOTIFICATION ? GenericStackInv.class : LongSlotStorage.class,
                            boundary == NativeBoundary.RETURN_NOTIFICATION ? "listener" : "onChange", fault);
                    try {
                        host.pendingScheduler().tick(now + 5L);
                    } finally {
                        restoreListener.run();
                    }
                    helper.assertTrue(notifications.get() == 1 && insideCheckpoint.get() != null,
                            "The injected fault ran inside one actual native notification");
                    long consumedBatches = 2L - ordinary.nativeItemHandler().amount(0);
                    helper.assertTrue(consumedBatches == 1L && queuedAmount(host, iron) == 2L,
                            "One scheduler pass pays only its dispatched prefix and retains the second caller's copy");
                    helper.assertTrue(holders[0].get(iron) == 1L
                                    && Arrays.stream(holders).skip(1L).allMatch(KeyCounter::isEmpty),
                            "Original holders hand off once and the later provider deposit remains owned by its holder");
                    CompoundTag settled = host.saveWithoutMetadata(helper.getLevel().registryAccess());
                    assertNativeConservation(helper, host, settled, iron, water, oxygen, consumedBatches, holders);
                    if (boundary == NativeBoundary.CAPACITY) {
                        GenericStackInv pending = pendingInventory(helper, settled);
                        helper.assertTrue(amount(host.getLogic().getReturnInv(), iron) == 0L
                                        && amount(pending, iron) + queuedAmount(host, iron) == 4L - consumedBatches
                                        && amount(pending, water) + queuedAmount(host, water) == 2_000L - 250L * consumedBatches
                                        && amount(pending, oxygen) + queuedAmount(host, oxygen) == 2_000L - 500L * consumedBatches,
                                "Changed return capacity preserves scratch leftovers separately from the unstarted queued copy");
                    }
                    if (boundary != NativeBoundary.RETURN_NOTIFICATION) {
                        GenericStackInv pendingAtPayment = pendingInventory(helper, insideCheckpoint.get());
                        helper.assertTrue(amount(pendingAtPayment, iron) == 1L && amount(pendingAtPayment, water) == 750L
                                        && amount(pendingAtPayment, oxygen) == 500L,
                                "Native payment-window NBT contains only actual shared inventory leftovers");
                    }
                    long coalBeforeRetry = ordinary.nativeItemHandler().amount(0);
                    helper.assertFalse(host.craftingMachine().pushPattern(pattern, requests[0], Direction.NORTH),
                            "A repeated provider call cannot admit another copy from the retained sentinel alone");
                    helper.assertTrue(ordinary.nativeItemHandler().amount(0) == coalBeforeRetry && holders[0].get(iron) == 1L,
                            "Rejected replay leaves all remaining holder and ordinary materials untouched");
                    factoryRuntime(controller).cancelAsyncState();
                    // Load the real BE, not just PendingRequest or a manually returned request inventory.
                    host.onChunkUnloaded();
                    var fresh = new PatternInterfaceBlockEntity(host.getBlockPos(), host.getBlockState(), host.kind());
                    helper.getLevel().setBlockEntity(fresh);
                    fresh.loadWithComponents(settled, helper.getLevel().registryAccess());
                    assertNativeConservation(helper, fresh, fresh.saveWithoutMetadata(helper.getLevel().registryAccess()),
                            iron, water, oxygen, consumedBatches, holders);
                    if (drop) {
                        AABB area = new AABB(fresh.getBlockPos()).inflate(2D);
                        GenericStackInv ownedBeforeDrop = pendingInventory(helper,
                                fresh.saveWithoutMetadata(helper.getLevel().registryAccess()));
                        long pendingIron = amount(ownedBeforeDrop, iron);
                        long voidedWater = amount(ownedBeforeDrop, water);
                        long dumpedChemical = amount(ownedBeforeDrop, oxygen);
                        helper.assertTrue(pendingIron == 1L && voidedWater == 750L && dumpedChemical == 500L
                                        && queuedAmount(fresh, iron) == 2L && queuedAmount(fresh, water) == 1_000L
                                        && queuedAmount(fresh, oxygen) == 1_000L,
                                "Before disposal: paid scratch=" + pendingIron + "/" + voidedWater + "/" + dumpedChemical
                                        + ", queued=" + queuedAmount(fresh, iron) + "/" + queuedAmount(fresh, water)
                                        + "/" + queuedAmount(fresh, oxygen));
                        Object owned = fieldValue(fresh.craftingMachine(), PatternInterfaceCraftingMachine.class, "pendingRequest");
                        GenericStackInv liveScratch = (GenericStackInv) fieldValue(owned, owned.getClass(), "inventory");
                        AtomicInteger chemicalDisposals = new AtomicInteger();
                        AtomicReference<Double> radiationAtChemicalClear = new AtomicReference<>();
                        long[] chemicalBalance = {dumpedChemical};
                        Runnable restoreScratchListener = observeChanges(liveScratch, GenericStackInv.class, "listener", () -> {
                            long remaining = amount(liveScratch, oxygen);
                            if (remaining < chemicalBalance[0]) {
                                chemicalDisposals.incrementAndGet();
                                radiationAtChemicalClear.set(radiationAt(helper, fresh.getBlockPos()));
                            }
                            chemicalBalance[0] = remaining;
                        });
                        // Observe actual spawn attempts, before entity-section visibility can affect a world query.
                        List<ItemEntity> drops = new ArrayList<>();
                        AtomicInteger canceledDrops = new AtomicInteger();
                        Consumer<EntityJoinLevelEvent> capture = event -> {
                            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                                    && area.contains(item.position())) {
                                if (event.isCanceled()) canceledDrops.incrementAndGet();
                                else drops.add(item);
                            }
                        };
                        double beforeRadiation = radiationAt(helper, fresh.getBlockPos());
                        double afterRadiation;
                        long queriedIron;
                        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityJoinLevelEvent.class, capture);
                        try {
                            fresh.dropContents();
                            afterRadiation = radiationAt(helper, fresh.getBlockPos());
                            queriedIron = dropAmount(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area), iron);
                            int firstDropCount = drops.size();
                            fresh.dropContents();
                            helper.assertTrue(drops.size() == firstDropCount
                                            && radiationAt(helper, fresh.getBlockPos()) == afterRadiation,
                                    "A second native disposal emits no more items or radiation: first=" + afterRadiation
                                            + ", second=" + radiationAt(helper, fresh.getBlockPos()));
                        } finally {
                            NeoForge.EVENT_BUS.unregister(capture);
                            restoreScratchListener.run();
                        }
                        double expectedRadiation = IRadiationManager.INSTANCE.isRadiationEnabled()
                                ? dumpedChemical * oxygen.getStack().getChemical().get(ChemicalAttributes.Radiation.class).getRadioactivity() : 0D;
                        String observed = "pending=" + pendingIron + "/" + voidedWater + "/" + dumpedChemical
                                + ", spawnedIron=" + dropAmount(drops, iron) + ", queriedIron=" + queriedIron
                                + ", waterDrops=" + dropAmount(drops, water) + ", chemicalDrops=" + dropAmount(drops, oxygen)
                                + ", canceledSpawns=" + canceledDrops.get() + ", chemicalDisposals=" + chemicalDisposals.get()
                                + ", radiation=" + beforeRadiation + "/" + radiationAtChemicalClear.get() + "/" + afterRadiation
                                + ", expectedDelta=" + expectedRadiation;
                        helper.assertTrue(chemicalDisposals.get() == 1 && radiationAtChemicalClear.get() != null
                                        && radiationAtChemicalClear.get() == afterRadiation,
                                "One actual scratch chemical clearance follows its synchronous native radiation dump: " + observed);
                        helper.assertTrue(Math.abs(afterRadiation - beforeRadiation - expectedRadiation)
                                        <= Math.max(1E-12D, expectedRadiation * 1E-9D),
                                "Native chemical disposal radiates exactly the paid scratch balance, never the queued copy: " + observed);
                        helper.assertTrue(dropAmount(drops, iron) == pendingIron && canceledDrops.get() == 0
                                        && dropAmount(drops, water) == 0L && dropAmount(drops, oxygen) == 0L,
                                "Actual native drop policy emits item leftovers, voids fluids and dumps chemical radiation: " + observed);
                        helper.assertTrue(voidedWater + 250L * consumedBatches + queuedAmount(fresh, water) == 2_000L
                                        && dumpedChemical + 500L * consumedBatches + queuedAmount(fresh, oxygen) == 2_000L
                                        && holders[0].get(iron) + consumedBatches + dropAmount(drops, iron) + queuedAmount(fresh, iron) == 5L,
                                "Consumed + holder + unstarted queue + native drops/disposal conserves all original material");
                        helper.assertTrue(pendingInventory(helper, fresh.saveWithoutMetadata(helper.getLevel().registryAccess())).isEmpty(),
                                "Dropped paid leftovers cannot be restored or settled again");
                        helper.assertTrue(queuedAmount(fresh, iron) == 2L && queuedAmount(fresh, water) == 1_000L
                                        && queuedAmount(fresh, oxygen) == 1_000L,
                                "Native scratch disposal leaves the unstarted copy owned by the host's persistent queue");
                    } else {
                        if (boundary == NativeBoundary.CAPACITY) fresh.getLogic().getReturnInv().clear();
                        fresh.serverTick();
                        fresh.serverTick();
                        CompoundTag afterTick = fresh.saveWithoutMetadata(helper.getLevel().registryAccess());
                        helper.assertTrue(pendingInventory(helper, afterTick).isEmpty(),
                                "Fresh native BE ticks settle the saved debt without another recipe start");
                        helper.assertTrue(queuedAmount(fresh, iron) == 2L,
                                "Settling paid leftovers does not execute or discard the saved unstarted copy");
                        assertNativeConservation(helper, fresh, afterTick, iron, water, oxygen, consumedBatches, holders);
                    }
                    helper.assertTrue(ordinary.nativeItemHandler().amount(0) == coalBeforeRetry,
                            "Reload, return settlement and drops never repay ordinary inputs");
                }).thenSucceed();
    }

    private void assertNativeConservation(GameTestHelper helper, PatternInterfaceBlockEntity host, CompoundTag saved,
                                          AEKey iron, AEKey water, AEKey oxygen, long consumed, KeyCounter[] holders) {
        GenericStackInv pending = pendingInventory(helper, saved);
        GenericStackInv returns = host.getLogic().getReturnInv();
        long holderIron = 0L;
        for (KeyCounter holder : holders) holderIron += holder.get(iron);
        helper.assertTrue(holderIron + consumed + amount(returns, iron) + amount(pending, iron) + queuedAmount(host, iron) == 5L,
                "Holder + consumed + returns + scratch + queue conserves original iron and one later provider deposit");
        helper.assertTrue(250L * consumed + amount(returns, water) + amount(pending, water) + queuedAmount(host, water) == 2_000L,
                "Consumed water + actual returns + pending water conserves the original mixed request");
        helper.assertTrue(500L * consumed + amount(returns, oxygen) + amount(pending, oxygen) + queuedAmount(host, oxygen) == 2_000L,
                "Consumed chemical + actual returns + pending chemical conserves the original mixed request");
    }

    private static GenericStackInv pendingInventory(GameTestHelper helper, CompoundTag saved) {
        return AE2NativeAdapters.restoreRequestInventory(saved.getList("pattern_pending_returns", Tag.TAG_COMPOUND),
                helper.getLevel().registryAccess());
    }

    private static void fillReturns(GenericStackInv returns) {
        returns.beginBatch();
        try {
            for (int slot = 0; slot < returns.size(); slot++) returns.setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 64L));
        } finally {
            returns.endBatch();
        }
    }

    private static FactoryRuntime factoryRuntime(MachineControllerBlockEntity controller) {
        return ((MachineControllerRuntime) fieldValue(controller, MachineControllerBlockEntity.class, "runtime")).factoryRuntime();
    }

    private static Object fieldValue(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot observe native ownership fixture", exception);
        }
    }

    private static Runnable observeOnce(Object owner, Class<?> type, String name, Runnable action) {
        AtomicReference<Runnable> pending = new AtomicReference<>(action);
        return observeChanges(owner, type, name, () -> {
            Runnable callback = pending.getAndSet(null);
            if (callback != null) callback.run();
        });
    }

    private static Runnable observeChanges(Object owner, Class<?> type, String name, Runnable action) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            Runnable original = (Runnable) field.get(owner);
            field.set(owner, (Runnable) () -> {
                if (original != null) original.run();
                action.run();
            });
            return () -> {
                try { field.set(owner, original); }
                catch (IllegalAccessException exception) { throw new AssertionError(exception); }
            };
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot observe real native listener", exception);
        }
    }

    private static long dropAmount(List<ItemEntity> drops, AEKey key) {
        long amount = 0L;
        for (ItemEntity entity : drops) {
            ItemStack item = entity.getItem();
            GenericStack wrapped = GenericStack.unwrapItemStack(item);
            if (wrapped != null) {
                if (wrapped.what().equals(key)) amount += wrapped.amount() * item.getCount();
            } else if (key.equals(AEItemKey.of(item))) amount += item.getCount();
        }
        return amount;
    }

    private static double radiationAt(GameTestHelper helper, BlockPos position) {
        GlobalPos target = GlobalPos.of(helper.getLevel().dimension(), position);
        return IRadiationManager.INSTANCE.getRadiationSources().values().stream()
                .filter(source -> source.getPos().equals(target)).mapToDouble(source -> source.getMagnitude()).sum();
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

    private long queuedAmount(PatternInterfaceBlockEntity host, AEKey key) {
        long amount = 0L;
        for (var group : host.pendingPatterns().groups()) {
            for (KeyCounter holder : group.inputs(group.copies())) amount += holder.get(key);
        }
        return amount;
    }

    private KeyCounter[] holders(IPatternDetails pattern) {
        KeyCounter[] holders = new KeyCounter[pattern.getInputs().length];
        for (int index = 0; index < holders.length; index++) {
            var input = pattern.getInputs()[index];
            GenericStack stack = input.getPossibleInputs()[0];
            holders[index] = new KeyCounter();
            holders[index].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
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

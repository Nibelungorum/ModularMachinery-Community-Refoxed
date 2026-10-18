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
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongSlotStorage;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native chemical CPU planning, synchronous execution and exact material ownership.
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekPatternGameTest {
    /** Optional keys for the common three-host real loot/placement lifecycle matrix. */
    public static List<GenericStack> carrierChemicals() {
        return List.of(new GenericStack(chemical("oxygen"), 7_000_000_000L),
                new GenericStack(chemical("hydrogen"), 8_000_000_000L),
                new GenericStack(chemical("chlorine"), 9_000_000_000L));
    }
    public void mixedPatternReturnsProductsAndExcessInputs(GameTestHelper helper) {
        nativeCpu(helper, "appmek_mixed_cpu", "ae2_me_pattern_interface", 0, true, false, false);
    }

    public void extendedPatternLastSlotProcessesChemicals(GameTestHelper helper) {
        nativeCpu(helper, "appmek_extended_cpu", "eae_me_extended_pattern_interface", 35, true, false, false);
    }

    public void chemicalCpuPlanSettles(GameTestHelper helper) {
        nativeCpu(helper, "appmek_pure_cpu", "ae2_me_pattern_interface", 0, false, false, false);
    }

    public void chemicalCpuCancellationPreservesRefusedInputs(GameTestHelper helper) {
        nativeCpu(helper, "appmek_cancel_cpu", "ae2_me_pattern_interface", 0, false, true, false);
    }

    public void mixedCpuCancellationPreservesRefusedInputs(GameTestHelper helper) {
        nativeCpu(helper, "appmek_mixed_cancel_cpu", "ae2_me_pattern_interface", 0, true, true, false);
    }

    public void chemicalCpuPreservesLongAmounts(GameTestHelper helper) {
        nativeCpu(helper, "appmek_long_cpu", "ae2_me_pattern_interface", 0, false, false, true);
    }

    private void nativeCpu(GameTestHelper helper, String name, String port, int slot,
                           boolean mixed, boolean cancel, boolean large) {
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        long crafts = large ? Integer.MAX_VALUE : 19L;
        long unit = large ? 2L : 500L;
        List<GenericStack> inputs = new ArrayList<>(List.of(new GenericStack(oxygen, unit)));
        List<GenericStack> products = new ArrayList<>(List.of(new GenericStack(hydrogen, unit)));
        List<MachineRequirement> requirements = new ArrayList<>(List.of(
                LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), unit)),
                LoadedChemicalRequirement.output(hydrogen.getId(), unit, 1F)));
        List<MachineOutput> outputs = new ArrayList<>(List.of(new LoadedChemicalOutput(hydrogen.getId(), unit, 1F)));
        if (mixed) {
            inputs.add(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
            inputs.add(new GenericStack(AEFluidKey.of(Fluids.WATER), 250L));
            products.add(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
            products.add(new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L));
            requirements.add(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)));
            requirements.add(MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)));
            requirements.add(MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT)));
            requirements.add(MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, 250)));
            outputs.add(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F));
            outputs.add(new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 250), 1F));
        }
        var fixture = new CompatPatternCpuFixture(helper, name, port, large ? ParallelTier.ULTIMATE : ParallelTier.PLUS,
                requirements, outputs);
        for (int index = 0; index < inputs.size(); index++) {
            GenericStack input = inputs.get(index);
            fixture.storage.setStack(index, new GenericStack(input.what(), Math.multiplyExact(input.amount(), crafts)));
        }
        fixture.host.getLogic().getPatternInv().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(inputs, products));
        fixture.ready().thenWaitUntil(() -> helper.assertTrue(fixture.grid().getCraftingService().isCraftable(hydrogen),
                        "Grid discovers the real chemical processing pattern"))
                .thenExecute(() -> fixture.begin(hydrogen, Math.multiplyExact(unit, crafts)))
                .thenWaitUntil(fixture::awaitPlan).thenExecute(() -> {
                    for (GenericStack input : inputs) helper.assertTrue(fixture.plan.usedItems().get(input.what()) == input.amount() * crafts,
                            "Calculated native plan retains exact item/fluid/chemical input identity and long totals");
                    long full = large ? crafts : 16L;
                    helper.assertTrue(fixture.plan.patternTimes().entrySet().stream().anyMatch(entry ->
                                    entry.getKey() instanceof PatternBatchDetails batch && batch.data().multiplier() == full),
                            "Actual native plan contains a locally bounded chemical batch, not a bare queue task");
                    fixture.submit();
                    // All return slots and network insertion of this blocker are genuinely unavailable.
                    AEItemKey blocker = AEItemKey.of(Items.IRON_BLOCK);
                    fixture.storage.blocked.add(blocker);
                    fillReturns(fixture.host.getLogic().getReturnInv());
                    helper.assertTrue(fixture.dispatch() == 0 && fixture.provider.accepted == 0,
                            "Complete return-capacity refusal accepts no standard CPU push");
                    for (GenericStack input : inputs) helper.assertTrue(fixture.cpu().getStored(input.what()) == input.amount() * crafts,
                            "Native CPU retains every resource after synchronous refusal");
                    for (GenericStack product : products) helper.assertTrue(fixture.cpu().getWaitingFor(product.what()) == 0L
                                    && fixture.cpu().getPendingOutputs(product.what()) == product.amount() * crafts,
                            "Refusal preserves pending native work and adds no waiting outputs");
                    helper.assertTrue(fixture.host.pendingPatterns().isEmpty()
                                    && fixture.controller.runtimeSnapshot().crafting().recipeId() == null,
                            "Refusal leaves no private queue and starts no machine lane");
                    fixture.host.getLogic().getReturnInv().clear();
                    if (cancel) {
                        fixture.cpu().cancel();
                    } else {
                        helper.assertTrue(fixture.dispatch() == 1, "One native standard push starts the exact chemical batch");
                        long started = fixture.cpu().getWaitingFor(hydrogen) / unit;
                        helper.assertTrue(started == full || !large && started == 3L, "Native task iteration may select either full batch or tail first");
                        fixture.assertActive(started);
                        for (GenericStack product : products) helper.assertTrue(fixture.cpu().getWaitingFor(product.what()) == product.amount() * started,
                                "Native CPU records the real scaled item/fluid/chemical promise");
                    }
                }).thenWaitUntil(() -> {
                    if (!cancel && fixture.cpu().hasJob()) fixture.dispatch();
                    helper.assertTrue(!fixture.cpu().hasJob(), "Native CPU settles or cancels the actual job");
                    for (GenericStack input : inputs) helper.assertTrue(fixture.stored(input.what()) == (cancel ? input.amount() * crafts : 0L)
                                    && fixture.cpu().getStored(input.what()) == 0L,
                            "Only native cancellation returns refused material; settled work consumes it exactly once");
                    for (GenericStack product : products) helper.assertTrue(fixture.stored(product.what()) == (cancel ? 0L : product.amount() * crafts)
                                    && fixture.cpu().getWaitingFor(product.what()) == 0L,
                            "Actual machine production settles every exact resource promise through native CPU injection");
                    helper.assertTrue(fixture.provider.accepted == (cancel ? 0L : large ? 1L : 2L),
                            "No duplicate accepted pushes and no hidden queue dispatches");
                    if (!cancel && !large) helper.assertTrue(fixture.provider.multipliers.equals(Map.of(16L, 1L, 3L, 1L)),
                            "Chemical CPU executes the bounded batch and exact tail separately");
                    if (!cancel && large) helper.assertTrue(fixture.provider.multipliers.equals(Map.of((long) Integer.MAX_VALUE, 1L)),
                            "One native CPU push pays and settles a chemical quantity above int without narrowing");
                }).thenSucceed();
    }

    public void radioactivePatternReturnsProductsThroughWasteBarrelStorageBus(GameTestHelper helper) {
        var fixture = chemicalFixture(helper, "appmek_radioactive_pattern", "nuclear_waste", "polonium");
        MekanismKey waste = chemical("nuclear_waste");
        MekanismKey polonium = chemical("polonium");
        fixture.storage.blocked.add(waste);
        fixture.storage.blocked.add(polonium);
        var network = AppMekGameTestFixtures.createWasteBarrelNetwork(helper, CompatPatternCpuFixture.PORT);
        fixture.ready().thenExecute(() -> {
            IPatternDetails pattern = installChemicalPattern(fixture.host, waste, polonium);
            KeyCounter[] request = holders(pattern);
            helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                    "Radioactive input synchronously starts real MMCR work");
            fixture.assertActive(1L);
            for (KeyCounter holder : request) helper.assertTrue(holder.isEmpty(), "Accepted radioactive request hands off once");
        }).thenWaitUntil(() -> helper.assertTrue(amount(fixture.host.getLogic().getReturnInv(), polonium) == 600L
                        && amount(fixture.host.getLogic().getReturnInv(), waste) == 500L,
                "Real recipe produces radioactive output and preserves excess input"))
                .thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenWaitUntil(() -> helper.assertTrue(network.barrel().getChemicalTank().getStored() == 601L
                                && amount(fixture.host.getLogic().getReturnInv(), polonium) == 0L,
                        "Real storage bus injects radioactive output into its waste barrel"))
                .thenExecute(() -> helper.assertTrue(amount(fixture.host.getLogic().getReturnInv(), waste) == 500L,
                        "The output barrel never accepts the different input chemical")).thenSucceed();
    }

    public void rejectedPatternPreservesInputHolders(GameTestHelper helper) {
        var fixture = chemicalFixture(helper, "appmek_rejected_pattern", "oxygen", "hydrogen");
        MekanismKey oxygen = chemical("oxygen");
        fixture.ready().thenExecute(() -> {
            IPatternDetails pattern = installChemicalPattern(fixture.host, oxygen, chemical("hydrogen"));
            KeyCounter[] malformed = holders(pattern);
            malformed[0].add(oxygen, 1L);
            helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, malformed, Direction.NORTH),
                    "Nonstandard per-copy quantity is refused before ownership transfer");
            helper.assertTrue(malformed[0].get(oxygen) == 1_001L, "Malformed caller retains every chemical unit");
            fillReturns(fixture.host.getLogic().getReturnInv());
            KeyCounter[] request = holders(pattern);
            helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                    "Full return capacity synchronously refuses rather than privately queueing");
            helper.assertTrue(request[0].get(oxygen) == 1_000L && fixture.host.pendingPatterns().isEmpty(),
                    "Refused request remains wholly caller-owned");
            fixture.host.getLogic().getReturnInv().clear();
            helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                    "Only an explicit new standard push starts work once capacity returns");
            fixture.assertActive(1L);
        }).thenSucceed();
    }

    public void batchPatternAccountsForChemicalInputsAcrossLanes(GameTestHelper helper) {
        // Retains the existing registry entry, now testing one exact native batch and tail, never cross-lane pooling.
        chemicalCpuPlanSettles(helper);
    }

    public void nativeBatchNotificationBoundary(GameTestHelper helper, boolean throwing) {
        nativePaymentCheckpoint(helper, throwing, false, false, false);
    }

    public void nativeBatchPendingCheckpoint(GameTestHelper helper, boolean drop) {
        nativePaymentCheckpoint(helper, false, drop, false, true);
    }

    public void nativeBatchReturnNotificationCheckpoint(GameTestHelper helper) {
        nativePaymentCheckpoint(helper, true, false, true, false);
    }

    public void nativeBatchSourceDestructionDuringPayment(GameTestHelper helper) {
        nativePaymentCheckpoint(helper, false, true, false, false, true, false);
    }

    public void nativeBatchRuntimeInvalidationDuringPayment(GameTestHelper helper) {
        nativePaymentCheckpoint(helper, false, false, false, false, false, true);
    }

    private void nativePaymentCheckpoint(GameTestHelper helper, boolean throwing, boolean drop,
                                         boolean returnNotification, boolean blockReturns) {
        nativePaymentCheckpoint(helper, throwing, drop, returnNotification, blockReturns, false, false);
    }

    private void nativePaymentCheckpoint(GameTestHelper helper, boolean throwing, boolean drop,
                                         boolean returnNotification, boolean blockReturns, boolean destroy, boolean invalidate) {
        String name = "appmek_sync_checkpoint_" + throwing + "_" + drop + "_" + returnNotification + "_" + blockReturns + "_" + destroy + "_" + invalidate;
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        BlockPos ordinaryPos = new BlockPos(2, 0, 0);
        var fixture = new CompatPatternCpuFixture(helper, name, "ae2_me_pattern_interface", ParallelTier.PLUS,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 500L)),
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                        MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)),
                        LoadedChemicalRequirement.output(hydrogen.getId(), 600L, 1F),
                        MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT)),
                        MachineRequirement.fluidOutput(new FluidStack(Fluids.LAVA, 250))),
                List.of(new LoadedChemicalOutput(hydrogen.getId(), 600L, 1F),
                        new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 250), 1F)), true);
        ItemBusBlockEntity ordinary = helper.getBlockEntity(ordinaryPos);
        ordinary.nativeItemHandler().setContents(0, new ItemStack(Items.COAL), 1L);
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<CompoundTag> checkpoint = new AtomicReference<>();
        fixture.ready().thenExecute(() -> {
            fixture.host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(oxygen, 1_000L), new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2L),
                            new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L)),
                    List.of(new GenericStack(hydrogen, 600L), new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                            new GenericStack(AEFluidKey.of(Fluids.LAVA), 250L))));
            IPatternDetails pattern = fixture.host.getLogic().getAvailablePatterns().getFirst();
            KeyCounter[] request = holders(pattern);
            List<ItemEntity> carriers = new ArrayList<>();
            AtomicReference<GameTestAssertException> callbackFailure = new AtomicReference<>();
            Consumer<EntityJoinLevelEvent> capture = event -> {
                if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                        && item.getItem().is(fixture.host.getBlockState().getBlock().asItem())) carriers.add(item);
            };
            Object owner = returnNotification ? fixture.host.getLogic().getReturnInv()
                    : fieldValue(ordinary.nativeItemHandler(), LongItemStorage.class, "storage");
            Runnable restore = observeOnce(owner, returnNotification ? GenericStackInv.class : LongSlotStorage.class,
                    returnNotification ? "listener" : "onChange", () -> {
                try {
                    notifications.incrementAndGet();
                    checkpoint.set(fixture.host.saveWithoutMetadata(helper.getLevel().registryAccess()));
                    if (destroy || invalidate) {
                        var atPayment = pendingInventory(helper, checkpoint.get());
                        helper.assertTrue(ordinary.nativeItemHandler().amount(0) == 0L
                                        && amount(atPayment, oxygen) == 500L
                                        && amount(atPayment, AEItemKey.of(Items.IRON_INGOT)) == 2L
                                        && amount(atPayment, AEFluidKey.of(Fluids.WATER)) == 1_000L,
                                "Teardown runs after real chemical and ordinary payment, before remaining item/fluid debits");
                    }
                    KeyCounter[] reentrant = holders(pattern);
                    helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, reentrant, Direction.NORTH),
                            "Native payment notification cannot reenter the active ownership handoff");
                    helper.assertTrue(holderAmount(reentrant, oxygen) == 1_000L
                                    && holderAmount(reentrant, AEItemKey.of(Items.IRON_INGOT)) == 2L
                                    && holderAmount(reentrant, AEFluidKey.of(Fluids.WATER)) == 1_000L,
                            "Rejected reentrant caller retains all item/fluid/chemical material");
                    if (blockReturns) fillReturns(fixture.host.getLogic().getReturnInv());
                    if (destroy) helper.getLevel().destroyBlock(fixture.host.getBlockPos(), true);
                    if (invalidate) {
                        ((MachineRecipeThread) fieldValue(fixture.controller, fixture.controller.getClass(), "normalRecipeThread"))
                                .invalidate();
                    }
                    if (throwing) throw new IllegalStateException("intentional native payment listener fault");
                } catch (GameTestAssertException failure) {
                    callbackFailure.set(failure);
                    throw failure;
                }
            });
            NeoForge.EVENT_BUS.addListener(capture);
            try {
                helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                        "Once native payment happened, ownership is accepted even if its notification throws");
            } finally {
                restore.run();
                NeoForge.EVENT_BUS.unregister(capture);
            }
            if (callbackFailure.get() != null) throw callbackFailure.get();
            helper.assertTrue(notifications.get() == 1 && checkpoint.get() != null,
                    "Evidence comes from an actual native payment/return notification");
            for (KeyCounter holder : request) helper.assertTrue(holder.isEmpty(), "Outer caller relinquishes material exactly once");
            CompoundTag saved = fixture.host.saveWithoutMetadata(helper.getLevel().registryAccess());
            GenericStackInv pending = pendingInventory(helper, saved);
            GenericStackInv atNotification = pendingInventory(helper, checkpoint.get());
            boolean interrupted = throwing && !returnNotification || destroy || invalidate;
            for (GenericStack balance : List.of(new GenericStack(oxygen, 500L),
                    new GenericStack(AEItemKey.of(Items.IRON_INGOT), interrupted ? 2L : 1L),
                    new GenericStack(AEFluidKey.of(Fluids.WATER), interrupted ? 1_000L : 750L))) {
                long expected = balance.amount();
                if (interrupted) helper.assertTrue(amount(atNotification, balance.what()) == expected,
                        "The native payment checkpoint records the known exact consumed prefix, not an arbitrary balance");
                helper.assertTrue(amount(pending, balance.what()) + amount(fixture.host.getLogic().getReturnInv(), balance.what()) == expected,
                        "One shared scratch owner conserves the actual unconsumed item/fluid/chemical balance at native notification");
            }
            helper.assertTrue(fixture.host.pendingPatterns().isEmpty(), "New paid work never becomes a legacy execution queue");
            KeyCounter retained = pending.getAvailableStacks();
            retained.addAll(fixture.host.getLogic().getReturnInv().getAvailableStacks());
            if (destroy || invalidate) {
                helper.assertTrue(ordinary.nativeItemHandler().amount(0) == 0L,
                        "The native ordinary handler was really paid before teardown, never rolled back");
                var state = fixture.controller.saveCustomOnly(helper.getLevel().registryAccess()).getCompound("crafting_runtime");
                helper.assertTrue(invalidate ? !state.getBoolean("active")
                                : state.getCompound("execution_commit").getString("phase").equals("ERROR_PAUSED")
                                || state.getCompound("recipe").getCompound("data").getBoolean("native_start_uncertain"),
                        "A lost runtime stays invalid, while a partially paid surviving runtime is quarantined");
            }
            if (drop) {
                if (!destroy) {
                    NeoForge.EVENT_BUS.addListener(capture);
                    try { helper.getLevel().destroyBlock(fixture.host.getBlockPos(), true); }
                    finally { NeoForge.EVENT_BUS.unregister(capture); }
                }
                helper.assertTrue(carriers.size() == 1 && fixture.host.isRemoved(),
                        "Real source destruction produces one carrier after native payment has begun");
                var position = new BlockPos(3, 1, 0);
                helper.setBlock(position, fixture.host.getBlockState());
                PatternInterfaceBlockEntity fresh = helper.getBlockEntity(position);
                var carrier = carriers.getFirst().getItem().copy();
                carriers.forEach(ItemEntity::discard);
                helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, fresh.getBlockPos(), carrier),
                        "The actual dropped carrier is placed on a genuinely new host");
                fresh.applyComponentsFromItemStack(carrier);
                if (blockReturns) fresh.getLogic().getReturnInv().clear();
                var carrierSave = fresh.saveWithoutMetadata(helper.getLevel().registryAccess());
                fresh.loadWithComponents(carrierSave.copy(), helper.getLevel().registryAccess());
                fresh.loadWithComponents(carrierSave.copy(), helper.getLevel().registryAccess());
                fresh.craftingMachine().flushPendingReturns();
                fresh.craftingMachine().flushPendingReturns();
                var after = pendingInventory(helper, fresh.saveWithoutMetadata(helper.getLevel().registryAccess()));
                for (AEKey key : List.of(oxygen, AEItemKey.of(Items.IRON_INGOT), AEFluidKey.of(Fluids.WATER))) {
                    helper.assertTrue(amount(after, key) + amount(fresh.getLogic().getReturnInv(), key) == retained.get(key),
                            "Placed carrier and repeated restore/refund preserve every leftover identity exactly once");
                }
                var ghost = holders(pattern);
                helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, ghost, Direction.NORTH),
                        "Destroyed source cannot ghost-spend a new CPU request");
                fixture.host.craftingMachine().flushPendingReturns();
                helper.assertTrue(holderAmount(ghost, oxygen) == 1_000L
                                && holderAmount(ghost, AEItemKey.of(Items.IRON_INGOT)) == 2L
                                && holderAmount(ghost, AEFluidKey.of(Fluids.WATER)) == 1_000L
                                && ordinary.nativeItemHandler().amount(0) == 0L,
                        "Old-source retries neither consume new holders nor replay ordinary payment");
            } else {
                fixture.host.onChunkUnloaded();
                helper.getLevel().removeBlockEntity(fixture.host.getBlockPos());
                var fresh = new PatternInterfaceBlockEntity(fixture.host.getBlockPos(), fixture.host.getBlockState(), fixture.host.kind());
                helper.getLevel().setBlockEntity(fresh);
                fresh.loadWithComponents(saved, helper.getLevel().registryAccess());
                fresh.loadWithComponents(saved, helper.getLevel().registryAccess());
                helper.assertTrue(ordinary.nativeItemHandler().amount(0) == 0L,
                        "Repeated balance restoration never repays physical ordinary input");
                if (blockReturns) fresh.getLogic().getReturnInv().clear();
                fresh.serverTick();
                fresh.serverTick();
                GenericStackInv after = pendingInventory(helper, fresh.saveWithoutMetadata(helper.getLevel().registryAccess()));
                for (AEKey key : List.of(oxygen, AEItemKey.of(Items.IRON_INGOT), AEFluidKey.of(Fluids.WATER))) {
                    helper.assertTrue(amount(after, key) + amount(fresh.getLogic().getReturnInv(), key) == retained.get(key),
                            "A genuinely fresh host and repeated restore/ticks settle each mixed balance once");
                }
            }
        }).thenSucceed();
    }

    public void chemicalPatternOutputMatchingPreservesLongAmounts(GameTestHelper helper) {
        var fixture = chemicalFixture(helper, "appmek_long_pattern", "oxygen", "hydrogen");
        long amount = (long) Integer.MAX_VALUE + 5_000L;
        MekanismKey hydrogen = chemical("hydrogen");
        MachineOutput output = AppMekBridge.get().patternOutput(hydrogen, amount).orElseThrow();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("appmek_long_match_recipe"), MMCR.id("appmek_long_pattern"), 1,
                List.of(LoadedChemicalRequirement.output(hydrogen.getId(), amount, 1F)), List.of(output), List.of(),
                0, 1, false, false, false, Set.of());
        helper.assertTrue(fixture.controller.patternOutputsMatch(recipe, fixture.controller.runtimeSnapshot(), List.of(output)),
                "Exact chemical conversion/matching retains long quantities");
        helper.assertFalse(fixture.controller.patternOutputsMatch(recipe, fixture.controller.runtimeSnapshot(),
                List.of(new LoadedChemicalOutput(hydrogen.getId(), amount - 1L, 1F))), "A different exact quantity cannot bind");
        helper.assertFalse(fixture.controller.patternOutputsMatch(recipe, fixture.controller.runtimeSnapshot(),
                List.of(new LoadedChemicalOutput(chemical("oxygen").getId(), amount, 1F))), "A different exact chemical ID cannot bind");
        helper.succeed();
    }

    public void differentChemicalOutputsCannotShareBinding(GameTestHelper helper) {
        var fixture = chemicalFixture(helper, "appmek_output_identity", "oxygen", "hydrogen");
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        MekanismKey chlorine = chemical("chlorine");
        fixture.ready().thenExecute(() -> {
            installChemicalPattern(fixture.host, oxygen, hydrogen);
            fixture.host.getLogic().getPatternInv().setItemDirect(1, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(oxygen, 1_000L)), List.of(new GenericStack(chlorine, 600L))));
            var patterns = fixture.host.getLogic().getAvailablePatterns();
            IPatternDetails valid = patterns.stream().filter(pattern -> pattern.getOutputs().getFirst().what().equals(hydrogen)).findFirst().orElseThrow();
            IPatternDetails different = patterns.stream().filter(pattern -> pattern.getOutputs().getFirst().what().equals(chlorine)).findFirst().orElseThrow();
            helper.assertTrue(valid instanceof PatternBatchDetails && !(different instanceof PatternBatchDetails),
                    "Only the exact hydrogen identity binds to the actual hydrogen recipe");
            KeyCounter[] request = holders(different);
            helper.assertFalse(fixture.host.craftingMachine().pushPattern(different, request, Direction.NORTH),
                    "A chlorine promise cannot execute a hydrogen-producing recipe");
            helper.assertTrue(request[0].get(oxygen) == 1_000L && fixture.host.pendingPatterns().isEmpty(),
                    "Wrong-output refusal preserves all caller-owned chemical input");
        }).thenSucceed();
    }

    public void taggedChemicalRemainsLegalWithoutCompression(GameTestHelper helper) {
        conservativeChemical(helper, 0);
    }

    public void nonConsumedChemicalRemainsLegalWithoutCompression(GameTestHelper helper) {
        conservativeChemical(helper, 1);
    }

    public void probabilisticChemicalRemainsLegalWithoutCompression(GameTestHelper helper) {
        conservativeChemical(helper, 2);
    }

    private void conservativeChemical(GameTestHelper helper, int mode) {
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        ChemicalIngredient ingredient = mode == 0
                ? ChemicalIngredient.tag(ResourceLocation.fromNamespaceAndPath("mekanism", "gaseous"), 500L)
                : ChemicalIngredient.chemical(oxygen.getId(), 500L);
        var requirement = new LoadedChemicalRequirement(IOType.INPUT, ingredient, 1F, List.of(), mode == 0 ? 1F : mode == 1 ? 0F : 0.5F);
        var fixture = new CompatPatternCpuFixture(helper, "appmek_conservative_" + mode, "ae2_me_pattern_interface", ParallelTier.PLUS,
                List.of(requirement, LoadedChemicalRequirement.output(hydrogen.getId(), 600L, 1F)),
                List.of(new LoadedChemicalOutput(hydrogen.getId(), 600L, 1F)),
                mode > 0 ? "chemical_input_hatch_basic" : null);
        ChemicalPortBlockEntity catalyst = mode > 0 ? helper.getBlockEntity(new BlockPos(2, 0, 0)) : null;
        fixture.storage.blocked.add(oxygen);
        fixture.storage.blocked.add(hydrogen);
        var sequence = fixture.ready().thenExecute(() -> {
            IPatternDetails pattern = installChemicalPattern(fixture.host, oxygen, hydrogen);
            var unchanged = PatternBatchPlanning.rewrite(Map.of(pattern, 19L), PatternBatchPlanning.capture(fixture.grid()));
            helper.assertTrue(unchanged.size() == 1 && unchanged.get(pattern) == 19L,
                    "Runtime-resolved chemical tags and non-consumed chemistry are not falsely compressed");
            KeyCounter[] request = holders(pattern);
            // Non-consumed requirements remain live conditions during processing, unlike paid inputs.
            // The request itself is returned immediately and must not masquerade as a retained catalyst.
            if (mode == 1) catalyst.chemicalTank().setStack(AppMekGameTestFixtures.chemical("oxygen", 500L));
            helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                    "Conservative planning still permits a legal normal single request; mode=" + mode
                            + ", consumeChance=" + requirement.consumeChance() + ", chemical=" + requirement.ingredient().id()
                            + ", pattern=" + pattern.getClass().getSimpleName()
                            + ", oxygenHeld=" + holderAmount(request, oxygen)
                            + ", oxygenReturned=" + amount(fixture.host.getLogic().getReturnInv(), oxygen)
                            + ", tick=" + fixture.controller.runtimeSnapshot().crafting().tick()
                            + ", failure=" + fixture.controller.runtimeSnapshot().crafting().failure());
            fixture.assertActive(1L);
            if (mode == 1) catalyst.chemicalTank().setEmpty();
            // Supply the continuing condition only after startup chose its real random outcome.
            if (mode == 2) catalyst.chemicalTank().setStack(AppMekGameTestFixtures.chemical("oxygen", 500L));
        });
        if (mode == 1) {
            sequence.thenWaitUntil(() -> helper.assertTrue(fixture.controller.runtimeSnapshot().crafting().failure() != null
                            && fixture.controller.runtimeSnapshot().crafting().tick() == 0
                            && amount(fixture.host.getLogic().getReturnInv(), oxygen) == 1_000L
                            && amount(fixture.host.getLogic().getReturnInv(), hydrogen) == 0L,
                    "Removing the genuine catalyst blocks processing without consuming or duplicating the returned request; state="
                            + fixture.controller.runtimeSnapshot().crafting()))
                    .thenExecute(() -> catalyst.chemicalTank().setStack(AppMekGameTestFixtures.chemical("oxygen", 500L)));
        }
        sequence.thenWaitUntil(() -> {
            long remaining = amount(fixture.host.getLogic().getReturnInv(), oxygen);
            helper.assertTrue(amount(fixture.host.getLogic().getReturnInv(), hydrogen) == 600L
                            && (mode == 0 ? remaining == 500L : mode == 1 ? remaining == 1_000L
                            : remaining == 500L || remaining == 1_000L),
                    "Real normal execution respects tag consumption and both legitimate probability outcomes; oxygen="
                            + remaining + ", hydrogen=" + amount(fixture.host.getLogic().getReturnInv(), hydrogen)
                            + ", state=" + fixture.controller.runtimeSnapshot().crafting());
            if (catalyst != null) helper.assertTrue(catalyst.chemicalTank().getStored() == 500L,
                    "The independent physical catalyst is still present after completion; none of its units were consumed");
        })
                .thenSucceed();
    }

    private CompatPatternCpuFixture chemicalFixture(GameTestHelper helper, String name, String input, String output) {
        return new CompatPatternCpuFixture(helper, name, "ae2_me_pattern_interface", ParallelTier.PLUS,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical(input).getId(), 500L)),
                        LoadedChemicalRequirement.output(chemical(output).getId(), 600L, 1F)),
                List.of(new LoadedChemicalOutput(chemical(output).getId(), 600L, 1F)));
    }

    private static MekanismKey chemical(String name) { return MekanismKey.of(AppMekGameTestFixtures.chemical(name, 1L)); }

    private static IPatternDetails installChemicalPattern(PatternInterfaceBlockEntity host, MekanismKey input, MekanismKey output) {
        host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, 1_000L)), List.of(new GenericStack(output, 600L))));
        return host.getLogic().getAvailablePatterns().getFirst();
    }

    private static KeyCounter[] holders(IPatternDetails pattern) {
        KeyCounter[] result = new KeyCounter[pattern.getInputs().length];
        for (int index = 0; index < result.length; index++) {
            var input = pattern.getInputs()[index];
            GenericStack stack = input.getPossibleInputs()[0];
            result[index] = new KeyCounter();
            result[index].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
        }
        return result;
    }

    private static long amount(GenericStackInv inventory, AEKey key) { return inventory.getAvailableStacks().get(key); }

    private static long holderAmount(KeyCounter[] holders, AEKey key) {
        long result = 0L;
        for (KeyCounter holder : holders) result += holder.get(key);
        return result;
    }

    private static GenericStackInv pendingInventory(GameTestHelper helper, CompoundTag saved) {
        return AE2NativeAdapters.restoreRequestInventory(saved.getList("pattern_pending_returns", Tag.TAG_COMPOUND),
                helper.getLevel().registryAccess());
    }

    private static void fillReturns(GenericStackInv inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) inventory.setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 64L));
    }

    private static Object fieldValue(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException exception) { throw new GameTestAssertException("Cannot access native checkpoint field: " + exception); }
    }

    private static Runnable observeOnce(Object owner, Class<?> type, String name, Runnable action) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            Runnable original = (Runnable) field.get(owner);
            AtomicReference<Runnable> pending = new AtomicReference<>(action);
            field.set(owner, (Runnable) () -> {
                if (original != null) original.run();
                Runnable callback = pending.getAndSet(null);
                if (callback != null) callback.run();
            });
            return () -> {
                try { field.set(owner, original); }
                catch (IllegalAccessException exception) { throw new GameTestAssertException("Cannot restore native checkpoint listener: " + exception); }
            };
        } catch (ReflectiveOperationException exception) { throw new GameTestAssertException("Cannot observe native checkpoint listener: " + exception); }
    }
}

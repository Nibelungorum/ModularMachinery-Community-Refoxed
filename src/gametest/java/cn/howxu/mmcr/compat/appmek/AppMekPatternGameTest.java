package cn.howxu.mmcr.compat.appmek;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongSlotStorage;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
import java.util.concurrent.atomic.AtomicReference;

/** Native chemical CPU plans and hosted material ownership.
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
        fixture.host.getLogic().getPatternInv().setItemDirect(slot, PatternDetailsHelper.encodeProcessingPattern(
                large ? inputs.stream().map(s -> new GenericStack(s.what(), s.amount() * crafts)).toList() : inputs,
                large ? products.stream().map(s -> new GenericStack(s.what(), s.amount() * crafts)).toList() : products));
        fixture.ready().thenWaitUntil(() -> helper.assertTrue(fixture.grid().getCraftingService().isCraftable(hydrogen),
                        "Grid discovers the real chemical processing pattern"))
                .thenExecute(() -> fixture.begin(hydrogen, Math.multiplyExact(unit, crafts)))
                .thenWaitUntil(fixture::awaitPlan).thenExecute(() -> {
                    for (GenericStack input : inputs) helper.assertTrue(fixture.plan.usedItems().get(input.what()) == input.amount() * crafts,
                            "Calculated native plan retains exact item/fluid/chemical input identity and long totals");
                    long tasks = large ? 1L : crafts;
                    helper.assertTrue(fixture.plan.patternTimes().size() == 1 && fixture.plan.patternTimes().containsValue(tasks)
                                    && fixture.plan.patternTimes().keySet().iterator().next() instanceof AEProcessingPattern,
                            "The native plan keeps the encoded quantity and pattern identity");
                    fixture.submit();
                    if (cancel) {
                        fixture.storage.blocked.add(AEItemKey.of(Items.IRON_BLOCK));
                        fillReturns(fixture.host.getLogic().getReturnInv());
                        helper.assertTrue(fixture.dispatch() == 1 && fixture.dispatch() == 0,
                                "Only the first delivery can enter escrow before output space is available");
                        for (GenericStack input : inputs) helper.assertTrue(fixture.cpu().getStored(input.what()) == input.amount() * (crafts - 1),
                                "The CPU retains every refused input");
                        fixture.cpu().cancel();
                        fixture.host.getLogic().getPatternInv().setItemDirect(slot, ItemStack.EMPTY);
                        fixture.host.getLogic().getReturnInv().clear();
                    } else fixture.cpu().setJobSuspended(false);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(!fixture.cpu().hasJob() && fixture.host.hostedPatterns().isEmpty(), "Job and escrow settle");
                    for (GenericStack input : inputs) helper.assertTrue(fixture.stored(input.what()) == (cancel ? input.amount() * crafts : 0L)
                                    && fixture.cpu().getStored(input.what()) == 0L,
                            "Cancellation and pattern retirement refund their respective owners exactly once");
                    for (GenericStack product : products) helper.assertTrue(fixture.stored(product.what()) == (cancel ? 0L : product.amount() * crafts)
                                    && fixture.cpu().getWaitingFor(product.what()) == 0L,
                            "The native CPU settles each real output");
                    helper.assertTrue(fixture.provider.accepted == (cancel || large ? 1L : crafts), "No duplicate native pushes");
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
            IPatternDetails pattern = installChemicalPattern(fixture.host, waste, polonium, 750);
            KeyCounter[] request = holders(pattern);
            helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, request, Direction.NORTH),
                    "Radioactive input synchronously starts real MMCR work");
            helper.assertTrue(!fixture.host.hostedPatterns().isEmpty(), "Inputs are held until the controller runs");
            for (KeyCounter holder : request) helper.assertTrue(holder.isEmpty(), "Accepted radioactive request hands off once");
        }).thenWaitUntil(() -> helper.assertTrue(amount(fixture.host.getLogic().getReturnInv(), polonium) == 600L
                        && fixture.host.hostedPatterns().groups().getFirst().materials().get(waste) == 250L,
                "Real recipe produces radioactive output and preserves excess input"))
                .thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenWaitUntil(() -> helper.assertTrue(network.barrel().getChemicalTank().getStored() == 601L
                                && amount(fixture.host.getLogic().getReturnInv(), polonium) == 0L,
                        "Real storage bus injects radioactive output into its waste barrel"))
                .thenExecute(() -> helper.assertTrue(fixture.host.hostedPatterns().groups().getFirst().materials().get(waste) == 250L,
                        "The output barrel never accepts the different input chemical")).thenSucceed();
    }

    public void rejectedPatternPreservesInputHolders(GameTestHelper helper) {
        var fixture = chemicalFixture(helper, "appmek_rejected_pattern", "oxygen", "hydrogen");
        MekanismKey oxygen = chemical("oxygen");
        fixture.ready().thenExecute(() -> {
            IPatternDetails pattern = installChemicalPattern(fixture.host, oxygen, chemical("hydrogen"));
            KeyCounter[] malformed = holders(pattern);
            malformed[0].add(chemical("chlorine"), 1L);
            helper.assertFalse(fixture.host.craftingMachine().pushPattern(pattern, malformed, Direction.NORTH),
                    "A wrong chemical key rejects the entire delivery");
            helper.assertTrue(malformed[0].get(oxygen) == 1_000L && malformed[0].get(chemical("chlorine")) == 1L,
                    "Rejection preserves every caller-owned unit");
            var accepted = holders(pattern);
            helper.assertTrue(fixture.host.craftingMachine().pushPattern(pattern, accepted, Direction.NORTH),
                    "A valid chemical delivery enters escrow");
            var next = holders(pattern);
            helper.assertTrue(!fixture.host.craftingMachine().pushPattern(pattern, next, Direction.NORTH)
                            && next[0].get(oxygen) == 1_000L, "First gate retains the next delivery at its sender");
        }).thenSucceed();
    }

    public void batchPatternAccountsForChemicalInputsAcrossLanes(GameTestHelper helper) {
        chemicalCpuPlanSettles(helper);
    }

    /** Saves from a real ordinary-handler callback between the chemical and item/fluid debits. */
    public void paymentCheckpoint(GameTestHelper helper, boolean throwing, boolean destroy, boolean invalidate) {
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var fixture = new CompatPatternCpuFixture(helper, "appmek_checkpoint_" + throwing + destroy + invalidate,
                "ae2_me_pattern_interface", ParallelTier.NORMAL,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 500)),
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                        MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)),
                        LoadedChemicalRequirement.output(hydrogen.getId(), 600, 1F)),
                List.of(new LoadedChemicalOutput(hydrogen.getId(), 600, 1F)), true);
        helper.<ParallelControllerBlockEntity>getBlockEntity(new BlockPos(0, 0, 1)).setCurrentParallelism(1);
        ItemBusBlockEntity ordinary = helper.getBlockEntity(new BlockPos(2, 0, 0));
        ordinary.nativeItemHandler().setContents(0, new ItemStack(Items.COAL), 1L);
        AtomicReference<CompoundTag> checkpoint = new AtomicReference<>();
        var prefix = Map.<AEKey, Long>of(oxygen, 500L, iron, 2L, water, 1_000L);
        fixture.ready().thenExecute(() -> {
            var host = fixture.host;
            host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(oxygen, 1_000), new GenericStack(iron, 2), new GenericStack(water, 1_000)),
                    List.of(new GenericStack(hydrogen, 600))));
            var pattern = host.getLogic().getAvailablePatterns().getFirst();
            var request = holders(pattern);
            helper.assertTrue(host.getLogic().pushPattern(pattern, request), "Native provider hands off all three inputs");
            Object storage = fieldValue(ordinary.nativeItemHandler(), LongItemStorage.class, "storage");
            AtomicReference<Boolean> reentered = new AtomicReference<>();
            var restore = observeOnce(storage, LongSlotStorage.class, "onChange", () -> {
                checkpoint.set(host.saveWithoutMetadata(helper.getLevel().registryAccess()));
                reentered.set(host.craftingMachine().pushPattern(pattern, holders(pattern), null));
                if (destroy) helper.getLevel().destroyBlock(host.getBlockPos(), true);
                if (invalidate) ((MachineRecipeThread) fieldValue(fixture.controller, fixture.controller.getClass(), "normalRecipeThread")).invalidate();
                if (throwing) throw new IllegalStateException("intentional native payment listener fault");
            });
            try { host.dispatchHostedPatterns(); } finally { restore.run(); }
            var saved = new HostedPatternInventory(() -> {});
            helper.assertTrue(checkpoint.get() != null && Boolean.FALSE.equals(reentered.get()), "No reentrant delivery during payment");
            saved.read(checkpoint.get(), helper.getLevel().registryAccess());
            helper.assertTrue(saved.group(pattern.getDefinition()).materials().equals(prefix)
                            && ordinary.nativeItemHandler().amount(0) == 0, "Checkpoint records only the already-paid prefix");
            if (destroy) {
                helper.assertTrue(host.hostedPatterns().isEmpty() && prefix.entrySet().stream().allMatch(e -> fixture.stored(e.getKey()) == e.getValue()),
                        "Dismantling refunds the unpaid prefix remainder once, never the physical coal");
            } else {
                var expected = throwing || invalidate ? prefix : Map.<AEKey, Long>of(oxygen, 500L, iron, 1L, water, 750L);
                saved.read(host.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                helper.assertTrue(saved.group(pattern.getDefinition()).materials().equals(expected),
                        "A failed native payment cannot replay or discard the remaining escrow");
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
        helper.assertTrue(fixture.controller.patternOutputsMatch(recipe, fixture.controller.runtimeSnapshot(),
                List.of(new LoadedChemicalOutput(hydrogen.getId(), amount - 1L, 1F))), "Quantity is not part of the chemical hint");
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
            helper.assertTrue(valid instanceof AEProcessingPattern && different instanceof AEProcessingPattern,
                    "Both hints retain native AE2 identity");
            KeyCounter[] request = holders(different);
            helper.assertFalse(fixture.host.craftingMachine().pushPattern(different, request, Direction.NORTH),
                    "A chlorine promise cannot execute a hydrogen-producing recipe");
            helper.assertTrue(request[0].get(oxygen) == 1_000L && fixture.host.hostedPatterns().isEmpty(),
                    "Wrong-output refusal preserves all caller-owned chemical input");
        }).thenSucceed();
    }

    public void taggedChemicalSupportsBatchDelivery(GameTestHelper helper) {
        chemicalBatchPolicy(helper, 0);
    }

    public void requestSuppliedNonConsumedChemicalReturnsAfterAdmission(GameTestHelper helper) {
        chemicalBatchPolicy(helper, 1);
    }

    public void probabilisticChemicalRemainsLegalWithoutCompression(GameTestHelper helper) {
        chemicalBatchPolicy(helper, 2);
    }

    public void requestInsufficientChemicalKeepsPhysicalCatalystCheck(GameTestHelper helper) {
        chemicalBatchPolicy(helper, 3);
    }

    private void chemicalBatchPolicy(GameTestHelper helper, int mode) {
        MekanismKey oxygen = chemical("oxygen");
        MekanismKey hydrogen = chemical("hydrogen");
        ChemicalIngredient ingredient = mode == 0
                ? ChemicalIngredient.tag(ResourceLocation.fromNamespaceAndPath("mekanism", "gaseous"), 500L)
                : ChemicalIngredient.chemical(oxygen.getId(), 500L);
        var requirement = new LoadedChemicalRequirement(IOType.INPUT, ingredient, 1F, List.of(), mode == 0 ? 1F : mode == 2 ? 0.5F : 0F);
        var requirements = new ArrayList<MachineRequirement>();
        requirements.add(requirement);
        if (mode != 0) requirements.add(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)));
        requirements.add(LoadedChemicalRequirement.output(hydrogen.getId(), 600L, 1F));
        var fixture = new CompatPatternCpuFixture(helper, "appmek_batch_policy_" + mode, "ae2_me_pattern_interface", ParallelTier.PLUS,
                requirements, List.of(new LoadedChemicalOutput(hydrogen.getId(), 600L, 1F)), mode > 0 ? "chemical_input_hatch_basic" : null);
        ChemicalPortBlockEntity catalyst = mode > 0 ? helper.getBlockEntity(new BlockPos(2, 0, 0)) : null;
        long copies = mode == 0 ? 16L : 1L;
        long requestAmount = mode == 3 ? 250L : mode == 0 ? 500L * copies : 1_000L;
        fixture.ready().thenExecute(() -> {
            var inputs = new ArrayList<>(List.of(new GenericStack(oxygen, requestAmount)));
            if (mode != 0) {
                inputs.add(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
                catalyst.chemicalTank().setStack(AppMekGameTestFixtures.chemical("oxygen", 500L));
            }
            fixture.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs,
                    List.of(new GenericStack(hydrogen, 600))));
            var pattern = fixture.host.getLogic().getAvailablePatterns().getFirst();
            helper.assertTrue(pattern instanceof AEProcessingPattern && fixture.host.getLogic().pushPattern(pattern, holders(pattern)),
                    "Tag, retained and probabilistic chemical inputs all use the native pattern");
        }).thenWaitUntil(() -> {
            helper.assertTrue(fixture.stored(hydrogen) == 600 * copies, "Only actual paid work returns hydrogen");
            var groups = fixture.host.hostedPatterns().groups();
            long remaining = groups.isEmpty() ? 0 : groups.getFirst().materials().getOrDefault(oxygen, 0L);
            helper.assertTrue(mode == 0 ? remaining == 0 : mode == 2 ? remaining == 500 || remaining == 1_000
                    : remaining == requestAmount, "Escrow retains precisely the material not consumed by the recipe");
            if (catalyst != null) helper.assertTrue(catalyst.chemicalTank().getStored() == 500, "Physical catalyst is not consumed");
        }).thenSucceed();
    }

    private CompatPatternCpuFixture chemicalFixture(GameTestHelper helper, String name, String input, String output) {
        return new CompatPatternCpuFixture(helper, name, "ae2_me_pattern_interface", ParallelTier.PLUS,
                List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(chemical(input).getId(), 500L)),
                        LoadedChemicalRequirement.output(chemical(output).getId(), 600L, 1F)),
                List.of(new LoadedChemicalOutput(chemical(output).getId(), 600L, 1F)));
    }

    private static MekanismKey chemical(String name) { return MekanismKey.of(AppMekGameTestFixtures.chemical(name, 1L)); }

    private static IPatternDetails installChemicalPattern(PatternInterfaceBlockEntity host, MekanismKey input, MekanismKey output) {
        return installChemicalPattern(host, input, output, 1_000L);
    }

    private static IPatternDetails installChemicalPattern(PatternInterfaceBlockEntity host, MekanismKey input, MekanismKey output,
                                                          long inputAmount) {
        host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, inputAmount)), List.of(new GenericStack(output, 600L))));
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

package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import net.minecraft.resources.Identifier;
import java.util.concurrent.atomic.AtomicInteger;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.Direction;
import net.minecraft.world.item.crafting.Ingredient;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** @author HowXu {@literal <dev@howxu.cn>} */
public final class HostedPatternNativeCpuGameTest {
    public static void nativeSmallPatterns(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "small", false, 2, 4, ParallelTier.PRO);
        f.calculate().thenExecute(() -> {
            helper.assertTrue(f.plan.patternTimes().size() == 1 && f.plan.patternTimes().containsValue(4L)
                    && f.plan.patternTimes().keySet().iterator().next().getClass() == AEProcessingPattern.class,
                    "The original plan calls the original processing pattern four times");
            f.submit();
            f.cpu().setJobSuspended(false);
        }).thenWaitUntil(() -> f.assertSettled(4)).thenSucceed();
    }

    public static void encodedDeliverySplitsAcrossLanes(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "scaled", false, 1, 2, ParallelTier.NORMAL);
        helper.getBlockEntity(new BlockPos(0, 0, 1), ParallelControllerBlockEntity.class).setCurrentParallelism(1);
        f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(f.input, 2)), List.of(new GenericStack(f.output, 2))));
        f.calculate().thenExecute(() -> {
            helper.assertTrue(f.plan.patternTimes().containsValue(1L), "One unchanged doubled pattern, two individual recipes");
            f.submit();
            f.cpu().setJobSuspended(false);
        }).thenWaitUntil(() -> f.assertSettled(1)).thenSucceed();
    }

    public static void firstDeliveryNeedsOrdinaryTopUp(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "first_gate", false, 2, 1, ParallelTier.NORMAL);
        var product = AEItemKey.of(Items.GOLD_NUGGET);
        var catalyst = AEItemKey.of(Items.REDSTONE);
        var id = MMCR.id("hosted_catalyst_with_alternative");
        if (!RecipeRegistry.containsStatic(id)) RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(id, f.machine.registryName(), 10,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.REDSTONE), 1, ItemStack.EMPTY).retainedInput(),
                        new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT, Items.COPPER_INGOT), 2, ItemStack.EMPTY),
                        MachineRequirement.itemOutput(new ItemStack(Items.GOLD_NUGGET, 2))), List.of(), List.of(),
                0, 2, false, true, false, Set.of()));
        var bus = helper.getBlockEntity(NativePatternCpuFixture.BUS, ItemInputBusBlockEntity.class);
        f.ready().thenExecute(() -> {
            f.host.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(f.input, 1), new GenericStack(catalyst, 1)), List.of(new GenericStack(product, 1))));
            var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
            KeyCounter first = counter(f, 1), next = counter(f, 1), mold = new KeyCounter();
            mold.add(catalyst, 1);
            helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{first, mold}), "First delivery is hosted");
            f.host.dispatchHostedPatterns();
            helper.assertTrue(!f.host.getLogic().pushPattern(pattern, new KeyCounter[]{next, new KeyCounter()}) && next.get(f.input) == 1
                    && f.host.hostedPatterns().group(pattern.getDefinition()).materials().get(f.input) == 1
                    && f.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                    "A catalyst and one iron cannot bootstrap a two-metal recipe, even with spare iron in ME");
            try (var tx = Transaction.openRoot()) {
                helper.assertTrue(bus.itemStorage().insert(0, ItemResource.of(Items.COPPER_INGOT), 1, tx) == 1, "Supply a legal alternative");
                tx.commit();
            }
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(product) == 2 && f.stored(f.input) == f.total
                        && bus.itemStorage().amount(0) == 0 && f.host.hostedPatterns().groups().getFirst().materials().equals(Map.of(catalyst, 1L)),
                "The real recipe pays hosted iron and ordinary copper, produces two nuggets and retains the catalyst"))
                .thenExecute(() -> {
                    var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
                    helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{counter(f, 1), new KeyCounter()}),
                            "A validated remainder need not bootstrap the first gate again");
                    f.host.getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
                }).thenWaitUntil(() -> helper.assertTrue(f.host.hostedPatterns().isEmpty() && f.stored(catalyst) == 1
                        && f.stored(f.input) == f.total + 1 && f.stored(AEItemKey.of(Items.COPPER_INGOT)) == 0
                        && f.stored(product) == 2, "Refund contains only retained catalyst and unpaid iron, never copper")).thenSucceed();
    }

    public static void retireAndRefund(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "refund", false, 1, 2, ParallelTier.NORMAL);
        f.ready().thenExecute(() -> {
            var slots = f.host.getLogic().getPatternInv();
            var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
            var key = pattern.getDefinition();
            slots.setItemDirect(1, slots.getStackInSlot(0).copy());
            helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{counter(f, 20)}), "Receive all twenty");
            slots.setItemDirect(0, ItemStack.EMPTY);
            helper.assertTrue(!f.host.hostedPatterns().canAccept(key), "First gate is closed before validation");
            helper.assertTrue(!f.host.getUpdateTag(helper.getLevel().registryAccess()).contains("hosted_patterns")
                    && !f.host.collectComponents().has(DataComponents.BLOCK_ENTITY_DATA), "No client or item copy of the inputs");
            f.storage.insertionLimit = f.total + 5;
            slots.setItemDirect(1, ItemStack.EMPTY);
            f.host.getLogic().updatePatterns();
            f.host.dispatchHostedPatterns();
            helper.assertTrue(f.stored(f.input) == f.total + 5 && f.host.hostedPatterns().group(key).materials().get(f.input) == 15,
                    "Refund debits only the five items accepted by ME");
            slots.setItemDirect(1, key.toStack());
            var retry = counter(f, 1);
            helper.assertTrue(!f.host.getLogic().pushPattern(pattern, new KeyCounter[]{retry}) && retry.get(f.input) == 1,
                    "Replacing a retired pattern cannot reclaim its unpaid refund");
            f.storage.insertionLimit = Long.MAX_VALUE;
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.input) == f.total + 20 && f.host.hostedPatterns().isEmpty()
                && f.stored(f.output) == 0, "All unpaid material eventually returns, without producing gold")).thenSucceed();
    }

    public static void twoControllersPayOneGroup(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "shared_40", false, 40, 1, ParallelTier.NORMAL);
        helper.getBlockEntity(new BlockPos(0, 0, 1), ParallelControllerBlockEntity.class).setCurrentParallelism(1);
        var pos = new BlockPos(2, 0, 2);
        var id = MMCR.id("hosted_shared_24");
        var machine = new DynamicMachine(id, "block.mmcr.test_cube", new BlockArray(Map.of(NativePatternCpuFixture.PORT.subtract(pos),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get()))));
        helper.setBlock(pos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        if (!RecipeRegistry.containsStatic(id)) RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(id, id, 10,
                List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 24)),
                        MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT)),
                        MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND))), List.of(), List.of(), 0, 1,
                false, false, false, Set.of()));
        var second = helper.getBlockEntity(pos, MachineControllerBlockEntity.class);
        second.setMachine(machine);
        second.setStructureCheckIntervalForTesting(1);
        f.ready().thenWaitUntil(() -> {
            helper.assertTrue(second.structureSnapshot().formed() && f.host.hostedControllers().size() == 2,
                    "Two independent controllers share the actual interface");
            var caps = AE2ResourceFamilies.ITEM.capabilityView(f.host,
                    f.host.itemRequestStorage(new KeyCounter[]{counter(f, 64)}), IOType.INPUT, false);
            var hints = List.<MachineOutput>of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F));
            for (var controller : List.of(f.controller, second)) {
                helper.assertTrue(controller.probeHostedPattern(f.host.getBlockPos(), hints, List.of(caps), Set.of()) != null,
                        "Both controllers have a current execution permit before checking concurrent probes");
            }
        })
                .thenExecute(() -> {
                    var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
                    helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{counter(f, 64)}), "Receive 64 iron once");
                    var group = f.host.hostedPatterns().group(pattern.getDefinition());
                    var caps = AE2ResourceFamilies.ITEM.capabilityView(f.host,
                            f.host.itemRequestStorage(group.requestCounters()), IOType.INPUT, false);
                    var hint = List.<MachineOutput>of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F));
                    for (var controller : List.of(f.controller, second)) {
                        helper.assertTrue(controller.probeHostedPattern(f.host.getBlockPos(), hint, List.of(caps), Set.of()) != null,
                                "Each controller can independently probe the same material");
                    }
                    helper.assertTrue(group == f.host.hostedPatterns().group(pattern.getDefinition())
                            && group.materials().get(f.input) == 64, "Probing reserves no material or owner");
                    f.host.dispatchHostedPatterns();
                    helper.assertTrue(f.host.hostedPatterns().isEmpty() && f.controller.runtimeSnapshot().factory().activeLaneCount() == 1
                            && id.equals(second.runtimeSnapshot().crafting().recipeId()), "One lane paid 40 and the other paid 24");
                }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == 41 && f.stored(AEItemKey.of(Items.DIAMOND)) == 1
                        && f.stored(f.input) == f.total, "Both actual recipes settle, including the unadvertised byproduct")).thenSucceed();
    }

    public static void cancelledStartWaitsForInputChange(GameTestHelper helper) {
        var f = new NativePatternCpuFixture(helper, "hook_source", false, 1, 2, ParallelTier.NORMAL);
        var id = MMCR.id("hosted_start_guard");
        var bus = helper.getBlockEntity(NativePatternCpuFixture.BUS, ItemInputBusBlockEntity.class);
        var calls = new AtomicInteger();
        var guarded = new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return f.machine.pattern(); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public boolean hasFactory() { return true; }
            @Override public int factoryThreadLimit() { return 2; }
            @Override public RecipeBehavior behavior() {
                return RecipeBehavior.builder().beforeStart(start -> {
                    calls.incrementAndGet();
                    if (bus.itemStorage().amount(0) == 0) start.cancel();
                }).build();
            }
        };
        MachineRegistry.register(guarded);
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(id, id, 8,
                List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                        MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT))), List.of(), List.of(),
                0, 1, false, false, false, Set.of()));
        f.controller.setMachine(guarded);
        f.ready().thenExecute(() -> {
            var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
            helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{counter(f, 1)}) && calls.get() == 0,
                    "Accepting the first delivery never calls beforeStart");
        }).thenWaitUntil(() -> helper.assertTrue(calls.get() == 1, "The controller makes a real start attempt"))
                .thenExecute(() -> {
                    var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
                    f.host.dispatchHostedPatterns();
                    var rejected = counter(f, 1);
                    helper.assertTrue(!f.host.getLogic().pushPattern(pattern, new KeyCounter[]{rejected})
                            && rejected.get(f.input) == 1 && calls.get() == 1
                            && f.host.hostedPatterns().group(pattern.getDefinition()).materials().get(f.input) == 1,
                            "Cancellation pays nothing, closes admission and cannot spin on an unchanged recipe");
                    try (var tx = Transaction.openRoot()) {
                        bus.itemStorage().insert(0, ItemResource.of(Items.REDSTONE), 1, tx);
                        tx.commit();
                    }
                }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == 1 && calls.get() == 2
                        && f.host.hostedPatterns().isEmpty(), "Changed ordinary input permits one fresh paid attempt")).thenSucceed();
    }

    public static void compressedDelivery(GameTestHelper helper, boolean fluid) {
        var f = new NativePatternCpuFixture(helper, fluid ? "bulk_fluid" : "bulk_item", fluid, 2, 1, ParallelTier.ULTIMATE);
        long actual = 2L * Integer.MAX_VALUE;
        f.ready().thenExecute(() -> {
            var pattern = f.host.getLogic().getAvailablePatterns().getFirst();
            KeyCounter raw = counter(f, actual);
            helper.assertTrue(f.host.getLogic().pushPattern(pattern, new KeyCounter[]{raw}) && raw.isEmpty(),
                    "A single external standard push transfers more than Integer.MAX_VALUE units");
        }).thenWaitUntil(() -> helper.assertTrue(f.stored(f.output) == actual && f.stored(f.input) == f.total
                        && f.host.hostedPatterns().isEmpty() && f.controller.runtimeSnapshot().factory().activeLaneCount() == 0,
                "Actual delivery must settle exactly once: output=" + f.stored(f.output) + ", hosted="
                        + f.host.hostedPatterns().groups() + ", factory=" + f.controller.runtimeSnapshot().factory())).thenSucceed();
    }

    private static KeyCounter counter(NativePatternCpuFixture fixture, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(fixture.input, amount);
        return counter;
    }
}

package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.appmek.AppMekPatternGameTest;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Pending work survives normal saves and native block-item restoration without stack expansion.
 * @author howxu <dev@howxu.cn>
 */
public final class PendingPatternLifecycleGameTest {
    public static void preparationRemovalCannotSpendCarriedInputs(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(1, 1, 0);
        BlockPos portPos = new BlockPos(1, 2, 0);
        BlockPos busPos = new BlockPos(1, 0, 0);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        var host = place(helper, portPos, "ae2_me_pattern_interface");
        helper.setBlock(busPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        ItemBusBlockEntity bus = helper.getBlockEntity(busPos);
        AtomicInteger hooks = new AtomicInteger();
        AtomicBoolean armed = new AtomicBoolean();
        var id = MMCR.id("pending_pattern_removed_during_prepare");
        var base = new DynamicMachine(id, "Pending Pattern Removal Test", new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(host.getBlockState().getBlock()),
                new BlockPos(0, -1, 0), new BlockPredicate.OfBlock(bus.getBlockState().getBlock()))));
        var machine = new DynamicMachine(id, base.displayNameKey(), base.pattern(), base.controller(), base.appearance(),
                base.portRequirements(), base.portTierRequirements(), base.dynamicPatterns(), base.modifierReplacements(),
                1, false, false, 1, base.factoryThreads(), base.role(), base.acceptedModuleIds(), base.structureStages(),
                base.failureAction(), RecipeBehavior.builder().beforeStart(context -> {
                    if (!armed.compareAndSet(true, false)) return;
                    hooks.incrementAndGet();
                    helper.getLevel().destroyBlock(host.getBlockPos(), true);
                }).build());
        if (!MachineRegistry.containsStatic(id)) MachineRegistry.register(machine);
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(id.withSuffix("_recipe"), id, 100,
                List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2)),
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1)),
                        MachineRequirement.itemOutput(new ItemStack(Items.GOLD_INGOT))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F)),
                List.of(), 0, 1, false, false, false, Set.of()));
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        bus.nativeItemHandler().insertItem(0, new ItemStack(Items.COAL), false);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                "Controller forms before the removal callback")).thenExecute(() -> {
            var output = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
            var definition = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L)), output);
            var inputs = new KeyCounter();
            inputs.add(AEItemKey.of(Items.IRON_INGOT), 5L);
            host.getLogic().getReturnInv().insert(AEItemKey.of(Items.IRON_INGOT), 11L,
                    Actionable.MODULATE, IActionSource.empty());
            List<ItemStack> carriers = new ArrayList<>();
            Consumer<EntityJoinLevelEvent> capture = event -> {
                if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                        && item.getItem().is(host.getBlockState().getBlock().asItem())) carriers.add(item.getItem().copy());
            };
            NeoForge.EVENT_BUS.addListener(capture);
            try {
                armed.set(true);
                helper.assertFalse(host.craftingMachine().pushPattern(new AEProcessingPattern(AEItemKey.of(definition)),
                        new KeyCounter[]{inputs}, null), "Removing the host during preparation rejects before payment");
            } finally {
                NeoForge.EVENT_BUS.unregister(capture);
            }
            helper.assertTrue(hooks.get() == 1 && host.isRemoved(), "The real beforeStart callback removes the source port");
            helper.assertTrue(bus.nativeItemHandler().getStackInSlot(0).getCount() == 1,
                    "Removing the source during preparation cannot pay ordinary inputs");
            helper.assertTrue(inputs.get(AEItemKey.of(Items.IRON_INGOT)) == 5L,
                    "Pre-payment rejection leaves the complete CPU input untouched");
            helper.assertTrue(carriers.size() == 1 && carriers.getFirst().has(DataComponents.BLOCK_ENTITY_DATA),
                    "The real dropped block carries only the previously owned return inventory");
            var restored = place(helper, new BlockPos(3, 2, 0), "ae2_me_pattern_interface");
            BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, restored.getBlockPos(), carriers.getFirst());
            helper.assertTrue(restored.pendingPatterns().isEmpty()
                            && restored.getLogic().getReturnInv().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT)) == 11L,
                    "The carrier never duplicates the rejected CPU input");
        }).thenSucceed();
    }

    public static void ordinaryCarrierRoundTrip(GameTestHelper helper) {
        carrierRoundTrip(helper, "ae2_me_pattern_interface");
    }

    public static void extendedCarrierRoundTrip(GameTestHelper helper) {
        carrierRoundTrip(helper, "eae_me_extended_pattern_interface");
    }

    public static void mirrorCarrierRoundTrip(GameTestHelper helper) {
        carrierRoundTrip(helper, "eaep_me_mirror_pattern_interface");
    }

    private static void carrierRoundTrip(GameTestHelper helper, String kind) {
        var host = place(helper, BlockPos.ZERO, kind);
        var chemicals = AppMekBridge.get().available() ? AppMekPatternGameTest.carrierChemicals() : List.<GenericStack>of();
        var queued = new KeyCounter();
        queued.add(AEItemKey.of(Items.IRON_INGOT), 5L);
        queued.add(AEFluidKey.of(Fluids.WATER), 3_000_000_000L);
        if (!chemicals.isEmpty()) queued.add(chemicals.get(0).what(), chemicals.get(0).amount());
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
        var template = new ArrayList<GenericStack>();
        for (var balance : queued) template.add(new GenericStack(balance.getKey(), balance.getLongValue()));
        var definition = AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(template, outputs));
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(),
                PendingPatternQueueGameTest.legacyData(helper, new KeyCounter[]{queued}, outputs, 1L, definition));
        var nativeReturns = new KeyCounter();
        nativeReturns.add(AEItemKey.of(Items.GOLD_INGOT), Long.MAX_VALUE);
        nativeReturns.add(AEFluidKey.of(Fluids.LAVA), 6_000_000_000L);
        if (!chemicals.isEmpty()) nativeReturns.add(chemicals.get(1).what(), chemicals.get(1).amount());
        for (var balance : nativeReturns) helper.assertTrue(host.getLogic().getReturnInv().insert(balance.getKey(), balance.getLongValue(),
                Actionable.MODULATE, IActionSource.empty()) == balance.getLongValue(), "Native return fixture owns every long unit");
        var pending = new KeyCounter();
        pending.add(AEItemKey.of(Items.DIAMOND), 5_000_000_000L);
        pending.add(AEFluidKey.of(Fluids.WATER), 4_000_000_000L);
        if (!chemicals.isEmpty()) pending.add(chemicals.get(2).what(), chemicals.get(2).amount());
        CompoundTag inFlight = new CompoundTag();
        AE2NativeAdapters.requestInventory(new KeyCounter[]{pending}).writeToChildTag(inFlight,
                "pattern_pending_returns", helper.getLevel().registryAccess());
        host.craftingMachine().loadPendingReturns(inFlight, helper.getLevel().registryAccess());
        List<ItemEntity> drops = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof ItemEntity item
                    && item.distanceToSqr(host.getBlockPos().getCenter()) < 4D) drops.add(item);
        };
        NeoForge.EVENT_BUS.addListener(capture);
        try { helper.assertTrue(helper.getLevel().destroyBlock(host.getBlockPos(), true), "Destroy the actual source block"); }
        finally { NeoForge.EVENT_BUS.unregister(capture); }
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModBlocks.BLOCKS.get(kind).get().asItem()),
                "Real loot carries mixed owners in one block item, without scattered duplicates");
        ItemStack carrier = drops.getFirst().getItem().copy();
        helper.assertTrue(carrier.has(DataComponents.BLOCK_ENTITY_DATA), "Real block loot preserves the mixed private owners");
        drops.forEach(ItemEntity::discard);
        host.dropContents();
        host.dropContents();
        helper.assertTrue(host.pendingPatterns().totalCopies() == 1L, "Native content drops never discharge the queue");
        var restored = place(helper, new BlockPos(2, 0, 0), kind);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, restored.getBlockPos(), carrier),
                "Native block item restoration loads the pending data");
        restored.applyComponentsFromItemStack(carrier);
        helper.assertTrue(restored.pendingPatterns().totalCopies() == 1L
                        && restored.pendingPatterns().groups().getFirst().inputs(1)[0].get(AEItemKey.of(Items.IRON_INGOT)) == 5L,
                "Replacing the carrier restores exactly the unprocessed inputs");
        var saved = restored.saveWithoutMetadata(helper.getLevel().registryAccess());
        restored.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        restored.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        var restoredQueue = new KeyCounter();
        for (var holder : restored.pendingPatterns().groups().getFirst().inputs(1)) restoredQueue.addAll(holder);
        for (var balance : queued) helper.assertTrue(restoredQueue.get(balance.getKey()) == balance.getLongValue(),
                "Repeated carrier load retains exact long queue identities, including optional chemicals");
        helper.assertTrue(restored.craftingMachine().hasPendingReturns(), "Carrier also restores in-flight balances");
        restored.craftingMachine().flushPendingReturns();
        restored.craftingMachine().flushPendingReturns();
        restored.refundLegacyInputs();
        restored.refundLegacyInputs();
        var expected = new KeyCounter();
        expected.addAll(nativeReturns);
        expected.addAll(pending);
        expected.addAll(queued);
        var actual = restored.getLogic().getReturnInv().getAvailableStacks();
        for (var balance : expected) helper.assertTrue(actual.get(balance.getKey()) == balance.getLongValue(),
                "Repeated restore/refund conserves each exact item/fluid/chemical identity without int truncation");
        helper.assertTrue(restored.pendingPatterns().isEmpty() && !restored.craftingMachine().hasPendingReturns(),
                "All three original owners settle once into native returns, never into new execution");
        helper.assertTrue(restored.getLogic().getPatternInv().isEmpty() && restored.linkedControllerPositions().isEmpty(),
                "Carrier does not copy native pattern slots or former links");
        helper.succeed();
    }

    public static void pendingSaveAndOldSave(GameTestHelper helper) {
        var host = place(helper, BlockPos.ZERO, "ae2_me_pattern_interface");
        offer(helper, host);
        helper.assertFalse(host.getUpdateTag(helper.getLevel().registryAccess()).contains("pending_patterns"),
                "Client block updates do not include the private material escrow");
        helper.assertTrue(host.getLogic().getReturnInv().isEmpty(), "Queued inputs are not native return contents");
        CompoundTag saved = new CompoundTag();
        host.saveAdditional(saved, helper.getLevel().registryAccess());
        var restored = place(helper, new BlockPos(2, 0, 0), "ae2_me_pattern_interface");
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.pendingPatterns().totalCopies() == 1L, "Ordinary block saves restore owned work");
        saved.remove("pending_patterns");
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.pendingPatterns().isEmpty(), "A legacy save without the queue starts empty");
        helper.succeed();
    }

    private static PatternInterfaceBlockEntity place(GameTestHelper helper, BlockPos position, String kind) {
        helper.setBlock(position, ModBlocks.BLOCKS.get(kind).get().defaultBlockState());
        return helper.getBlockEntity(position);
    }

    private static void offer(GameTestHelper helper, PatternInterfaceBlockEntity host) {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.IRON_INGOT), 5L);
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
        var definition = AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L)), outputs));
        PendingPatternQueueGameTest.read(helper, host.pendingPatterns(),
                PendingPatternQueueGameTest.legacyData(helper, new KeyCounter[]{inputs}, outputs, 1L, definition));
        helper.assertTrue(host.pendingPatterns().totalCopies() == 1L, "Read a complete legacy v1 input template");
    }
}

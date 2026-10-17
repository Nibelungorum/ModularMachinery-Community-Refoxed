package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.menu.SlotSemantics;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue.OfferLimits;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import java.util.function.Consumer;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the mixin-installed long return inventory against native AE2 storage and menus.
 * @author howxu <dev@howxu.cn>
 */
public class PatternReturnInventoryGameTest {
    public void pendingQueueLongBalancesAndPersistence(GameTestHelper helper) {
        var host = place(helper);
        var queue = host.pendingPatterns();
        AEItemKey iron = variant(helper, 700);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        KeyCounter items = new KeyCounter();
        items.add(iron, Long.MAX_VALUE / 2L);
        items.add(gold, Long.MAX_VALUE / 2L);
        KeyCounter[] inputs = {items};
        var definition = AEItemKey.of(Items.PAPER);
        var outputs = List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        var limits = new OfferLimits(3L, 3L);
        long now = helper.getLevel().getGameTime();
        for (int copy = 0; copy < 2; copy++) helper.assertTrue(
                queue.offer(definition, outputs, inputs, limits, now),
                "Each complete key independently fits its long budget");
        helper.assertTrue(!queue.offer(definition, outputs, inputs, limits, now),
                "Per-key multiplication overflow rejects a third copy before changing ownership");
        items.add(iron, 1L);
        helper.assertTrue(queue.totalCopies() == 2L && queue.groups().size() == 1
                        && queue.groups().getFirst().inputs(2L)[0].get(iron) == Long.MAX_VALUE - 1L
                        && queue.groups().getFirst().inputs(2L)[0].get(gold) == Long.MAX_VALUE - 1L
                        && host.getLogic().getReturnInv().isEmpty(),
                "Compressed private ownership is independent of caller mutation and cross-resource totals");
        CompoundTag saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(2, 0, 0)),
                host.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        var restoredInputs = restored.pendingPatterns().groups().getFirst().inputs(2L)[0];
        helper.assertTrue(restored.pendingPatterns().totalCopies() == 2L
                        && restoredInputs.get(iron) == Long.MAX_VALUE - 1L
                        && restoredInputs.get(gold) == Long.MAX_VALUE - 1L,
                "Host NBT retains complete component keys and exact compressed long balances");
        restored.onChunkUnloaded();
        helper.succeed();
    }

    public void nativeSlotsAndExactLongBalances(GameTestHelper helper) {
        var inventory = place(helper).getLogic().getReturnInv();
        helper.assertTrue(inventory instanceof PatternReturnInventory, "MMCR installs the real overflow inventory");
        var original = new PatternProviderReturnInventory(() -> {});
        for (AEKey key : List.of(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.ENDER_PEARL),
                AEItemKey.of(Items.DIAMOND_SWORD), AEFluidKey.of(Fluids.WATER))) {
            inventory.clear();
            helper.assertTrue(inventory.insert(0, key, Long.MAX_VALUE, Actionable.MODULATE) == original.getMaxAmount(key),
                    "Visible slots keep the native physical capacity");
            inventory.clear();
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == Long.MAX_VALUE
                            && inventory.isEmpty(), "Simulation accepts the exact long quantity without mutation");
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()) == Long.MAX_VALUE
                            && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                    "Visible and hidden slots store an exact complete-key balance");
            helper.assertTrue(inventory.insert(key, 1L, Actionable.SIMULATE, IActionSource.empty()) == 0L
                            && inventory.insert(key, 1L, Actionable.MODULATE, IActionSource.empty()) == 0L,
                    "A full key cannot overflow into another slot");
            KeyCounter received = new KeyCounter();
            inventory.injectIntoNetwork(receiver(received, new AtomicBoolean(true)), IActionSource.empty(), stack -> {});
            helper.assertTrue(received.get(key) == Long.MAX_VALUE && inventory.isEmpty(), "Native injection returns every unit");
        }
        var iron = AEItemKey.of(Items.IRON_INGOT);
        inventory.insert(9, iron, 100L, Actionable.MODULATE);
        helper.assertTrue(inventory.insert(9, iron, Long.MAX_VALUE, Actionable.SIMULATE) == Long.MAX_VALUE - 100L
                        && inventory.getAmount(9) == 100L, "Simulation subtracts occupied capacity before addition");
        helper.assertTrue(inventory.insert(9, iron, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE - 100L
                        && inventory.getAmount(9) == Long.MAX_VALUE, "Actual insertion agrees with simulation near the long limit");
        helper.assertTrue(original.insert(iron, 10_000L, Actionable.MODULATE, IActionSource.empty()) == 576L,
                "Unrelated AE2 providers retain their nine-slot capacity");
        helper.succeed();
    }

    public void componentKeysPersistenceAndTypeLimit(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        List<AEItemKey> keys = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            AEItemKey key = variant(helper, index);
            keys.add(key);
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()) == Long.MAX_VALUE,
                    "All 256 component-bearing keys independently accept Long.MAX_VALUE");
        }
        for (AEKey key : keys) helper.assertTrue(inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                "Different complete keys do not share a long total budget");
        helper.assertTrue(inventory.insert(AEItemKey.of(Items.DIAMOND), 1L, Actionable.SIMULATE, IActionSource.empty()) == 0L,
                "Occupied physical and hidden slots reject another resource type");
        CompoundTag saved = new CompoundTag();
        host.saveAdditional(saved, helper.getLevel().registryAccess());
        host.onChunkUnloaded();
        var restored = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(2, 0, 0)),
                ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        for (AEKey key : keys) helper.assertTrue(restored.getLogic().getReturnInv().getAvailableStacks().get(key) == Long.MAX_VALUE,
                "Native NBT and chunk unload preserve components and exact quantities");
        AtomicBoolean online = new AtomicBoolean(false);
        KeyCounter received = new KeyCounter();
        var network = receiver(received, online);
        helper.assertTrue(!restored.getLogic().getReturnInv().injectIntoNetwork(network, IActionSource.empty(), stack -> {}),
                "Network rejection retains all hidden resources");
        online.set(true);
        KeyCounter notified = new KeyCounter();
        restored.getLogic().getReturnInv().injectIntoNetwork(network, IActionSource.empty(),
                stack -> notified.add(stack.what(), stack.amount()));
        for (AEKey key : keys) helper.assertTrue(received.get(key) == Long.MAX_VALUE && notified.get(key) == Long.MAX_VALUE,
                "Network recovery returns exact balances and preserves AE2 settlement callbacks");
        helper.assertTrue(restored.getLogic().getReturnInv().isEmpty(), "Recovered inventory drains completely");
        helper.succeed();
    }

    public void cumulativeReturnSimulation(GameTestHelper helper) {
        var inventory = place(helper).getLogic().getReturnInv();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        inventory.insert(9, iron, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        var simulated = AE2NativeAdapters.copyForSimulation(inventory);
        helper.assertTrue(simulated.insert(iron, 4L, Actionable.MODULATE, IActionSource.empty()) == 4L
                        && simulated.insert(iron, 4L, Actionable.MODULATE, IActionSource.empty()) == 1L,
                "Cumulative native preflight respects the shared complete-key budget");
        helper.assertTrue(inventory.getAvailableStacks().get(iron) == Long.MAX_VALUE - 5L,
                "Failed native preflight does not touch the real material owner");
        inventory.clear();
        simulated = AE2NativeAdapters.copyForSimulation(inventory);
        helper.assertTrue(simulated.insert(0, iron, 1000L, Actionable.MODULATE) == 64L
                        && simulated.insert(9, iron, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE - 64L,
                "The isolated preflight copy preserves visible capacities and hidden long slots");
        helper.succeed();
    }

    public void oldSaveMenuAndBoundedDrops(GameTestHelper helper) {
        var host = place(helper);
        var old = new PatternProviderReturnInventory(() -> {});
        var key = variant(helper, 500);
        old.setStack(0, new GenericStack(key, 64L));
        old.setStack(8, new GenericStack(key, 16L));
        CompoundTag saved = new CompoundTag();
        old.writeToChildTag(saved, "returnInv", helper.getLevel().registryAccess());
        host.getLogic().readFromNBT(saved, helper.getLevel().registryAccess());
        var inventory = host.getLogic().getReturnInv();
        helper.assertTrue(inventory.getAmount(0) == 64L && inventory.getAmount(8) == 16L && key.equals(inventory.getKey(8)),
                "Old nine-slot saves keep their original positions and components");
        inventory.insert(264, AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE, Actionable.MODULATE);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new PatternInterfaceMenu(AE2MenuTypes.PATTERN, 0, player.getInventory(), host);
        helper.assertTrue(menu.getSlots(SlotSemantics.STORAGE).size() == 9
                        && menu.getSlots(SlotSemantics.STORAGE).get(8).getItem().getCount() == 16,
                "Menu synchronizes only the original nine ordinary slots");
        List<ItemStack> drops = new ArrayList<>();
        inventory.addDrops(drops, helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND)).count() == 1024L,
                "Even the last hidden slot produces bounded ordinary drops");
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.IRON_INGOT)).mapToLong(ItemStack::getCount).sum() == 80L
                        && drops.stream().filter(stack -> stack.is(Items.IRON_INGOT)).allMatch(stack -> key.equals(AEItemKey.of(stack))),
                "Old visible contents drop with their exact components");
        helper.assertTrue(drops.stream().allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()),
                "All physical drops are legal item stacks");
        helper.succeed();
    }

    public void nativeTransferAndMenuInsertionConserveResources(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        var key = AEItemKey.of(Items.IRON_INGOT);
        var items = host.nativeItemHandler();
        inventory.insert(0, key, 64L, Actionable.MODULATE);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                "Full physical slots cannot falsely accept an external item");
        helper.assertTrue(items.insertItem(9, new ItemStack(Items.IRON_INGOT, 10_000), false).isEmpty()
                        && inventory.getAmount(9) == 10_000L, "Native handler inserts large outputs into hidden slots");
        inventory.clear();
        inventory.insert(9, key, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), true).getCount() == 59
                        && inventory.getAmount(0) == 0L, "Simulation respects the final five units without mutation");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new PatternInterfaceMenu(AE2MenuTypes.PATTERN, 0, player.getInventory(), host);
        int visible = menu.getSlots(SlotSemantics.STORAGE).getFirst().index;
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 64));
        menu.clicked(visible, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 59 && inventory.getAmount(0) == 5L
                        && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                "Menu clicks take only the units that fit before reducing the player's stack");
        menu.setCarried(ItemStack.EMPTY);
        inventory.setStack(0, null);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        menu.clicked(menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 59
                        && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE, "Shift insertion conserves unaccepted items");
        inventory.clear();
        inventory.insert(0, key, 64L, Actionable.MODULATE);
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 64));
        menu.clicked(visible, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 64
                        && inventory.getKey(0).equals(AEItemKey.of(Items.GOLD_INGOT)), "Full visible slots still allow legal replacement");
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        menu.clicked(visible, 0, ClickType.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.GOLD_INGOT) && inventory.getAmount(0) == 32L
                        && inventory.getKey(0).equals(key), "Hotbar replacement conserves both stacks");
        inventory.clear();
        var water = AEFluidKey.of(Fluids.WATER);
        long nativeCapacity = new PatternProviderReturnInventory(() -> {}).getMaxAmount(water);
        inventory.insert(0, water, nativeCapacity, Actionable.MODULATE);
        helper.assertTrue(host.nativeFluidHandler().fill(water.toStack(10_000),
                        FluidAction.EXECUTE) == 10_000,
                "Native fluid insertion uses remaining visible and hidden tanks");
        helper.assertTrue(inventory.getAmount(0) == nativeCapacity
                        && inventory.getAvailableStacks().get(water) == nativeCapacity + 10_000L,
                "Native fluid success equals the actual balance increase");
        helper.succeed();
    }

    public void extendedInterfaceUsesTheSameOverflow(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(ExtendedPatternInterfaceKind.INSTANCE.id()).get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        helper.assertTrue(host.getLogic().getPatternInv().size() == 36
                        && host.getLogic().getReturnInv().insert(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                        Actionable.MODULATE, IActionSource.empty()) == Long.MAX_VALUE,
                "ExtendedAE keeps 36 pattern slots and shares the hidden long return inventory");
        helper.succeed();
    }

    public void nativeOutputPlanningConservesLongBudgets(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var items = new ItemBusCapability(host.nativeItemHandler(), IOType.OUTPUT);
        var context = new CraftingContext(new CapabilitySnapshot(List.of(items)));
        inventory.insert(9, iron, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        var blocked = context.planOutputRequirements(List.of(MachineRequirement.itemOutput(new ItemStack(Items.IRON_INGOT, 10))), 1L, false);
        helper.assertTrue(!blocked.successful() && inventory.getAvailableStacks().get(iron) == Long.MAX_VALUE - 5L,
                "Full output admission rejects cross-slot reuse of the final five units before any insertion");
        var aliases = new MachineIoView(new CapabilitySnapshot(List.of(items,
                new ItemBusCapability(host.nativeItemHandler(), IOType.OUTPUT))));
        helper.assertTrue(aliases.itemOutputCapacity(new ItemStack(Items.IRON_INGOT)) == 5L,
                "Output capacity queries count the shared key budget only once across aliased capabilities");
        var worker = CraftingContext.prepareAsyncPlan(List.of(MachineRequirement.itemOutput(new ItemStack(Items.IRON_INGOT, 10))),
                1L, context.captureAsyncCapabilities(Set.of(items.type().id()))).plan();
        helper.assertTrue(worker.mainThreadRequirements().contains(0),
                "ASYNC candidates require the actual server-side output budget before selecting or consuming input");
        inventory.clear();
        var huge = context.planOutputRequirements(List.of(MachineRequirement.itemOutput(new ItemStack(Items.IRON_INGOT))), Long.MAX_VALUE, false);
        helper.assertTrue(huge.successful() && huge.plan().commit()
                        && inventory.getAvailableStacks().get(iron) == Long.MAX_VALUE,
                "Real native item planning and execution can settle Long.MAX_VALUE without int-sized iteration");
        helper.assertTrue(aliases.itemOutputCapacity(new ItemStack(Items.IRON_INGOT)) == 0L,
                "Empty hidden slots cannot create output capacity for an already full key");
        inventory.clear();
        var swords = context.planOutputRequirements(List.of(MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND_SWORD, 2))), 1L, false);
        helper.assertTrue(swords.successful() && swords.plan().commit()
                        && inventory.getAmount(0) == 1L && inventory.getAmount(1) == 1L,
                "Planning keeps native non-stackable item slots at one even though hidden slots support long balances");
        inventory.clear();
        var water = AEFluidKey.of(Fluids.WATER);
        inventory.insert(9, water, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        helper.assertTrue(host.nativeFluidHandler().fill(water.toStack(10), FluidAction.SIMULATE) == 5
                        && inventory.getAmount(0) == 0L
                        && host.nativeFluidHandler().fill(water.toStack(10), FluidAction.EXECUTE) == 5,
                "Aggregated fluid simulation and execution share one complete-key budget");
        inventory.clear();
        var fluids = new FluidHatchCapability(host.nativeFluidHandler(), IOType.OUTPUT);
        var fluidContext = new CraftingContext(new CapabilitySnapshot(List.of(fluids)));
        var fluidWorker = CraftingContext.prepareAsyncPlan(List.of(MachineRequirement.fluidOutput(water.toStack(10))),
                1L, fluidContext.captureAsyncCapabilities(Set.of(fluids.type().id()))).plan();
        helper.assertTrue(fluidWorker.mainThreadRequirements().contains(0),
                "ASYNC fluid candidates also delegate inventory-wide budgets to the server planner");
        var hugeFluid = fluidContext.planOutputRequirements(List.of(MachineRequirement.fluidOutput(water.toStack(1))), Long.MAX_VALUE, false);
        helper.assertTrue(hugeFluid.successful() && hugeFluid.plan().commit()
                        && inventory.getAvailableStacks().get(water) == Long.MAX_VALUE,
                "Real native fluid planning and execution settle the complete long quantity");
        helper.assertTrue(new MachineIoView(new CapabilitySnapshot(List.of(fluids))).fluidOutputCapacity(water.toStack(1)) == 0L,
                "Fluid output queries cannot reuse empty tanks after the complete key reaches its long limit");
        inventory.clear();
        helper.succeed();
    }

    public void nativeSyncUsesCompleteNewProjections(GameTestHelper helper) {
        var target = place(helper);
        var source = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(2, 0, 0)),
                ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var old = target.getLogic().getReturnInv();
        var next = source.getLogic().getReturnInv();
        old.insert(0, iron, 5L, Actionable.MODULATE);
        old.insert(9, iron, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        old.insert(1, water, 5L, Actionable.MODULATE);
        old.insert(10, water, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        next.insert(0, iron, 10L, Actionable.MODULATE);
        next.insert(9, iron, Long.MAX_VALUE - 10L, Actionable.MODULATE);
        next.insert(1, water, 10L, Actionable.MODULATE);
        next.insert(10, water, Long.MAX_VALUE - 10L, Actionable.MODULATE);
        var itemSource = new ItemBusCapability(source.nativeItemHandler(), IOType.OUTPUT);
        var itemTarget = new ItemBusCapability(target.nativeItemHandler(), IOType.OUTPUT);
        sync(helper, itemSource::encode, itemTarget::decode);
        helper.assertTrue(old.getAmount(0) == 10L && old.getAmount(9) == Long.MAX_VALUE - 10L
                        && old.getAvailableStacks().get(water) == Long.MAX_VALUE,
                "Item sync applies the complete new balance without clipping against old hidden amounts or clearing fluids");
        var fluidSource = new FluidHatchCapability(source.nativeFluidHandler(), IOType.OUTPUT);
        var fluidTarget = new FluidHatchCapability(target.nativeFluidHandler(), IOType.OUTPUT);
        sync(helper, fluidSource::encode, fluidTarget::decode);
        helper.assertTrue(old.getAmount(1) == 10L && old.getAmount(10) == Long.MAX_VALUE - 10L
                        && old.getAvailableStacks().get(iron) == Long.MAX_VALUE,
                "Fluid sync preserves the item projection and independently updates the full fluid balance");
        GenericStack[] invalid = new GenericStack[old.size()];
        invalid[0] = new GenericStack(iron, 1L);
        invalid[9] = new GenericStack(iron, Long.MAX_VALUE);
        boolean rejected = false;
        try {
            ((PatternReturnInventory) old).replaceProjection(iron.getType(), invalid);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected && old.getAmount(0) == 10L && old.getAmount(9) == Long.MAX_VALUE - 10L,
                "Invalid cross-slot totals are rejected before replacing any synchronized content");
        old.clear();
        next.clear();
        helper.succeed();
    }

    private static void sync(GameTestHelper helper, Consumer<RegistryFriendlyByteBuf> encode, Consumer<RegistryFriendlyByteBuf> decode) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            encode.accept(buffer);
            decode.accept(buffer);
        } finally {
            buffer.release();
        }
    }

    private static PatternInterfaceBlockEntity place(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        return helper.getBlockEntity(BlockPos.ZERO);
    }

    private static AEItemKey variant(GameTestHelper helper, int index) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("return-key-" + index));
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), index % 5 + 1);
        return AEItemKey.of(stack);
    }

    private static MEStorage receiver(KeyCounter received, AtomicBoolean online) {
        return new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (!online.get()) return 0L;
                if (mode == Actionable.MODULATE) received.add(key, amount);
                return amount;
            }
            @Override public Component getDescription() { return Component.empty(); }
        };
    }
}

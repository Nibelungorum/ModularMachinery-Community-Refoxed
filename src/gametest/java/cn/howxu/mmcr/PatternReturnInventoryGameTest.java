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
import appeng.helpers.externalstorage.GenericStackItemHandler;
import appeng.helpers.externalstorage.GenericStackFluidHandler;
import appeng.menu.SlotSemantics;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternReturnInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedPatternInterfaceKind;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exercises the actual mixin-installed pattern inventory with game registries and AE2 callbacks.
 *
 * @author howxu <dev@howxu.cn>
 */
public class PatternReturnInventoryGameTest {
    public void nativeSlotsAndExactLongBalances(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        helper.assertTrue(inventory instanceof PatternReturnInventory, "MMCR installs its real overflow inventory");
        var nativeInventory = new PatternProviderReturnInventory(() -> {});
        List<AEKey> keys = List.of(AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.ENDER_PEARL),
                AEItemKey.of(Items.DIAMOND_SWORD), AEFluidKey.of(Fluids.WATER));
        for (AEKey key : keys) {
            inventory.clear();
            helper.assertTrue(inventory.insert(0, key, Long.MAX_VALUE, Actionable.MODULATE)
                            == nativeInventory.getMaxAmount(key),
                    "Visible slot retains AE2's physical item or fluid capacity");
            inventory.clear();
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty())
                            == Long.MAX_VALUE && inventory.isEmpty(), "Simulation accepts the exact long amount without mutation");
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty())
                            == Long.MAX_VALUE, "Actual insertion stores Long.MAX_VALUE regardless of physical stack size");
            helper.assertTrue(inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                    "Complete-key balance is exact across visible and hidden slots");
            helper.assertTrue(inventory.insert(key, 1L, Actionable.SIMULATE, IActionSource.empty()) == 0L
                            && inventory.insert(key, 1L, Actionable.MODULATE, IActionSource.empty()) == 0L,
                    "A full key cannot overflow into another slot");
            KeyCounter extracted = new KeyCounter();
            inventory.injectIntoNetwork(receiver(extracted, new AtomicBoolean(true)), IActionSource.empty(), stack -> {});
            helper.assertTrue(extracted.get(key) == Long.MAX_VALUE && inventory.isEmpty(),
                    "Native network extraction returns the entire exact amount");
        }
        var iron = AEItemKey.of(Items.IRON_INGOT);
        inventory.insert(9, iron, 100L, Actionable.MODULATE);
        helper.assertTrue(inventory.insert(9, iron, Long.MAX_VALUE, Actionable.SIMULATE) == Long.MAX_VALUE - 100L
                        && inventory.getAmount(9) == 100L,
                "Slot simulation subtracts existing balance instead of overflowing current plus requested");
        helper.assertTrue(inventory.insert(9, iron, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE - 100L
                        && inventory.getAmount(9) == Long.MAX_VALUE,
                "Actual insertion accepts exactly the remaining amount");
        helper.assertTrue(nativeInventory.insert(iron, 10_000L, Actionable.MODULATE, IActionSource.empty()) == 576L,
                "Unrelated native AE2 providers retain their original nine-slot capacity");
        helper.succeed();
    }

    public void componentKeysPersistenceAndTypeLimit(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        List<AEItemKey> keys = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            AEItemKey key = variant(helper, index);
            keys.add(key);
            helper.assertTrue(inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty())
                            == Long.MAX_VALUE, "All 256 complete keys independently accept Long.MAX_VALUE");
        }
        KeyCounter balances = inventory.getAvailableStacks();
        for (AEKey key : keys) helper.assertTrue(balances.get(key) == Long.MAX_VALUE,
                "Totals exceeding long range across different resources do not change individual balances");
        var excess = AEItemKey.of(Items.DIAMOND);
        helper.assertTrue(inventory.insert(excess, 1L, Actionable.SIMULATE, IActionSource.empty()) == 0L
                        && inventory.insert(excess, 1L, Actionable.MODULATE, IActionSource.empty()) == 0L,
                "All occupied visible and hidden slots reject a new type exactly");
        KeyCounter cpuInput = new KeyCounter();
        cpuInput.add(excess, 1L);
        var request = host.itemRequestStorage(new KeyCounter[]{cpuInput});
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(!request.accept(host.itemReturnStorage(), transaction),
                    "Type overflow is rejected before claiming CPU input");
            transaction.commit();
        }
        helper.assertTrue(cpuInput.get(excess) == 1L, "Rejected CPU input still belongs to AE2");

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        host.saveAdditional(saved);
        host.onChunkUnloaded();
        var restored = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(2, 0, 0)),
                ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()));
        KeyCounter reloaded = restored.getLogic().getReturnInv().getAvailableStacks();
        for (AEKey key : keys) helper.assertTrue(reloaded.get(key) == Long.MAX_VALUE,
                "Save, chunk unload and reload retain exact counts and complete components");
        AtomicBoolean online = new AtomicBoolean(false);
        KeyCounter received = new KeyCounter();
        var network = receiver(received, online);
        helper.assertTrue(!restored.getLogic().getReturnInv().injectIntoNetwork(network, IActionSource.empty(), stack -> {}),
                "Network refusal keeps all resources in the return buffer");
        online.set(true);
        KeyCounter notified = new KeyCounter();
        restored.getLogic().getReturnInv().injectIntoNetwork(network, IActionSource.empty(),
                stack -> notified.add(stack.what(), stack.amount()));
        for (AEKey key : keys) helper.assertTrue(received.get(key) == Long.MAX_VALUE && notified.get(key) == Long.MAX_VALUE,
                "Recovery returns every hidden key exactly and preserves AE2 insertion callbacks");
        helper.assertTrue(restored.getLogic().getReturnInv().isEmpty(), "All slots drain after recovery");
        helper.succeed();
    }

    public void transactionalReturnsAndOutputPlanning(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        AEItemKey key = AEItemKey.of(iron);
        KeyCounter inputs = new KeyCounter();
        inputs.add(key, Long.MAX_VALUE);
        var request = host.itemRequestStorage(new KeyCounter[]{inputs});
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(request.extract(0, iron, 1L, transaction) == 1L
                            && request.accept(host.itemReturnStorage(), transaction),
                    "Consumption and huge leftovers are admitted in the same transaction");
            helper.assertTrue(inputs.get(key) == 0L, "CPU ownership changes only inside the admission transaction");
        }
        helper.assertTrue(inputs.get(key) == Long.MAX_VALUE && request.amount(0) == Long.MAX_VALUE
                        && inventory.isEmpty(), "Root rollback restores consumption, CPU ownership and all buffer slots");
        try (Transaction transaction = Transaction.openRoot()) {
            request.extract(0, iron, 1L, transaction);
            helper.assertTrue(request.accept(host.itemReturnStorage(), transaction), "Repeated admission succeeds after rollback");
            transaction.commit();
        }
        helper.assertTrue(inputs.get(key) == 0L && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE - 1L,
                "Committed leftovers are returned once and consumption remains exact");
        var output = host.itemOutputStorage();
        var reservations = new PlanningReservations();
        helper.assertTrue(output.planOutput(iron, Long.MAX_VALUE, reservations, false).accepted() == 1L,
                "Output planning respects the complete key budget across all slots");
        helper.assertTrue(output.planOutput(iron, 1L, reservations, false).accepted() == 0L,
                "A second reservation cannot spend the same final unit");
        KeyCounter excess = new KeyCounter();
        excess.add(key, 2L);
        var rejected = host.itemRequestStorage(new KeyCounter[]{excess});
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(!rejected.accept(host.itemReturnStorage(), transaction), "Partial returns cannot claim a full request");
            transaction.commit();
        }
        helper.assertTrue(excess.get(key) == 2L && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE - 1L,
                "Failed nested return allocation restores even the partially inserted first unit");
        inventory.clear();
        var plan = output.planOutput(iron, Long.MAX_VALUE, new PlanningReservations(), true);
        helper.assertTrue(plan.accepted() == Long.MAX_VALUE, "Output plans use hidden long capacity");
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(plan.operation().commit(transaction).success(), "Output operation stores the exact long amount");
            transaction.commit();
        }
        helper.assertTrue(inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                "Committed production has no int conversion or shared multi-resource budget");
        helper.succeed();
    }

    public void oldSaveMenuAndBoundedDrops(GameTestHelper helper) {
        var host = place(helper);
        var old = new PatternProviderReturnInventory(() -> {});
        AEItemKey key = variant(helper, 500);
        old.setStack(0, new GenericStack(key, 64L));
        old.setStack(8, new GenericStack(key, 16L));
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        old.writeToTag(saved.childrenList("returnInv"));
        host.getLogic().readFromNBT(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()));
        var inventory = host.getLogic().getReturnInv();
        helper.assertTrue(inventory.getAmount(0) == 64L && inventory.getAmount(8) == 16L
                        && key.equals(inventory.getKey(8)), "Old nine-slot lists keep their slot contents and components");
        inventory.insert(264, AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE, Actionable.MODULATE);
        var menu = new PatternInterfaceMenu(AE2MenuTypes.PATTERN, 0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), host);
        helper.assertTrue(menu.getSlots(SlotSemantics.STORAGE).size() == 9
                        && menu.getSlots(SlotSemantics.STORAGE).get(8).getItem().getCount() == 16,
                "UI retains just the original nine ordinary slots; hidden balances are not synchronized as item counts");
        List<ItemStack> drops = new ArrayList<>();
        inventory.addDrops(drops, helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(drops.isEmpty(), "Native addDrops cannot also discharge balances owned by the block carrier");
        helper.assertTrue(inventory.getAvailableStacks().get(key) == 80L
                        && inventory.getAvailableStacks().get(AEItemKey.of(Items.DIAMOND)) == Long.MAX_VALUE
                        && host.collectComponents().has(DataComponents.BLOCK_ENTITY_DATA),
                "Old component keys and extreme return quantities remain available to the compressed block carrier");
        helper.succeed();
    }

    public void extendedInterfaceUsesTheSameOverflow(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(ExtendedPatternInterfaceKind.INSTANCE.id()).get().defaultBlockState());
        var host = helper.getBlockEntity(BlockPos.ZERO, PatternInterfaceBlockEntity.class);
        AEItemKey key = AEItemKey.of(Items.IRON_INGOT);
        helper.assertTrue(host.getLogic().getPatternInv().size() == 36
                        && host.getLogic().getReturnInv().insert(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty())
                        == Long.MAX_VALUE, "ExtendedAE keeps 36 pattern slots and shares the hidden long return inventory");
        helper.succeed();
    }

    public void nativeTransferAndMenuInsertionConserveResources(GameTestHelper helper) {
        var host = place(helper);
        var inventory = host.getLogic().getReturnInv();
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        AEItemKey key = AEItemKey.of(iron);
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(BlockPos.ZERO), null);
        helper.assertTrue(items instanceof GenericStackItemHandler, "Real registered AE2 item capability is exercised");
        inventory.insert(0, key, 64L, Actionable.MODULATE);
        helper.assertTrue(items.getCapacityAsLong(0, iron) == 64L,
                "Native item handler reports physical slot capacity: " + items.getCapacityAsLong(0, iron));
        try (Transaction transaction = Transaction.openRoot()) {
            int accepted = items.insert(0, iron, 1, transaction);
            helper.assertTrue(accepted == 0,
                    "Native handler cannot claim an insertion into a full physical slot: accepted=" + accepted
                            + ", balance=" + inventory.getAmount(0));
            helper.assertTrue(items.insert(iron, 10_000, transaction) == 10_000,
                    "Native handler routes excess into hidden slots instead of swallowing it");
            transaction.commit();
        }
        helper.assertTrue(inventory.getAvailableStacks().get(key) == 10_064L, "Handler success equals actual balance increase");
        inventory.clear();
        inventory.insert(9, key, Long.MAX_VALUE - 5L, Actionable.MODULATE);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(items.insert(0, iron, 64, transaction) == 5, "Native handler respects the complete-key budget");
        }
        helper.assertTrue(inventory.getAvailableStacks().get(key) == Long.MAX_VALUE - 5L,
                "Native capability simulation rollback keeps the original long amount");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new PatternInterfaceMenu(AE2MenuTypes.PATTERN, 0, player.getInventory(), host);
        int visibleSlot = menu.getSlots(SlotSemantics.STORAGE).getFirst().index;
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 64));
        menu.clicked(visibleSlot, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 59 && inventory.getAmount(0) == 5L
                        && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                "Ordinary menu click takes only the five units that fit before mutating the player's stack");
        menu.setCarried(ItemStack.EMPTY);
        inventory.setStack(0, null);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        int playerSlot = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index;
        menu.clicked(playerSlot, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 59
                         && inventory.getAvailableStacks().get(key) == Long.MAX_VALUE,
                 "Shift insertion likewise retains every unaccepted player item");
        inventory.clear();
        inventory.insert(0, key, 64L, Actionable.MODULATE);
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 64));
        menu.clicked(visibleSlot, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 64
                        && inventory.getKey(0).equals(AEItemKey.of(Items.GOLD_INGOT)) && inventory.getAmount(0) == 64L,
                "A full visible slot can still be replaced with a different resource");
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        menu.clicked(visibleSlot, 0, ContainerInput.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(0).is(Items.GOLD_INGOT)
                        && player.getInventory().getItem(0).getCount() == 64 && inventory.getAmount(0) == 32L
                        && inventory.getKey(0).equals(key), "Hotbar swap preserves both resource stacks");
        inventory.clear();
        var fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(BlockPos.ZERO), null);
        helper.assertTrue(fluids instanceof GenericStackFluidHandler, "Real registered AE2 fluid capability is exercised");
        FluidResource water = FluidResource.of(Fluids.WATER);
        AEFluidKey waterKey = AEFluidKey.of(water);
        long nativeCapacity = new PatternProviderReturnInventory(() -> {}).getMaxAmount(waterKey);
        inventory.insert(0, waterKey, nativeCapacity, Actionable.MODULATE);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(fluids.insert(0, water, 1, transaction) == 0,
                    "Native fluid handler cannot overstate acceptance in a full visible tank");
            helper.assertTrue(fluids.insert(9, water, 10_000, transaction) == 10_000,
                    "Native fluid handler can use the hidden buffer");
            transaction.commit();
        }
        helper.assertTrue(inventory.getAmount(0) == nativeCapacity && inventory.getAmount(9) == 10_000L,
                "Native fluid balances match the reported insertions exactly");
        helper.succeed();
    }

    private static PatternInterfaceBlockEntity place(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        return helper.getBlockEntity(BlockPos.ZERO, PatternInterfaceBlockEntity.class);
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
            @Override
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (!online.get()) return 0L;
                if (mode == Actionable.MODULATE) received.add(key, amount);
                return amount;
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }
        };
    }
}

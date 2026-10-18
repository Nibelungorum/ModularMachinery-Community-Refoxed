package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** Native v1 migration refunds whole groups, without execution or a simulated handler rollback.
 * @author howxu <dev@howxu.cn>
 */
public class PendingPatternQueueGameTest {
    public void mixedLegacyGroupRefundsExactlyOnce(GameTestHelper helper) {
        var host = place(helper);
        AEItemKey iron = namedIron("refund-components");
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        KeyCounter inputs = counter(iron, 5L);
        inputs.add(water, 1_000L);
        read(helper, host.pendingPatterns(), legacyData(helper, new KeyCounter[]{inputs}, outputs(), 7L));
        helper.assertTrue(host.pendingPatterns().refundNext(host), "Whole mixed legacy group transfers synchronously");
        helper.assertTrue(host.pendingPatterns().isEmpty()
                        && host.getLogic().getReturnInv().getAvailableStacks().get(iron) == 35L
                        && host.getLogic().getReturnInv().getAvailableStacks().get(water) == 7_000L,
                "Every complete resource identity transfers to returns, not to a recipe");
        helper.assertFalse(host.pendingPatterns().refundNext(host), "Transferred material cannot refund twice");
        helper.succeed();
    }

    public void largeCompressedGroupRefundsAtomically(GameTestHelper helper) {
        var host = place(helper);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        long copies = 2_147_483_647L;
        read(helper, host.pendingPatterns(), legacyData(helper, new KeyCounter[]{counter(iron, 5L)}, outputs(), copies));
        helper.assertTrue(host.pendingPatterns().refundNext(host) && host.pendingPatterns().isEmpty()
                        && host.getLogic().getReturnInv().getAvailableStacks().get(iron) == 5L * copies,
                "A compressed legacy group refunds as one complete long balance");
        helper.assertFalse(host.pendingPatterns().refundNext(host), "No duplicate refund");
        helper.succeed();
    }

    public void fullReturnsPreserveBothOwners(GameTestHelper helper) {
        var host = place(helper);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        read(helper, host.pendingPatterns(), legacyData(helper, new KeyCounter[]{counter(iron, 5L)}, outputs(), 2L));
        var inventory = host.getLogic().getReturnInv();
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, new GenericStack(AEItemKey.of(Items.DIAMOND), 64L));
        }
        CompoundTag before = save(helper, host.pendingPatterns(), 0L);
        helper.assertFalse(host.pendingPatterns().refundNext(host), "Full native inventory rejects before changing either owner");
        helper.assertTrue(save(helper, host.pendingPatterns(), 0L).equals(before)
                        && inventory.getAvailableStacks().get(iron) == 0L,
                "A blocked refund retains the entire original escrow");
        inventory.setStack(0, null);
        helper.assertTrue(host.pendingPatterns().refundNext(host) && host.pendingPatterns().isEmpty()
                        && inventory.getAvailableStacks().get(iron) == 10L,
                "Capacity recovery refunds once without ever starting a recipe");
        helper.succeed();
    }

    public void oldSaveHasEmptyPendingQueue(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        read(helper, queue, legacyData(helper, new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 1L)}, outputs(), 1L));
        read(helper, queue, new CompoundTag());
        helper.assertTrue(queue.isEmpty() && queue.groups().isEmpty(), "Pre-escrow saves load no ownership");
        helper.succeed();
    }

    public void nativeRefundCallbacksObserveSingleOwner(GameTestHelper helper) {
        var host = place(helper);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        read(helper, host.pendingPatterns(), legacyData(helper, new KeyCounter[]{counter(iron, 5L)}, outputs(), 1L));
        var inventory = host.getLogic().getReturnInv();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        try {
            Field field = GenericStackInv.class.getDeclaredField("listener");
            field.setAccessible(true);
            Object original = field.get(inventory);
            boolean notificationFailed = false;
            try {
                field.set(inventory, (Runnable) () -> {
                    helper.assertTrue(host.pendingPatterns().isEmpty()
                                    && inventory.getAvailableStacks().get(iron) == 5L,
                            "Native callback sees the completed ownership handoff");
                    helper.assertFalse(host.pendingPatterns().refundNext(host), "Reentry cannot transfer the same group again");
                    saved.set(host.saveWithoutMetadata(helper.getLevel().registryAccess()));
                    throw new IllegalStateException("Injected native refund notification failure");
                });
                host.pendingPatterns().refundNext(host);
            } catch (IllegalStateException expected) {
                notificationFailed = true;
            } finally {
                field.set(inventory, original);
            }
            helper.assertTrue(notificationFailed && saved.get() != null && host.pendingPatterns().isEmpty()
                            && inventory.getAvailableStacks().get(iron) == 5L,
                    "A failed notification cannot roll back transferred native ownership");
            var restored = new PendingPatternQueue();
            read(helper, restored, saved.get());
            helper.assertTrue(restored.isEmpty(), "Callback saves cannot resurrect the refunded group");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        helper.succeed();
    }

    public void pendingQueueRoundTripPreservesComponentsAndLongCounts(GameTestHelper helper) {
        var iron = namedIron("component-input");
        var water = AEFluidKey.of(Fluids.WATER);
        var mixed = counter(iron, 3L);
        mixed.add(water, 1_000L);
        long copies = 2_147_483_647L;
        var queue = new PendingPatternQueue();
        read(helper, queue, legacyData(helper, new KeyCounter[]{mixed, counter(iron, 5L)}, outputs(), copies));
        var restored = new PendingPatternQueue();
        read(helper, restored, save(helper, queue, 0L));
        var group = restored.groups().getFirst();
        helper.assertTrue(group.copies() == copies && group.inputs(copies)[0].get(iron) == 3L * copies
                        && group.inputs(copies)[0].get(water) == 1_000L * copies
                        && group.inputs(copies)[1].get(iron) == 5L * copies,
                "Registry-aware persistence preserves components, slots and long balances");
        helper.succeed();
    }

    public void reloadKeepsAgeWithoutOfflineAdvance(GameTestHelper helper) {
        CompoundTag data = legacyData(helper, new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 1L)}, outputs(), 2L);
        var group = savedGroup(data);
        group.putLong("first_age", 10L);
        group.putLong("last_age", 6L);
        var queue = new PendingPatternQueue();
        read(helper, queue, data);
        helper.assertTrue(save(helper, queue, 1_000_000L).equals(data), "Saving before rebase retains saved ages");
        queue.rebaseClock(1_000_000L);
        helper.assertTrue(queue.groups().getFirst().firstTick() == 999_990L
                        && queue.groups().getFirst().lastTick() == 999_994L,
                "Offline time cannot advance legacy age");
        helper.succeed();
    }

    public void damagedOrFutureDataIsPreservedAndBlocked(GameTestHelper helper) {
        var host = place(helper);
        CompoundTag valid = legacyData(helper, new KeyCounter[]{counter(AEItemKey.of(Items.IRON_INGOT), 1L)}, outputs(), 1L);
        List<CompoundTag> damaged = new ArrayList<>();
        CompoundTag future = valid.copy();
        future.getCompound("pending_patterns").putInt("version", 99);
        damaged.add(future);
        CompoundTag unknown = valid.copy();
        savedGroup(unknown).getList("inputs", Tag.TAG_LIST).getList(0).getCompound(0)
                .putString("#t", "mmcr:unknown_resource_family");
        damaged.add(unknown);
        CompoundTag missingDefinition = valid.copy();
        savedGroup(missingDefinition).putString("definition", "unknown-definition");
        damaged.add(missingDefinition);
        CompoundTag missingOutput = valid.copy();
        savedGroup(missingOutput).getList("outputs", Tag.TAG_COMPOUND).getCompound(0)
                .putString("#t", "mmcr:unknown_output_family");
        damaged.add(missingOutput);
        CompoundTag overflow = valid.copy();
        savedGroup(overflow).putLong("copies", Long.MAX_VALUE);
        savedGroup(overflow).getList("inputs", Tag.TAG_LIST).getList(0).getCompound(0).putLong("#", 2L);
        damaged.add(overflow);
        CompoundTag invalid = valid.copy();
        savedGroup(invalid).putLong("copies", 0L);
        damaged.add(invalid);
        CompoundTag scalar = new CompoundTag();
        scalar.putString("pending_patterns", "opaque-owned-data");
        damaged.add(scalar);
        for (CompoundTag source : damaged) {
            CompoundTag expected = source.copy();
            read(helper, host.pendingPatterns(), source);
            source.putString("pending_patterns", "external mutation");
            helper.assertFalse(host.pendingPatterns().refundNext(host), "Opaque ownership cannot refund partially");
            helper.assertTrue(!host.pendingPatterns().isEmpty() && host.pendingPatterns().groups().isEmpty()
                            && host.getLogic().getReturnInv().isEmpty(), "Invalid balances remain opaque ownership");
            var offered = counter(AEItemKey.of(Items.IRON_INGOT), 1L);
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L)), outputs()), helper.getLevel());
            helper.assertFalse(host.craftingMachine().pushPattern(pattern, new KeyCounter[]{offered}, Direction.UP),
                    "A real CPU request is blocked by opaque legacy ownership");
            helper.assertTrue(offered.get(AEItemKey.of(Items.IRON_INGOT)) == 1L
                            && save(helper, host.pendingPatterns(), 0L).equals(expected),
                    "CPU counters and opaque save bytes are preserved");
        }
        helper.succeed();
    }

    static CompoundTag legacyData(GameTestHelper helper, KeyCounter[] inputs, List<GenericStack> outputs, long copies) {
        return legacyData(helper, inputs, outputs, copies, AEItemKey.of(Items.PAPER));
    }

    static CompoundTag legacyData(GameTestHelper helper, KeyCounter[] inputs, List<GenericStack> outputs,
                                  long copies, AEItemKey definition) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag data = new CompoundTag();
        CompoundTag queue = new CompoundTag();
        CompoundTag group = new CompoundTag();
        group.putLong("id", 1L);
        group.put("definition", AEItemKey.CODEC.encodeStart(ops, definition).getOrThrow());
        group.put("outputs", GenericStack.CODEC.listOf().encodeStart(ops, outputs).getOrThrow());
        List<List<GenericStack>> slots = new ArrayList<>();
        for (KeyCounter input : inputs) {
            List<GenericStack> stacks = new ArrayList<>();
            for (var entry : input) stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            slots.add(stacks);
        }
        group.put("inputs", GenericStack.CODEC.listOf().listOf().encodeStart(ops, slots).getOrThrow());
        group.putLong("copies", copies);
        group.putLong("first_age", 0L);
        group.putLong("last_age", 0L);
        ListTag groups = new ListTag();
        groups.add(group);
        queue.putInt("version", 1);
        queue.put("groups", groups);
        data.put("pending_patterns", queue);
        return data;
    }

    static void read(GameTestHelper helper, PendingPatternQueue queue, CompoundTag data) {
        queue.read(data, helper.getLevel().registryAccess());
    }

    private static CompoundTag save(GameTestHelper helper, PendingPatternQueue queue, long tick) {
        CompoundTag saved = new CompoundTag();
        queue.write(saved, helper.getLevel().registryAccess(), tick);
        return saved;
    }

    private static CompoundTag savedGroup(CompoundTag tag) {
        return tag.getCompound("pending_patterns").getList("groups", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static PatternInterfaceBlockEntity place(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        return helper.getBlockEntity(BlockPos.ZERO);
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }

    private static List<GenericStack> outputs() {
        return List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L));
    }

    private static AEItemKey namedIron(String name) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }
}

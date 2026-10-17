package cn.howxu.mmcr;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue.GroupSnapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PendingPatternQueue.OfferLimits;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Native synchronous queue ownership, exact signatures and registry-aware persistence.
 *
 * @author howxu <dev@howxu.cn>
 */
public class PendingPatternQueueGameTest {
    private static final OfferLimits UNBOUNDED_COPIES = new OfferLimits(Long.MAX_VALUE, Long.MAX_VALUE);

    public void mergesOnlyIdenticalSlotTemplates(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey definition = AEItemKey.of(Items.PAPER);
        helper.assertTrue(queue.offer(definition, outputs(), slots(counter(iron, 1L), counter(iron, 1L)),
                UNBOUNDED_COPIES, 10L), "First exact slot template is accepted");
        helper.assertTrue(queue.offer(definition, outputs(), slots(counter(iron, 1L), counter(iron, 1L)),
                UNBOUNDED_COPIES, 14L), "Identical slots merge");
        helper.assertTrue(queue.offer(definition, outputs(), slots(counter(iron, 2L), new KeyCounter()),
                UNBOUNDED_COPIES, 14L), "Equal material totals in different slots form a separate group");
        helper.assertTrue(queue.offer(definition, outputs(), slots(counter(namedIron("component"), 1L), counter(iron, 1L)),
                UNBOUNDED_COPIES, 14L), "Component-different keys form a separate group");
        helper.assertTrue(queue.offer(definition, List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2L)),
                slots(counter(iron, 1L), counter(iron, 1L)), UNBOUNDED_COPIES, 14L),
                "Different promised quantities cannot merge");
        helper.assertTrue(queue.offer(namedIron("definition"), outputs(), slots(counter(iron, 1L), counter(iron, 1L)),
                UNBOUNDED_COPIES, 14L), "Different definitions cannot merge");
        GroupSnapshot first = queue.groups().getFirst();
        helper.assertTrue(queue.groups().size() == 5 && queue.totalCopies() == 6L && first.copies() == 2L,
                "Only the two identical requests share one compressed group");
        helper.assertTrue(first.firstTick() == 10L && first.lastTick() == 14L,
                "Merging preserves the oldest request while advancing the last arrival");
        helper.succeed();
    }

    public void offerOwnsDeepCopies(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        AEItemKey iron = namedIron("owned");
        KeyCounter original = counter(iron, 5L);
        KeyCounter[] inputs = slots(original);
        List<GenericStack> outputs = new ArrayList<>(outputs());
        helper.assertTrue(queue.offer(AEItemKey.of(Items.PAPER), outputs, inputs, UNBOUNDED_COPIES, 1L),
                "Accept a copy without claiming caller counters");
        helper.assertTrue(original.get(iron) == 5L, "Offer does not consume caller-owned counters");
        original.clear();
        inputs[0] = counter(AEItemKey.of(Items.DIAMOND), 99L);
        outputs.clear();
        GroupSnapshot group = queue.groups().getFirst();
        group.inputs(1L)[0].clear();
        helper.assertTrue(group.inputs()[0].get(iron) == 5L && group.outputs().equals(outputs()),
                "Caller mutation and returned mutable counters cannot change owned material or promises");
        boolean outputsReadOnly = false;
        try {
            group.outputs().clear();
        } catch (UnsupportedOperationException exception) {
            outputsReadOnly = true;
        }
        helper.assertTrue(outputsReadOnly, "Output snapshot is immutable");
        helper.succeed();
    }

    public void removeAndRestorePreserveIdentityAndAge(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey definition = AEItemKey.of(Items.PAPER);
        accept(helper, queue, definition, slots(counter(iron, 2L)), 10L);
        accept(helper, queue, definition, slots(counter(iron, 2L)), 14L);
        accept(helper, queue, AEItemKey.of(Items.BOOK), slots(counter(iron, 3L)), 15L);
        GroupSnapshot original = queue.groups().getFirst();
        queue.removeCopies(original.id(), 1L);
        helper.assertTrue(!queue.offer(definition, outputs(), original.inputs(), new OfferLimits(1L, 1L), 16L),
                "Shrunken capacity rejects new work while an unpaid operation is outstanding");
        queue.restoreCopies(original, 1L);
        assertRestored(helper, queue.groups().getFirst(), original);
        queue.removeCopies(original.id(), original.copies());
        helper.assertTrue(queue.groups().size() == 1, "Native removal is immediately visible without a fake transaction");
        queue.restoreCopies(original, original.copies());
        assertRestored(helper, queue.groups().getFirst(), original);
        helper.assertTrue(queue.totalCopies() == 3L && queue.groups().getLast().definition().equals(AEItemKey.of(Items.BOOK)),
                "Full restoration retains the original group order and every other owned request");
        helper.succeed();
    }

    public void overflowRejectsWithoutMutation(GameTestHelper helper) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey copper = AEItemKey.of(Items.COPPER_INGOT);
        AEItemKey definition = AEItemKey.of(Items.PAPER);
        var queue = new PendingPatternQueue();
        accept(helper, queue, definition, slots(counter(iron, Long.MAX_VALUE)), 1L);
        List<GroupSnapshot> original = queue.groups();
        helper.assertTrue(!queue.offer(definition, outputs(), slots(counter(iron, Long.MAX_VALUE)), UNBOUNDED_COPIES, 2L),
                "Multiplication overflow cannot merge");
        helper.assertTrue(!queue.offer(AEItemKey.of(Items.BOOK), outputs(), slots(counter(iron, 1L)), UNBOUNDED_COPIES, 2L),
                "Same key cannot overflow across distinct groups");
        helper.assertTrue(queue.groups().equals(original), "Rejected synchronous offers leave the exact previous snapshots");
        var acrossSlots = new PendingPatternQueue();
        helper.assertTrue(!acrossSlots.offer(definition, outputs(), slots(counter(iron, Long.MAX_VALUE), counter(iron, 1L)),
                UNBOUNDED_COPIES, 1L) && acrossSlots.isEmpty(), "Cross-slot overflow owns nothing");
        helper.assertTrue(queue.offer(AEItemKey.of(Items.BOOK), outputs(), slots(counter(copper, Long.MAX_VALUE)),
                UNBOUNDED_COPIES, 2L), "Unrelated keys each retain their own exact long range");
        var promised = new PendingPatternQueue();
        helper.assertTrue(promised.offer(definition, List.of(new GenericStack(copper, Long.MAX_VALUE)),
                slots(counter(iron, 1L)), UNBOUNDED_COPIES, 1L), "An exact long output promise is accepted");
        helper.assertTrue(!promised.offer(AEItemKey.of(Items.BOOK), List.of(new GenericStack(copper, 1L)),
                slots(counter(iron, 1L)), UNBOUNDED_COPIES, 2L) && promised.totalCopies() == 1L,
                "Output overflow across groups cannot add material ownership");
        var maxCopies = compressed(helper, slots(counter(iron, 1L)), outputs(), Long.MAX_VALUE);
        GroupSnapshot maxGroup = maxCopies.groups().getFirst();
        helper.assertTrue(!maxCopies.offer(maxGroup.definition(), outputs(), slots(counter(iron, 1L)), UNBOUNDED_COPIES, 101L),
                "Copy count itself cannot overflow when merging");
        helper.assertTrue(!maxCopies.offer(definition, outputs(), slots(counter(copper, 1L)), UNBOUNDED_COPIES, 101L),
                "Total copies cannot overflow across different groups");
        helper.assertTrue(maxCopies.totalCopies() == Long.MAX_VALUE && maxCopies.groups().size() == 1
                        && maxGroup.inputs(Long.MAX_VALUE)[0].get(iron) == Long.MAX_VALUE,
                "Copy-overflow rejection preserves the exact valid long-sized compressed group");
        helper.succeed();
    }

    public void capacityAndDistinctKeyLimitsPreserveExistingWork(GameTestHelper helper) {
        AEItemKey definition = AEItemKey.of(Items.PAPER);
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        var bounded = new PendingPatternQueue();
        for (int index = 0; index < 64; index++) {
            helper.assertTrue(bounded.offer(definition, outputs(), slots(counter(iron, index + 1L)), UNBOUNDED_COPIES, 1L),
                    "Different legal templates fill distinct groups");
        }
        helper.assertTrue(!bounded.offer(definition, outputs(), slots(counter(iron, 65L)), UNBOUNDED_COPIES, 1L),
                "A new group cannot bypass the group bound");
        helper.assertTrue(bounded.offer(definition, outputs(), slots(counter(iron, 1L)), UNBOUNDED_COPIES, 2L),
                "Existing groups may still merge at the distinct-group bound");
        helper.assertTrue(!bounded.offer(definition, outputs(), slots(counter(iron, 1L)), new OfferLimits(1L, 1L), 2L)
                        && bounded.totalCopies() == 65L && bounded.groups().getFirst().copies() == 2L,
                "Capacity shrink rejects without truncating accepted work");
        KeyCounter manyKeys = new KeyCounter();
        for (int index = 0; index < 256; index++) manyKeys.add(namedIron("key-" + index), 1L);
        var keys = new PendingPatternQueue();
        accept(helper, keys, definition, slots(manyKeys), 1L);
        helper.assertTrue(!keys.offer(definition, outputs(), slots(counter(namedIron("excess"), 1L)), UNBOUNDED_COPIES, 2L),
                "A new component key cannot bypass the distinct-key bound");
        keys.removeCopies(keys.groups().getFirst().id(), 1L);
        helper.assertTrue(keys.offer(definition, outputs(), slots(counter(namedIron("excess"), 1L)), UNBOUNDED_COPIES, 2L),
                "Removing owned inputs releases key capacity synchronously");
        var copies = new PendingPatternQueue();
        var limits = new OfferLimits(2L, 1L);
        helper.assertTrue(copies.offer(definition, outputs(), slots(counter(iron, 1L)), limits, 1L), "First group fits");
        helper.assertTrue(!copies.offer(definition, outputs(), slots(counter(iron, 1L)), limits, 1L),
                "Group bound is independent of total free capacity");
        helper.assertTrue(copies.offer(definition, outputs(), slots(counter(iron, 2L)), limits, 1L), "Distinct group fits");
        helper.assertTrue(!copies.offer(definition, outputs(), slots(counter(iron, 3L)), limits, 1L),
                "A new group cannot bypass total copy capacity");
        helper.succeed();
    }

    public void largeCompressedGroupCanSplitAndRestore(GameTestHelper helper) {
        long copies = 2_147_483_647L;
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        var queue = compressed(helper, slots(counter(iron, 5L)), outputs(), copies);
        GroupSnapshot original = queue.groups().getFirst();
        queue.removeCopies(original.id(), copies - 3L);
        helper.assertTrue(queue.groups().getFirst().copies() == 3L
                        && queue.groups().getFirst().inputs(3L)[0].get(iron) == 15L,
                "Splitting a compressed long group does not expand per-request state");
        queue.restoreCopies(original, copies - 3L);
        assertRestored(helper, queue.groups().getFirst(), original);
        queue.removeCopies(original.id(), copies - 3L);
        helper.assertTrue(queue.groups().getFirst().copies() == 3L && original.copies() == copies
                        && original.inputs(copies)[0].get(iron) == copies * 5L,
                "Native partial removal does not mutate previously returned snapshots");
        helper.succeed();
    }

    public void oldSaveHasEmptyPendingQueue(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        accept(helper, queue, AEItemKey.of(Items.PAPER), slots(counter(AEItemKey.of(Items.IRON_INGOT), 1L)), 1L);
        CompoundTag legacy = new CompoundTag();
        legacy.putString("unrelated", "untouched");
        queue.read(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(queue.isEmpty() && queue.groups().isEmpty() && queue.totalCopies() == 0L
                        && legacy.getString("unrelated").equals("untouched"),
                "Legacy saves load an empty queue without changing unrelated fields");
        helper.succeed();
    }

    public void pendingQueueRoundTripPreservesComponentsAndLongCounts(GameTestHelper helper) {
        AEItemKey iron = namedIron("component-input");
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        KeyCounter mixed = counter(iron, 3L);
        mixed.add(water, 1_000L);
        List<GenericStack> promises = List.of(new GenericStack(namedIron("component-output"), 2L));
        long copies = 2_147_483_647L;
        var original = compressed(helper, slots(mixed, counter(iron, 5L)), promises, copies);
        var restored = load(helper, save(helper, original, 110L));
        restored.rebaseClock(5_000_000L);
        GroupSnapshot group = restored.groups().getFirst();
        KeyCounter[] material = group.inputs(copies);
        helper.assertTrue(group.copies() == copies && material.length == 2
                        && material[0].get(iron) == 3L * copies && material[0].get(water) == 1_000L * copies
                        && material[1].get(iron) == 5L * copies,
                "Persistence keeps resource families, component keys, slot distribution and long counts");
        helper.assertTrue(group.definition().equals(original.groups().getFirst().definition())
                        && group.outputs().equals(promises) && group.id() == original.groups().getFirst().id(),
                "Definition components, promises and group identity survive a complete round trip");
        helper.succeed();
    }

    public void reloadKeepsAgeWithoutOfflineAdvance(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        AEItemKey definition = AEItemKey.of(Items.PAPER);
        KeyCounter[] inputs = slots(counter(AEItemKey.of(Items.IRON_INGOT), 1L));
        accept(helper, queue, definition, inputs, 100L);
        accept(helper, queue, definition, inputs, 104L);
        CompoundTag saved = save(helper, queue, 110L);
        var restored = load(helper, saved);
        helper.assertTrue(save(helper, restored, 1_000_000L).equals(saved), "Saving before rebase preserves saved ages");
        helper.assertTrue(!restored.offer(definition, outputs(), inputs, UNBOUNDED_COPIES, 1_000_000L),
                "Admission cannot use an unreconciled clock");
        restored.rebaseClock(1_000_000L);
        GroupSnapshot group = restored.groups().getFirst();
        helper.assertTrue(group.firstTick() == 999_990L && group.lastTick() == 999_994L,
                "Reload never counts offline elapsed time");
        restored.rebaseClock(1_000_100L);
        helper.assertTrue(restored.groups().getFirst().firstTick() == group.firstTick(), "Rebasing occurs once");
        restored.removeCopies(group.id(), 1L);
        helper.assertTrue(restored.groups().getFirst().firstTick() == group.firstTick(),
                "Partial execution never refreshes the oldest request");
        helper.succeed();
    }

    public void damagedOrFutureDataIsPreservedAndBlocked(GameTestHelper helper) {
        var queue = new PendingPatternQueue();
        accept(helper, queue, AEItemKey.of(Items.PAPER), slots(counter(AEItemKey.of(Items.IRON_INGOT), 1L)), 1L);
        CompoundTag valid = save(helper, queue, 2L);
        List<CompoundTag> damaged = new ArrayList<>();
        CompoundTag future = valid.copy();
        future.getCompound("pending_patterns").putInt("version", 99);
        damaged.add(future);
        CompoundTag zeroCopies = valid.copy();
        savedGroup(zeroCopies).putLong("copies", 0L);
        damaged.add(zeroCopies);
        CompoundTag unknownDefinition = valid.copy();
        savedGroup(unknownDefinition).putString("definition", "unknown-definition");
        damaged.add(unknownDefinition);
        CompoundTag unknownResource = valid.copy();
        savedGroup(unknownResource).getList("inputs", Tag.TAG_LIST).getList(0).getCompound(0)
                .putString("#t", "mmcr:unknown_resource_family");
        damaged.add(unknownResource);
        CompoundTag unknownOutput = valid.copy();
        savedGroup(unknownOutput).getList("outputs", Tag.TAG_COMPOUND).getCompound(0)
                .putString("#t", "mmcr:unknown_output_family");
        damaged.add(unknownOutput);
        CompoundTag overflow = valid.copy();
        savedGroup(overflow).putLong("copies", Long.MAX_VALUE);
        savedGroup(overflow).getList("outputs", Tag.TAG_COMPOUND).getCompound(0).putLong("#", 2L);
        damaged.add(overflow);
        CompoundTag scalar = new CompoundTag();
        scalar.putString("pending_patterns", "opaque-owned-data");
        damaged.add(scalar);
        for (CompoundTag source : damaged) {
            CompoundTag expected = source.copy();
            PendingPatternQueue restored = load(helper, source);
            source.putString("pending_patterns", "external mutation after read");
            restored.rebaseClock(1_000L);
            helper.assertTrue(!restored.isEmpty() && restored.groups().isEmpty(),
                    "Unknown ownership is not exposed as an empty usable queue");
            helper.assertTrue(!restored.offer(AEItemKey.of(Items.PAPER), outputs(),
                    slots(counter(AEItemKey.of(Items.IRON_INGOT), 1L)), UNBOUNDED_COPIES, 1_000L),
                    "Opaque ownership blocks new admissions");
            helper.assertTrue(save(helper, restored, 2_000L).equals(expected), "Blocked data is preserved in full");
        }
        helper.succeed();
    }

    private static void assertRestored(GameTestHelper helper, GroupSnapshot restored, GroupSnapshot original) {
        helper.assertTrue(restored.id() == original.id() && restored.copies() == original.copies()
                        && restored.firstTick() == original.firstTick() && restored.lastTick() == original.lastTick()
                        && restored.definition().equals(original.definition()) && restored.outputs().equals(original.outputs()),
                "Unpaid restoration keeps exact identity, count, ages and promises");
        KeyCounter[] expected = original.inputs(original.copies());
        KeyCounter[] actual = restored.inputs(restored.copies());
        helper.assertTrue(actual.length == expected.length, "Restoration retains slot distribution");
        for (int slot = 0; slot < expected.length; slot++) {
            for (var entry : expected[slot]) {
                helper.assertTrue(actual[slot].get(entry.getKey()) == entry.getLongValue(),
                        "Restoration retains exact material ownership");
            }
        }
    }

    private static void accept(GameTestHelper helper, PendingPatternQueue queue, AEItemKey definition,
                               KeyCounter[] inputs, long tick) {
        helper.assertTrue(queue.offer(definition, outputs(), inputs, UNBOUNDED_COPIES, tick), "Accept complete fixture");
    }

    /** Build an extreme fixture through a real persisted quantity, not billions of offers. */
    private static PendingPatternQueue compressed(GameTestHelper helper, KeyCounter[] inputs,
                                                   List<GenericStack> outputs, long copies) {
        var queue = new PendingPatternQueue();
        helper.assertTrue(queue.offer(namedIron("definition-components"), outputs, inputs, UNBOUNDED_COPIES, 100L),
                "Seed fixture with an accepted template");
        CompoundTag saved = save(helper, queue, 100L);
        savedGroup(saved).putLong("copies", copies);
        PendingPatternQueue restored = load(helper, saved);
        restored.rebaseClock(100L);
        helper.assertTrue(restored.groups().size() == 1, "Compressed fixture decodes as one executable group");
        return restored;
    }

    private static CompoundTag savedGroup(CompoundTag tag) {
        return tag.getCompound("pending_patterns").getList("groups", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag save(GameTestHelper helper, PendingPatternQueue queue, long tick) {
        CompoundTag output = new CompoundTag();
        queue.write(output, helper.getLevel().registryAccess(), tick);
        return output;
    }

    private static PendingPatternQueue load(GameTestHelper helper, CompoundTag tag) {
        var queue = new PendingPatternQueue();
        queue.read(tag, helper.getLevel().registryAccess());
        return queue;
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return counter;
    }

    private static KeyCounter[] slots(KeyCounter... slots) {
        return slots;
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

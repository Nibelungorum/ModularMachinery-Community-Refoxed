package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unpaid inputs owned by this interface, never a general-purpose inventory capability.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class HostedPatternInventory {
    public static final String SAVE_KEY = "hosted_patterns";
    private static final long STOPPED = Long.MIN_VALUE;
    private final Map<AEItemKey, Group> groups = new LinkedHashMap<>();
    private final Runnable changed;
    private long revision;
    private boolean relativeClock;
    private boolean refunding;

    public HostedPatternInventory(Runnable changed) { this.changed = changed; }
    public boolean isEmpty() { return groups.isEmpty(); }
    public long revision() { return revision; }
    public List<Group> groups() { return List.copyOf(groups.values()); }
    public Group group(AEItemKey definition) { return groups.get(definition); }

    public enum Gate { FIRST, OPEN, REFUND }

    /** Immutable snapshot: object identity also fences a stale probe, including changes during native callbacks.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public record Group(AEItemKey definition, List<GenericStack> outputs, Map<AEKey, Long> materials,
                        Gate gate, long lastDelivery, long eligibleSince, long ageCredit) {
        public KeyCounter[] requestCounters() {
            KeyCounter counter = new KeyCounter();
            materials.forEach(counter::add);
            return new KeyCounter[]{counter};
        }
        public long eligibleAge(long now) {
            return eligibleSince == STOPPED ? ageCredit : ageCredit + Math.min(20, Math.max(0, now - eligibleSince));
        }
        private Group withMaterials(Map<AEKey, Long> next) {
            return new Group(definition, outputs, Map.copyOf(next), gate, lastDelivery, eligibleSince, ageCredit);
        }
    }

    public boolean canAccept(AEItemKey definition) {
        Group group = groups.get(definition);
        return !refunding && (group == null || group.gate == Gate.OPEN);
    }

    public boolean accept(IPatternDetails pattern, KeyCounter[] inputs, long now, Actionable mode) {
        rebaseClock(now);
        AEItemKey definition = pattern.getDefinition();
        if (!canAccept(definition)) return false;
        Group old = groups.get(definition);
        Map<AEKey, Long> totals = old == null ? new LinkedHashMap<>() : new LinkedHashMap<>(old.materials);
        boolean delivered = false;
        for (KeyCounter input : inputs) {
            for (var entry : input) {
                if (entry.getLongValue() <= 0) return false;
                totals.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                delivered = true;
            }
        }
        if (!delivered) return false;
        if (mode == Actionable.SIMULATE) return true;
        // Validate the whole delivery first; publish both owners before any listener can save or reenter.
        groups.put(definition, new Group(definition, old == null ? List.copyOf(pattern.getOutputs()) : old.outputs,
                Map.copyOf(totals), old == null ? Gate.FIRST : old.gate, now,
                old == null ? STOPPED : old.eligibleSince, old == null ? 0 : old.ageCredit));
        for (KeyCounter input : inputs) input.clear();
        revision++;
        changed.run();
        return true;
    }

    /** Running a probe never reserves materials. Pause the clock when no lane can use the remainder. */
    public Group eligibility(Group group, boolean eligible, long now) {
        if (groups.get(group.definition) != group || group.gate == Gate.REFUND) return group;
        if (eligible == (group.eligibleSince != STOPPED)) return group;
        Group next = new Group(group.definition, group.outputs, group.materials, eligible ? Gate.OPEN : group.gate,
                group.lastDelivery, eligible ? now : STOPPED, Math.min(20, group.eligibleAge(now)));
        groups.put(group.definition, next);
        changed.run();
        return next;
    }

    public void suspend(Group group, long now) {
        if (groups.get(group.definition) == group && group.gate == Gate.OPEN) {
            groups.put(group.definition, new Group(group.definition, group.outputs, group.materials,
                    Gate.FIRST, group.lastDelivery, STOPPED, Math.min(20, group.eligibleAge(now))));
            changed.run();
        }
    }

    public void consume(Group expected, KeyCounter consumed, Actionable mode) {
        if (groups.get(expected.definition) != expected || expected.gate != Gate.OPEN) {
            throw new IllegalStateException("Hosted materials changed before payment");
        }
        Map<AEKey, Long> next = new LinkedHashMap<>(expected.materials);
        for (var entry : consumed) {
            long amount = entry.getLongValue();
            long balance = next.getOrDefault(entry.getKey(), 0L);
            if (amount <= 0 || amount > balance) throw new IllegalStateException("Invalid hosted debit");
            if (amount == balance) next.remove(entry.getKey());
            else next.put(entry.getKey(), balance - amount);
        }
        if (mode == Actionable.MODULATE) {
            replace(expected, next);
            revision++;
            changed.run();
        }
    }

    private void replace(Group group, Map<AEKey, Long> next) {
        if (next.isEmpty()) groups.remove(group.definition);
        else groups.put(group.definition, group.withMaterials(next));
    }

    public void retireAll() { retire(groups.keySet()); }

    public void retire(Set<AEItemKey> removed) {
        for (Group group : groups()) {
            if (group.gate != Gate.REFUND && removed.contains(group.definition)) {
                groups.put(group.definition, new Group(group.definition, group.outputs, group.materials,
                        Gate.REFUND, group.lastDelivery, STOPPED, 0));
                changed.run();
            }
        }
    }

    /** MEStorage has no rollback. Record each successful insertion immediately, never in an abortable root. */
    public void refund(MEStorage storage, IActionSource source, boolean dismantling) {
        if (refunding) return;
        refunding = true;
        try {
            for (Group group : groups()) {
                if (!dismantling && group.gate != Gate.REFUND) continue;
                for (var entry : group.materials.entrySet()) {
                    long accepted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
                    if (accepted == 0) continue;
                    Group current = groups.get(group.definition);
                    Map<AEKey, Long> next = new LinkedHashMap<>(current.materials);
                    if (accepted == entry.getValue()) next.remove(entry.getKey());
                    else next.put(entry.getKey(), entry.getValue() - accepted);
                    replace(current, next);
                    changed.run();
                }
            }
        } finally {
            if (dismantling) groups.clear();
            refunding = false;
        }
    }

    public void write(CompoundTag out, HolderLookup.Provider registries, long now) {
        if (groups.isEmpty()) return;
        if (relativeClock) now = 0;
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var entries = new ListTag();
        out.put(SAVE_KEY, entries);
        for (Group group : groups.values()) {
            var tag = new CompoundTag();
            entries.add(tag);
            tag.put("definition", AEItemKey.CODEC.encodeStart(ops, group.definition).getOrThrow());
            tag.put("outputs", GenericStack.CODEC.listOf().encodeStart(ops, group.outputs).getOrThrow());
            tag.put("materials", GenericStack.CODEC.listOf().encodeStart(ops, group.materials.entrySet().stream()
                    .map(e -> new GenericStack(e.getKey(), e.getValue())).toList()).getOrThrow());
            tag.putString("gate", group.gate.name());
            tag.putLong("quiet", Math.min(5, Math.max(0, now - group.lastDelivery)));
            tag.putLong("eligible_age", Math.min(20, group.eligibleAge(now)));
        }
    }

    public void read(CompoundTag in, HolderLookup.Provider registries) {
        groups.clear();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var entries = in.getList(SAVE_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            var tag = entries.getCompound(index);
            var definition = AEItemKey.CODEC.parse(ops, tag.get("definition")).getOrThrow();
            var outputs = GenericStack.CODEC.listOf().parse(ops, tag.get("outputs")).getOrThrow();
            Map<AEKey, Long> materials = new LinkedHashMap<>();
            for (var stack : GenericStack.CODEC.listOf().parse(ops, tag.get("materials")).getOrThrow()) {
                if (stack.amount() <= 0 || materials.putIfAbsent(stack.what(), stack.amount()) != null) {
                    throw new IllegalArgumentException("Invalid hosted material balance");
                }
            }
            Group group = new Group(definition, List.copyOf(outputs), Map.copyOf(materials),
                    tag.contains("gate") ? Gate.valueOf(tag.getString("gate")) : Gate.FIRST, -tag.getLong("quiet"),
                    STOPPED, tag.getLong("eligible_age"));
            if (materials.isEmpty() || outputs.isEmpty() || groups.putIfAbsent(definition, group) != null) {
                throw new IllegalArgumentException("Invalid hosted pattern group");
            }
        }
        relativeClock = true;
        revision++;
    }

    public void rebaseClock(long now) {
        if (!relativeClock) return;
        groups.replaceAll((key, g) -> new Group(key, g.outputs, g.materials, g.gate,
                now + g.lastDelivery, STOPPED, g.ageCredit));
        relativeClock = false;
    }
}

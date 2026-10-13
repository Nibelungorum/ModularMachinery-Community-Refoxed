package cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.mixin.compat.appliedenergistics2.GenericStackInvAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Native return slots followed by 256 hidden, long-sized overflow slots.
 * AE2 retains ownership of network injection, persistence and crafting settlement.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternReturnInventory extends PatternProviderReturnInventory {
    private static final int OVERFLOW_SLOTS = 256;
    private static final int MAX_DROPPED_STACKS_PER_SLOT = 1024;
    private final int nativeSlots;

    public PatternReturnInventory(Runnable listener) {
        super(listener);
        nativeSlots = size();
        ((GenericStackInvAccessor) (Object) this).mmcr$setStacks(Arrays.copyOf(stacks, nativeSlots + OVERFLOW_SLOTS));
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    public static long slotCapacity(GenericStackInv inventory, int slot, @Nullable AEKey key, AEKeyType type) {
        if (inventory instanceof PatternReturnInventory returns) return returns.slotCapacity(slot, key, type);
        return key == null ? inventory.getCapacity(type) : inventory.getMaxAmount(key);
    }

    public static long slotLimit(GenericStackInv inventory, int slot, @Nullable AEKey key, AEKeyType type) {
        if (inventory instanceof PatternReturnInventory returns) return returns.slotLimit(slot, key, type);
        return key == null ? inventory.getCapacity(type) : inventory.getMaxAmount(key);
    }

    private long slotLimit(int slot, @Nullable AEKey key, AEKeyType type) {
        return slot < nativeSlots ? key == null ? super.getCapacity(type) : super.getMaxAmount(key) : Long.MAX_VALUE;
    }

    private long slotCapacity(int slot, @Nullable AEKey key, AEKeyType type) {
        long maximum = slotLimit(slot, key, type);
        if (key == null) return maximum;
        long budget = Long.MAX_VALUE;
        for (int index = 0; index < size(); index++) {
            if (index != slot && key.equals(getKey(index))) budget -= Math.min(budget, getAmount(index));
        }
        return Math.min(maximum, budget);
    }

    public long remainingCapacity(AEKey key) {
        long remaining = Long.MAX_VALUE;
        for (GenericStack stack : stacks) {
            if (stack != null && key.equals(stack.what())) remaining -= Math.min(remaining, stack.amount());
        }
        return remaining;
    }

    /** Validates the whole new projection before applying it, independent of the previous snapshot's balances. */
    public void replaceProjection(AEKeyType type, GenericStack[] projection) {
        if (projection.length != size()) throw new IllegalArgumentException("Invalid pattern return projection size");
        Map<AEKey, Long> totals = new HashMap<>();
        for (int slot = 0; slot < size(); slot++) {
            GenericStack stack = projection[slot];
            if (stack == null) continue;
            long previous = totals.getOrDefault(stack.what(), 0L);
            if (!type.equals(stack.what().getType()) || !isAllowedIn(slot, stack.what()) || stack.amount() <= 0L
                    || stack.amount() > slotLimit(slot, stack.what(), type) || stack.amount() > Long.MAX_VALUE - previous) {
                throw new IllegalArgumentException("Invalid pattern return projection balance");
            }
            totals.put(stack.what(), previous + stack.amount());
        }
        beginBatch();
        try {
            for (int slot = 0; slot < size(); slot++) {
                AEKey current = getKey(slot);
                if (projection[slot] != null || current != null && type.equals(current.getType())) {
                    super.setStack(slot, projection[slot]);
                }
            }
        } finally {
            endBatch();
        }
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        if (stack != null) {
            if (stack.amount() < 0L) throw new IllegalArgumentException("Pattern return amount must be non-negative");
            long amount = Math.min(stack.amount(), slotCapacity(slot, stack.what(), stack.what().getType()));
            stack = amount == 0L ? null : new GenericStack(stack.what(), amount);
        }
        super.setStack(slot, stack);
    }

    @Override
    public long insert(int slot, AEKey key, long amount, Actionable mode) {
        Objects.requireNonNull(key, "key");
        if (amount < 0L) throw new IllegalArgumentException("Pattern return amount must be non-negative");
        if (!canInsert() || !isAllowedIn(slot, key)) return 0L;
        AEKey current = getKey(slot);
        if (current != null && !current.equals(key)) return 0L;
        long currentAmount = getAmount(slot);
        long maximum = slotCapacity(slot, key, key.getType());
        long inserted = Math.min(amount, Math.max(0L, maximum - currentAmount));
        if (inserted > 0L && mode == Actionable.MODULATE) setStack(slot, new GenericStack(key, currentAmount + inserted));
        return inserted;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount < 0L) throw new IllegalArgumentException("Pattern return amount must be non-negative");
        return super.insert(key, Math.min(amount, remainingCapacity(key)), mode, source);
    }

    @Override
    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (GenericStack stack : stacks) {
            if (stack != null) addDrops(stack, drops, level, pos);
        }
    }

    /** Applies the same bounded destruction policy to owned leftovers not yet settled into the return inventory. */
    public static void addDrops(GenericStack stack, List<ItemStack> drops, Level level, BlockPos pos) {
        if (stack.what() instanceof AEItemKey item) {
            int stackLimit = item.getMaxStackSize();
            long dropped = Math.min(stack.amount(), (long) stackLimit * MAX_DROPPED_STACKS_PER_SLOT);
            for (long remaining = dropped; remaining > 0L;) {
                int count = (int) Math.min(remaining, stackLimit);
                drops.add(item.toStack(count));
                remaining -= count;
            }
            if (stack.amount() > dropped) {
                MMCR.LOG.warn("Discarding {} item(s) from {} after bounded pattern return drop",
                        stack.amount() - dropped, item);
            }
        } else {
            stack.what().addDrops(stack.amount(), drops, level, pos);
        }
    }
}

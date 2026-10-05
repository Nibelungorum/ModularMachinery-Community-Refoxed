package cn.howxu.mmcr.compat.botania;

import java.util.Objects;

/**
 * One bounded physical mana store shared by native transport and recipe IO.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ManaStorage {
    private final Runnable changed;
    private int amount;

    public ManaStorage(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public int amount() { return amount; }
    public int capacity() { return BotaniaManaIds.CAPACITY; }
    public Object identity() { return this; }

    public long move(long requested, boolean insert, boolean simulate) {
        long available = insert ? (long) capacity() - amount : amount;
        int moved = (int) Math.min(Math.max(0L, requested), available);
        if (moved > 0 && !simulate) {
            amount += insert ? moved : -moved;
            changed.run();
        }
        return moved;
    }

    public void setAmount(long value) {
        int next = (int) Math.min(capacity(), Math.max(0L, value));
        if (next == amount) return;
        amount = next;
        changed.run();
    }
}

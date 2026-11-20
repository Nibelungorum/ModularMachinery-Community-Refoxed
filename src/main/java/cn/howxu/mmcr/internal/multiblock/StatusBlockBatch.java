package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.BlockArray;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Incremental discovery and circular reconciliation cursor for one formation.
 *
 * @author howxu <dev@howxu.cn>
 */
final class StatusBlockBatch {
    private final BlockPos origin;
    final BlockArray pattern;
    private final LongArrayList members = new LongArrayList();
    private Iterator<BlockPos> candidates;
    private final int buildBudget;
    private int cursor;
    private int remaining;
    private boolean target;
    private boolean lastTarget;
    private boolean retired;

    StatusBlockBatch(BlockPos origin, BlockArray pattern, boolean active, boolean discover) {
        this.origin = origin.immutable();
        this.pattern = pattern;
        candidates = discover && !pattern.isEmpty() ? pattern.pattern().keySet().iterator() : null;
        buildBudget = Math.ceilDiv(pattern.pattern().size(), 20);
        target = active;
    }

    void target(boolean active) {
        target = active;
    }

    boolean active() {
        return !retired && target;
    }

    boolean retired() {
        return retired;
    }

    boolean busy() {
        return candidates != null || remaining > 0 || (!members.isEmpty() && target != lastTarget);
    }

    void retire() {
        retired = true;
        candidates = null;
        target = lastTarget = false;
        remaining = members.size();
    }

    static int visitBudget(int size) {
        return Math.ceilDiv(size, size <= 128 ? 1 : size < 1024 ? 5 : 10);
    }

    void step(LongPredicate claim, LongConsumer visit) {
        if (candidates != null) {
            for (int i = 0; i < buildBudget && candidates.hasNext(); i++) {
                long pos = origin.offset(candidates.next()).asLong();
                if (claim.test(pos)) members.add(pos);
            }
            if (!candidates.hasNext()) {
                candidates = null;
                lastTarget = target;
                remaining = members.size();
            }
            return;
        }
        if (lastTarget != target) {
            lastTarget = target;
            remaining = members.size();
        }
        int count = Math.min(remaining, visitBudget(members.size()));
        for (int i = 0; i < count; i++) {
            visit.accept(members.getLong(cursor));
            if (++cursor == members.size()) cursor = 0;
        }
        remaining -= count;
    }
}

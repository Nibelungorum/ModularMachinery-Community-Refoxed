package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongUnaryOperator;

/** Private projections for one query; no reference to a mutable CPU or interface inventory.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
record PatternBulkSizing(Map<AEKey, Long> unit, Map<AEKey, Long> hosted, long maxCopies) {
    static PatternBulkSizing from(KeyCounter[] inputs, Map<AEKey, Long> hosted, long max) {
        Map<AEKey, Long> unit = new HashMap<>();
        try {
            for (var slot : inputs) for (var entry : slot) unit.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
        } catch (ArithmeticException overflow) {
            return new PatternBulkSizing(Map.of(), hosted, 0);
        }
        for (var e : unit.entrySet()) max = Math.min(max, (Long.MAX_VALUE - hosted.getOrDefault(e.getKey(), 0L)) / e.getValue());
        return new PatternBulkSizing(unit, hosted, Math.max(0, max));
    }

    KeyCounter[] projected(long copies) {
        var projected = new KeyCounter();
        hosted.forEach(projected::add);
        if (copies > 0) unit.forEach((key, amount) -> projected.add(key, amount * copies));
        return new KeyCounter[]{projected};
    }

    static long copiesForBest(long cap, LongUnaryOperator payableAt) {
        if (cap <= 0) return 0;
        long best = payableAt.applyAsLong(cap);
        if (best <= 0 || payableAt.applyAsLong(0) >= best) return 0;
        long low = 1, high = cap;
        while (low < high) {
            long mid = low + (high - low) / 2;
            if (payableAt.applyAsLong(mid) >= best) high = mid;
            else low = mid + 1;
        }
        return low;
    }
}

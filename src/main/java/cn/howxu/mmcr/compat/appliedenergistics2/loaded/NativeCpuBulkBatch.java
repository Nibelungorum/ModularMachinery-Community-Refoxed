package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** One synchronous dispatch attempt against the job inventory. Vanilla already extracted the first copy.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class NativeCpuBulkBatch {
    private NativeCpuBulkBatch() {}

    public static long push(ICraftingInventory inventory, KeyCounter[] unit, long remaining, double power,
                            DoubleUnaryOperator simulatePower, KeyCounter outputs, KeyCounter containers,
                            ToLongFunction<AEKey> waiting, LongUnaryOperator quote, Predicate<KeyCounter[]> deliver) {
        Map<AEKey, Long> inputs = new HashMap<>();
        Map<AEKey, Long> returns = new HashMap<>();
        if (!Double.isFinite(power) || power < 0 || !sum(inputs, unit) || inputs.isEmpty() || !sum(returns, outputs, containers)) {
            return deliver.test(unit) ? 1 : 0;
        }
        long limit = remaining;
        for (var e : inputs.entrySet()) {
            long amount = e.getValue();
            limit = Math.min(limit, Long.MAX_VALUE / amount);
            if (limit <= 1) break;
            long extra = inventory.extract(e.getKey(), (limit - 1) * amount, Actionable.SIMULATE);
            limit = Math.min(limit, 1 + extra / amount);
        }
        for (var e : returns.entrySet()) {
            limit = Math.min(limit, (Long.MAX_VALUE - waiting.applyAsLong(e.getKey())) / e.getValue());
        }
        if (limit <= 0) return 0;
        if (limit > 1 && power > 0) {
            double available = simulatePower.applyAsDouble(Math.min(Double.MAX_VALUE, power * limit));
            if (!(available >= power - 0.01)) return 0; // AE's original one-copy tolerance
            if (available < power * limit) {
                // Extra copies must actually be fundable, even for sub-centi-AE fluid inputs.
                // Compare the same double charge AE will debit, including above 2^53.
                long low = 1, high = limit;
                while (low < high) {
                    long mid = high - (high - low) / 2;
                    if (power * mid <= available) low = mid;
                    else high = mid - 1;
                }
                limit = low;
            }
        }
        // Even with only one fundable copy, a zero quote can prevent underfeeding the first-delivery gate.
        long copies = Math.min(limit, quote.applyAsLong(limit));
        if (copies <= 0) return 0;
        if (copies == 1) return deliver.test(unit) ? 1 : 0;

        var batch = new KeyCounter[unit.length];
        for (int i = 0; i < unit.length; i++) {
            batch[i] = new KeyCounter();
            for (var e : unit[i]) batch[i].add(e.getKey(), e.getLongValue() * copies);
        }
        var extracted = new KeyCounter();
        for (var e : inputs.entrySet()) {
            long need = e.getValue() * (copies - 1);
            long got = inventory.extract(e.getKey(), need, Actionable.MODULATE);
            if (got > 0) extracted.add(e.getKey(), got);
            if (got != need) {
                refund(inventory, extracted);
                return 0;
            }
        }
        if (!deliver.test(batch)) {
            refund(inventory, extracted);
            return 0;
        }
        for (var slot : unit) slot.clear();
        scale(outputs, copies);
        scale(containers, copies);
        return copies;
    }

    private static boolean sum(Map<AEKey, Long> totals, KeyCounter... counters) {
        for (var counter : counters) for (var e : counter) {
            long amount = e.getLongValue(), previous = totals.getOrDefault(e.getKey(), 0L);
            if (amount <= 0 || previous > Long.MAX_VALUE - amount) return false;
            totals.put(e.getKey(), previous + amount);
        }
        return true;
    }

    private static void scale(KeyCounter counter, long copies) {
        for (var e : counter) counter.set(e.getKey(), e.getLongValue() * copies);
    }

    private static void refund(ICraftingInventory inventory, KeyCounter extracted) {
        for (var e : extracted) inventory.insert(e.getKey(), e.getLongValue(), Actionable.MODULATE);
    }
}

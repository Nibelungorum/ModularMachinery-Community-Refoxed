package cn.howxu.mmcr.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;

import java.util.List;
import java.util.function.ToLongFunction;

/** An execution-only adapter. Thunderbolt owns extraction, refunds, power and task progress.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public record TianshuBatchProvider(ICraftingProvider provider, BulkPatternProvider bulk,
                                   BatchJobView job, IEnergyService energy) implements IBatchCraftingProvider {
    /** The time wheel also backs non-Tianshu machines.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    public interface Owner {
        boolean mmcr$isTianshu();
    }

    @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
    @Override public boolean isBusy() { return provider.isBusy(); }

    @Override
    public long getBatchCapacity(IPatternDetails pattern) {
        if (isBusy()) return 0;
        long limit = outputHeadroom(pattern, job.waitingFor().list::get);
        // Exact AEProcessingPattern condenses equal keys and has no container remainders.
        for (var input : pattern.getInputs()) limit = Math.min(limit, Long.MAX_VALUE / input.getMultiplier());
        return limit;
    }

    public static long outputHeadroom(IPatternDetails pattern, ToLongFunction<AEKey> waiting) {
        long limit = Long.MAX_VALUE;
        for (var output : pattern.getOutputs()) {
            limit = Math.min(limit, (Long.MAX_VALUE - waiting.applyAsLong(output.what())) / output.amount());
        }
        return limit;
    }

    private long limitByPower(long limit, double perCopy) {
        if (limit <= 1 || perCopy == 0) return limit;
        double wanted = perCopy * limit;
        double available = energy.extractAEPower(Math.min(Double.MAX_VALUE, wanted), Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (!(available >= perCopy - 0.01)) return 0;
        if (available >= wanted) return limit;
        // Thunderbolt tolerates a 0.01 AE shortfall. Only its first copy may use that tolerance.
        long low = 1, high = limit;
        while (low < high) {
            long mid = high - (high - low) / 2;
            if (perCopy * mid <= available) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    @Override
    public long pushBatch(IPatternDetails pattern, KeyCounter[] unit, long offered) {
        // Earlier providers in this visit may already have registered some of the same outputs.
        long headroom = Math.min(offered, outputHeadroom(pattern, job.waitingFor().list::get));
        long cap = limitByPower(headroom, CraftingCpuHelper.calculatePatternPower(unit));
        if (cap <= 0) return offered;
        long copies = Math.min(cap, bulk.suggestPatternCopies(pattern, unit, cap));
        if (copies <= 0) return offered;
        // The executor reuses its one-copy prototype for the next provider, even after acceptance.
        var total = new KeyCounter[unit.length];
        for (int i = 0; i < unit.length; i++) {
            total[i] = new KeyCounter();
            for (var e : unit[i]) total[i].add(e.getKey(), Math.multiplyExact(e.getLongValue(), copies));
        }
        return provider.pushPattern(pattern, total) ? offered - copies : offered;
    }
}

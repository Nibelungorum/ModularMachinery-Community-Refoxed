package cn.howxu.mmcr.api.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.BulkPatternProviderSource;

import java.util.Optional;

/** Read-only, server-thread hints for a standard AE2 processing-pattern delivery.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@FunctionalInterface
public interface BulkPatternProvider {
    /** Unsupported providers must keep their normal AE2 dispatch path. */
    static Optional<BulkPatternProvider> find(ICraftingProvider provider) {
        return provider instanceof BulkPatternProviderSource source
                ? Optional.ofNullable(source.mmcr$bulkPatternProvider()) : Optional.empty();
    }

    /**
     * Returns a suggested number of copies of the provider's original pattern, not recipe operations.
     * Supply the real slot-wise composition of one copy and at most the copies the caller can fund.
     * Zero means no useful delivery now. The counters are not consumed and the quote reserves nothing.
     * Only a successful {@link ICraftingProvider#pushPattern} transfers ownership: the sender must account
     * for all delivered copies, their power and expected outputs, even though it made just one call.
     */
    long suggestPatternCopies(IPatternDetails pattern, KeyCounter[] actualInputsPerCopy, long maxCopies);
}

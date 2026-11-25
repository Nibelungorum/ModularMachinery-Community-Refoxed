package cn.howxu.mmcr.compat.neoecoae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** ECO's linear transaction has already quoted, reserved power and materialized the complete input total.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public record HostedParallelProvider(ICraftingProvider provider) implements ECOParallelCraftingProvider {
    @Override public int eco$getAvailableParallelSlots() { return Integer.MAX_VALUE; }

    @Override
    public boolean eco$pushPatternBatch(IPatternDetails pattern, KeyCounter[] total, long copies, @Nullable UUID job) {
        return provider.pushPattern(pattern, total);
    }
}

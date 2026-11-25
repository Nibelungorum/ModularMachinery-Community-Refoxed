package cn.howxu.mmcr.mixin.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.compat.ae2lt.TianshuBatchProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Resolve only this Tianshu task; never install an adapter in Thunderbolt's global registry or cache.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(value = BatchExecutor.class, remap = false)
public abstract class TianshuBatchExecutorMixin {
    @WrapOperation(method = "runBatchOnly(ILcom/moakiee/thunderbolt/core/crafting/batch/BatchCpuAccounting$Mode;"
            + "Lappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
            + "Lcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;Lappeng/crafting/inv/ListCraftingInventory;"
            + "Ljava/util/Map;Ljava/lang/Runnable;Ljava/util/Map;IJZLcom/moakiee/thunderbolt/core/crafting/batch/TickProviderDispatchSchedule;"
            + "Lcom/moakiee/thunderbolt/api/crafting/batch/BatchProviderAdapter;)Lcom/moakiee/thunderbolt/core/crafting/batch/BatchExecutor$BatchRunResult;",
            at = @At(value = "INVOKE", target =
            "Lcom/moakiee/thunderbolt/core/crafting/batch/BatchProviderResolutionCache;resolve(Lappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;Lcom/moakiee/thunderbolt/api/crafting/batch/BatchJobView;Lcom/moakiee/thunderbolt/api/crafting/batch/BatchProviderAdapter;)Lcom/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider;"))
    private static IBatchCraftingProvider mmcr$resolve(@Coerce Object cache, ICraftingProvider provider,
            IPatternDetails pattern, BatchJobView job, BatchProviderAdapter adapter,
            Operation<IBatchCraftingProvider> original, @Local(argsOnly = true) IEnergyService energy) {
        if (adapter == null && job instanceof TimeWheelJobAccessor view && pattern.getClass() == AEProcessingPattern.class
                && ((BatchTaskHandle) job).details() == pattern
                && ((TianshuBatchProvider.Owner) (Object) view.mmcr$timeWheel()).mmcr$isTianshu()) {
            var bulk = BulkPatternProvider.find(provider).orElse(null);
            if (bulk != null) return new TianshuBatchProvider(provider, bulk, job, energy);
        }
        return original.call(cache, provider, pattern, job, adapter);
    }
}

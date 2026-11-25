package cn.howxu.mmcr.mixin.compat.neoecoae;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProvider;
import cn.dancingsnow.neoecoae.compat.ae2.ECOExternalProviderBlocking;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchMode;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchPlan;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.compat.neoecoae.HostedParallelProvider;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Use ECO's ordinary atomic batch lane, keeping the real provider identity for all CPU accounting.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingProviderDispatcher", remap = false)
public abstract class EcoProviderDispatcherMixin {
    @Unique
    private static @Nullable BulkPatternProvider mmcr$bulk(Object request, ICraftingProvider provider) {
        return ((EcoRequestAccessor) request).mmcr$pattern().getClass() == AEProcessingPattern.class
                ? BulkPatternProvider.find(provider).orElse(null) : null;
    }

    // A scaled execution-pattern wrapper is not the installed pattern. Skip that speculative lane.
    @WrapOperation(method = "dispatchCandidate", at = @At(value = "INVOKE", target =
            "Lcn/dancingsnow/neoecoae/crafting/execution/ECOProcessingPatternDispatcher;supportsScaledDispatchCached(Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingDispatchRequest;Lappeng/api/networking/crafting/ICraftingProvider;)Z"))
    private boolean mmcr$skipScaled(@Coerce Object dispatcher, @Coerce Object request, ICraftingProvider provider,
                                   Operation<Boolean> original) {
        return mmcr$bulk(request, provider) == null && original.call(dispatcher, request, provider);
    }

    @ModifyExpressionValue(method = "tryDispatchOrdinaryBatch", at = @At(value = "INVOKE", target =
            "Lcn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProviders;find(Ljava/lang/Object;)Lcn/dancingsnow/neoecoae/api/me/provider/ECOParallelCraftingProvider;"))
    private ECOParallelCraftingProvider mmcr$parallel(ECOParallelCraftingProvider previous, @Coerce Object request,
                                                     ICraftingProvider provider) {
        return mmcr$bulk(request, provider) != null ? new HostedParallelProvider(provider) : previous;
    }

    @WrapOperation(method = {"tryDispatchOrdinaryBatch", "dispatchCandidate"}, at = @At(value = "INVOKE", target =
            "Lcn/dancingsnow/neoecoae/crafting/execution/ECOBatchDispatchPlanning;plan(Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingDispatchRequest;Lappeng/api/networking/crafting/ICraftingProvider;JJDLappeng/api/networking/energy/IEnergyService;Lcn/dancingsnow/neoecoae/crafting/execution/batch/ECOBatchMode;)Lcn/dancingsnow/neoecoae/crafting/execution/batch/ECOBatchPlan;"), require = 2)
    private ECOBatchPlan mmcr$quote(@Coerce Object request, ICraftingProvider provider, long capacity, long quota,
            double power, IEnergyService energy, ECOBatchMode mode, Operation<ECOBatchPlan> original) {
        var bulk = mmcr$bulk(request, provider);
        // A non-blocking host already had its batch attempt. Never bypass a refusal by pushing one copy.
        if (bulk != null && mode == ECOBatchMode.SINGLE && !ECOExternalProviderBlocking.isEnabled(provider)) return null;
        // Slot counts are ints in ECO's queue API. MMCR's capacity is a long quotation instead.
        long providerLimit = bulk != null && mode == ECOBatchMode.LINEAR ? Long.MAX_VALUE : capacity;
        var plan = original.call(request, provider, providerLimit, quota, power, energy, mode);
        if (bulk == null || plan == null) return plan;
        var unit = ((EcoRequestAccessor) request).mmcr$unit();
        long copies = bulk.suggestPatternCopies(plan.identity().originalPattern(), unit, plan.craftCount());
        if (copies <= 0) return null;
        return copies >= plan.craftCount() ? plan : new ECOBatchPlan(plan.identity(), copies, mode, plan.limits());
    }
}

package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchDiagnostics;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps scaled jobs in the same binding's MMCR-only provider index.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin {
    @Unique
    private long mmcr$nextProviderDiagnosticNanos = System.nanoTime();

    @WrapMethod(method = "submitJob")
    private ICraftingSubmitResult mmcr$traceSubmission(ICraftingPlan plan, ICraftingRequester requester,
                                                      ICraftingCPU target, boolean prioritizePower,
                                                      IActionSource source, Operation<ICraftingSubmitResult> original) {
        PatternBatchDiagnostics.plan("service-submit", target, plan);
        ICraftingSubmitResult result;
        try {
            result = original.call(plan, requester, target, prioritizePower, source);
        } catch (RuntimeException exception) {
            MMCR.LOG.info("[PatternBatchDiag] service-submit-exception: service={} plan={}",
                    System.identityHashCode(this), System.identityHashCode(plan), exception);
            throw exception;
        }
        PatternBatchDiagnostics.result("service-submit-result", target, plan, result);
        return result;
    }

    @ModifyVariable(method = "getProviders", at = @At("HEAD"), argsOnly = true)
    private IPatternDetails mmcr$unitProviderKey(IPatternDetails pattern) {
        var key = PatternBatchPlanning.providerKey(pattern);
        long now = System.nanoTime();
        if (now - mmcr$nextProviderDiagnosticNanos >= 0L) {
            mmcr$nextProviderDiagnosticNanos = now + 1_000_000_000L;
            MMCR.LOG.info("[PatternBatchDiag] provider-lookup: service={} normalized={} {} route={}",
                    System.identityHashCode(this), key != pattern, PatternBatchDiagnostics.pattern(pattern),
                    PatternBatchDiagnostics.callers());
        }
        return key;
    }
}

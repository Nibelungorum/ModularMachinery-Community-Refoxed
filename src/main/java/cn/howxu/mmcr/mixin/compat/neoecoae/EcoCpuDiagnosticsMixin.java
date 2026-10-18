package cn.howxu.mmcr.mixin.compat.neoecoae;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchDiagnostics;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Targets ECO's live execution class, not the legacy no-op compatibility anchors.
 * @author howxu <dev@howxu.cn>
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", remap = false)
public abstract class EcoCpuDiagnosticsMixin {
    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult mmcr$traceSubmission(IGrid grid, ICraftingPlan plan, IActionSource source,
                                                      ICraftingRequester requester, Operation<ICraftingSubmitResult> original) {
        return PatternBatchDiagnostics.submit(this, grid, plan, source, requester, original);
    }
}

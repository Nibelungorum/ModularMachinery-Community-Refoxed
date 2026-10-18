package cn.howxu.mmcr.mixin.compat.ae2lt;

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

/** Observes the independent Tianshu CPU without depending on its implementation classes.
 * @author howxu <dev@howxu.cn>
 */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic", remap = false)
public abstract class TimeWheelCpuDiagnosticsMixin {
    @WrapMethod(method = "trySubmitJob")
    private ICraftingSubmitResult mmcr$traceSubmission(IGrid grid, ICraftingPlan plan, IActionSource source,
                                                      ICraftingRequester requester, Operation<ICraftingSubmitResult> original) {
        return PatternBatchDiagnostics.submit(this, grid, plan, source, requester, original);
    }
}

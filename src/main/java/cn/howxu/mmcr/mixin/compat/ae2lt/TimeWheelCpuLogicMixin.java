package cn.howxu.mmcr.mixin.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.compat.ae2lt.TianshuBatchProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU;
import com.moakiee.ae2lt.logic.tianshu.TianshuCraftingCpuHost;
import com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** A failed batch must not sneak one copy past the same first-delivery gate.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(value = Ae2LtTimeWheelCraftingCpuLogic.class, remap = false)
public abstract class TimeWheelCpuLogicMixin implements TianshuBatchProvider.Owner {
    @Shadow @Final private TimeWheelCraftingCPU cpu;
    @Shadow public abstract long getWaitingFor(AEKey key);

    @Override public boolean mmcr$isTianshu() { return cpu.getHost() instanceof TianshuCraftingCpuHost; }

    @WrapOperation(method = {"pushBulkForTask", "pushOnePattern"}, at = @At(value = "INVOKE", target =
            "Lcom/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic;tryPushPattern(Lcom/moakiee/ae2lt/crafting/timewheel/Ae2LtTimeWheelCraftingCpuLogic$ResolvedProvider;[Lappeng/api/stacks/KeyCounter;Lcom/moakiee/thunderbolt/core/crafting/batch/TickProviderDispatchSchedule;)Z"), require = 2)
    private boolean mmcr$checkSingle(Ae2LtTimeWheelCraftingCpuLogic logic, @Coerce Object resolved, KeyCounter[] unit,
            TickProviderDispatchSchedule schedule, Operation<Boolean> original,
            @Local(argsOnly = true) IPatternDetails pattern, @Local ICraftingProvider provider) {
        if (mmcr$isTianshu() && pattern.getClass() == AEProcessingPattern.class) {
            var bulk = BulkPatternProvider.find(provider).orElse(null);
            if (bulk != null && (TianshuBatchProvider.outputHeadroom(pattern, this::getWaitingFor) < 1
                    || bulk.suggestPatternCopies(pattern, unit, 1) < 1)) {
                schedule.recordFailure(pattern, provider);
                return false;
            }
        }
        return original.call(logic, resolved, unit, schedule);
    }
}

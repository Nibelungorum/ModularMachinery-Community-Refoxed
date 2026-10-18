package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only the immutable plan's task map, preserving AE2's material accounting.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(CraftingSimulationState.class)
public abstract class CraftingSimulationStateMixin {
    @Inject(method = "buildCraftingPlan", at = @At("RETURN"), cancellable = true)
    private static void mmcr$rewritePlan(CraftingSimulationState state, CraftingCalculation calculation,
                                         long calculatedAmount, CallbackInfoReturnable<CraftingPlan> callback) {
        if (!((Object) calculation instanceof PatternBatchPlanning.SnapshotSource source)) {
            MMCR.LOG.info("[PatternBatchDiag] build plan skipped: calculation={} reason=missing-snapshot-mixin",
                    System.identityHashCode(calculation));
            return;
        }
        var plan = callback.getReturnValue();
        MMCR.LOG.info("[PatternBatchDiag] build plan: calculation={} output={} simulation={} tasks={} limits={}",
                System.identityHashCode(calculation), plan.finalOutput(), plan.simulation(), plan.patternTimes().size(),
                source.mmcr$batchSnapshot().limits().size());
        var crafts = PatternBatchPlanning.rewrite(plan.patternTimes(), source.mmcr$batchSnapshot());
        MMCR.LOG.info("[PatternBatchDiag] build plan result: calculation={} changed={} tasks={}",
                System.identityHashCode(calculation), !crafts.equals(plan.patternTimes()), crafts.size());
        callback.setReturnValue(new CraftingPlan(plan.finalOutput(), plan.bytes(), plan.simulation(),
                plan.multiplePaths(), plan.usedItems(), plan.emittedItems(), plan.missingItems(), crafts));
    }
}

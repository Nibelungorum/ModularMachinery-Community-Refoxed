package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.registry.ModDataComponents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Temporary, read-only diagnostics shared by native and optional CPU paths.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchDiagnostics {
    private PatternBatchDiagnostics() { }

    public static ICraftingSubmitResult submit(Object logic, IGrid grid, ICraftingPlan plan,
                                               IActionSource source, ICraftingRequester requester,
                                               Operation<ICraftingSubmitResult> original) {
        MMCR.LOG.info("[PatternBatchDiag] cpu-submit: logic={} instance={} grid={} plan={}",
                logic.getClass().getName(), System.identityHashCode(logic), System.identityHashCode(grid),
                System.identityHashCode(plan));
        plan("cpu-submit", logic, plan);
        ICraftingSubmitResult result;
        try {
            result = original.call(grid, plan, source, requester);
        } catch (RuntimeException exception) {
            MMCR.LOG.info("[PatternBatchDiag] cpu-submit-exception: owner={} plan={}",
                    identity(logic), System.identityHashCode(plan), exception);
            throw exception;
        }
        result("cpu-submit-result", logic, plan, result);
        return result;
    }

    public static void plan(String stage, Object owner, ICraftingPlan plan) {
        if (plan == null) {
            MMCR.LOG.info("[PatternBatchDiag] {}: owner={} plan=null", stage, identity(owner));
            return;
        }
        try {
            var tasks = plan.patternTimes();
            long batchTasks = tasks.keySet().stream().filter(PatternBatchDetails.class::isInstance).count();
            long scaledTasks = tasks.keySet().stream().filter(pattern -> pattern instanceof PatternBatchDetails batch
                    && batch.data().multiplier() > 1L).count();
            MMCR.LOG.info("[PatternBatchDiag] {}: owner={} plan={} planType={} output={} simulation={} tasks={} batchTasks={} scaledTasks={}",
                    stage, identity(owner), System.identityHashCode(plan), plan.getClass().getName(), plan.finalOutput(),
                    plan.simulation(), tasks.size(), batchTasks, scaledTasks);
            int shown = 0;
            for (var task : tasks.entrySet()) {
                if (shown++ >= 8) break;
                MMCR.LOG.info("[PatternBatchDiag] {} task: plan={} count={} {}", stage,
                        System.identityHashCode(plan), task.getValue(), pattern(task.getKey()));
            }
        } catch (RuntimeException exception) {
            // A third-party diagnostic getter must never block or replace the real transaction.
            MMCR.LOG.info("[PatternBatchDiag] {}: plan={} inspection-failed={}", stage,
                    System.identityHashCode(plan), exception.toString());
        }
    }

    public static void result(String stage, Object owner, ICraftingPlan plan, ICraftingSubmitResult result) {
        try {
            MMCR.LOG.info("[PatternBatchDiag] {}: owner={} plan={} resultType={} success={} error={}",
                    stage, identity(owner), System.identityHashCode(plan), result == null ? null : result.getClass().getName(),
                    result != null && result.successful(), result == null ? null : result.errorCode());
        } catch (RuntimeException exception) {
            MMCR.LOG.info("[PatternBatchDiag] {}: owner={} plan={} result-inspection-failed={}",
                    stage, identity(owner), System.identityHashCode(plan), exception.toString());
        }
    }

    public static String pattern(IPatternDetails pattern) {
        if (pattern == null) return "pattern=null";
        try {
            var definition = pattern.getDefinition();
            var marker = definition == null ? null : definition.get(ModDataComponents.PATTERN_BATCH.get());
            return "patternType=" + pattern.getClass().getName() + " batch="
                    + (pattern instanceof PatternBatchDetails batch ? batch.data() : null)
                    + " definitionBatch=" + marker + " outputs=" + pattern.getOutputs();
        } catch (RuntimeException exception) {
            return "patternType=" + pattern.getClass().getName() + " inspection-failed=" + exception;
        }
    }

    public static String callers() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> frame.getClassName().startsWith("appeng.")
                        || frame.getClassName().startsWith("com.moakiee.")
                        || frame.getClassName().startsWith("cn.dancingsnow.")
                        || frame.getClassName().startsWith("net.pedroksl."))
                .limit(12)
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .toList().toString());
    }

    private static String identity(Object owner) {
        return owner == null ? "auto" : owner.getClass().getName() + "@" + System.identityHashCode(owner);
    }
}

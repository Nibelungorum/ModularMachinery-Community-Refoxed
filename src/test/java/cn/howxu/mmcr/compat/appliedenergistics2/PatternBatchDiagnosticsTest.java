package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchDiagnostics;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Checks that observing a CPU submission never retries or changes its transaction.
 * @author howxu <dev@howxu.cn>
 */
class PatternBatchDiagnosticsTest {
    @Test
    void submissionPreservesOriginalArgumentsResultAndTasks() {
        var tasks = new LinkedHashMap<IPatternDetails, Long>();
        IPatternDetails pattern = new DiagnosticFailurePattern();
        tasks.put(pattern, 12L);
        var plan = new CraftingPlan(null, 0L, false, false, null, null, null, tasks);
        var calls = new AtomicInteger();
        var result = PatternBatchDiagnostics.submit(new Object(), null, plan, null, null, arguments -> {
            calls.incrementAndGet();
            assertThat(arguments).containsExactly(null, plan, null, null);
            assertThat(plan.patternTimes()).isSameAs(tasks).containsEntry(pattern, 12L);
            return CraftingSubmitResult.CPU_BUSY;
        });
        assertThat(result).isSameAs(CraftingSubmitResult.CPU_BUSY);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(tasks).containsOnlyKeys(pattern).containsEntry(pattern, 12L);
    }

    @Test
    void submissionExceptionIsNotSwallowedOrRetried() {
        var plan = new CraftingPlan(null, 0L, false, false, null, null, null, new LinkedHashMap<>());
        var calls = new AtomicInteger();
        var failure = new IllegalStateException("submission failed");
        assertThatThrownBy(() -> PatternBatchDiagnostics.submit(new Object(), null, plan, null, null, arguments -> {
            calls.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void resultInspectionFailureCannotReplaceAnAlreadySubmittedTransaction() {
        var plan = new CraftingPlan(null, 0L, false, false, null, null, null, new LinkedHashMap<>());
        var calls = new AtomicInteger();
        var originalResult = new ICraftingSubmitResult() {
            @Override public CraftingSubmitErrorCode errorCode() { throw new IllegalStateException("unavailable diagnostics"); }
            @Override public Object errorDetail() { return null; }
            @Override public ICraftingLink link() { return null; }
        };
        var result = PatternBatchDiagnostics.submit(new Object(), null, plan, null, null, arguments -> {
            calls.incrementAndGet();
            return originalResult;
        });
        assertThat(result).isSameAs(originalResult);
        assertThat(calls.get()).isEqualTo(1);
    }

    /** A foreign pattern's diagnostic getters must not prevent submitting the original plan.
     * @author howxu <dev@howxu.cn>
     */
    private static final class DiagnosticFailurePattern implements IPatternDetails {
        @Override public AEItemKey getDefinition() { throw new IllegalStateException("definition unavailable"); }
        @Override public IInput[] getInputs() { return new IInput[0]; }
        @Override public List<GenericStack> getOutputs() { return List.of(); }
    }
}

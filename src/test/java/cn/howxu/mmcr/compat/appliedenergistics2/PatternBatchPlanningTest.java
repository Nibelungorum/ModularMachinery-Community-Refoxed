package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning.BatchLimit;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning.Snapshot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchSupport.Safety;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** World-free publication policy tests; actual definitions and AE simulation run as GameTests.
 * @author howxu <dev@howxu.cn>
 */
class PatternBatchPlanningTest {
    @Test
    void heterogeneousProvidersDoNotSumCapacity() {
        var small = new BatchLimit(64, Safety.PROVEN_LINEAR);
        var large = new BatchLimit(256, Safety.PROVEN_LINEAR);
        assertThat(small.merge(large)).isEqualTo(large);
        assertThat(large.merge(small).merge(large)).isEqualTo(large);
    }

    @Test
    void unknownProviderPoisonsItsIdentityRatherThanPretendingToBeSerial() {
        var serial = new BatchLimit(1, Safety.PROVEN_LINEAR);
        var unknown = new BatchLimit(1, Safety.UNPROVEN);
        var parallel = new BatchLimit(256, Safety.PROVEN_LINEAR);
        assertThat(serial.merge(parallel)).isEqualTo(parallel);
        assertThat(unknown.merge(parallel)).isEqualTo(parallel.merge(unknown));
        assertThat(unknown.merge(parallel).safety()).isEqualTo(Safety.UNPROVEN);
        assertThat(unknown.merge(parallel).maxMultiplier()).isEqualTo(parallel.maxMultiplier());
    }

    @Test
    void absentSnapshotPreservesTasksWithoutCallingPatternOrWorldCode() {
        IPatternDetails pattern = new UninspectablePattern();
        var crafts = new LinkedHashMap<IPatternDetails, Long>();
        crafts.put(pattern, 2_147_483_647L);
        var result = PatternBatchPlanning.rewrite(crafts, Snapshot.EMPTY);
        assertThat(result).isEqualTo(crafts).isNotSameAs(crafts);
        result.clear();
        assertThat(crafts).containsExactly(Map.entry(pattern, 2_147_483_647L));
        assertThat(PatternBatchPlanning.providerKey(pattern)).isSameAs(pattern);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class UninspectablePattern implements IPatternDetails {
        @Override public AEItemKey getDefinition() { throw new AssertionError("Unexpected pattern inspection"); }
        @Override public IInput[] getInputs() { throw new AssertionError("Unexpected input callback"); }
        @Override public List<GenericStack> getOutputs() { throw new AssertionError("Unexpected output callback"); }
    }
}

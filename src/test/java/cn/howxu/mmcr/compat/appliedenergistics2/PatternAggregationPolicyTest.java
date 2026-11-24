package cn.howxu.mmcr.compat.appliedenergistics2;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternAggregationPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class PatternAggregationPolicyTest {
    @Test
    void waitsOnlyForAnExecutablePartialLane() {
        assertThat(PatternAggregationPolicy.shouldStart(64, 64, 0, 0)).isTrue();
        assertThat(PatternAggregationPolicy.shouldStart(6, 64, 4, 19)).isFalse();
        assertThat(PatternAggregationPolicy.shouldStart(6, 64, 5, 5)).isTrue();
        assertThat(PatternAggregationPolicy.shouldStart(6, 64, 0, 20)).isTrue();
        assertThat(PatternAggregationPolicy.shouldStart(0, 64, 20, 20)).isFalse();
        assertThat(PatternAggregationPolicy.shouldStart(6, 0, 20, 20)).isFalse();
    }
}

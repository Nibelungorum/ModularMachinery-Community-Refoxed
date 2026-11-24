package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class PatternBulkSizingTest {
    @Test void asksOnlyForCopiesThatIncreasePayableWorkInTheNextLane() {
        LongUnaryOperator empty = n -> Math.min(256, n / 2);
        assertThat(PatternBulkSizing.copiesForBest(1, empty)).isZero();
        assertThat(PatternBulkSizing.copiesForBest(511, empty)).isEqualTo(510);
        assertThat(PatternBulkSizing.copiesForBest(600, empty)).isEqualTo(512);
        assertThat(PatternBulkSizing.copiesForBest(600, n -> Math.min(256, (100 + n) / 2))).isEqualTo(412);
        assertThat(PatternBulkSizing.copiesForBest(600, n -> 256)).isZero();
    }

    @Test void longCountsUseABoundedSearchWithoutSignedOverflow() {
        var calls = new AtomicInteger();
        assertThat(PatternBulkSizing.copiesForBest(Long.MAX_VALUE, n -> {
            calls.incrementAndGet();
            return n < Long.MAX_VALUE - 2 ? 0L : 1L;
        })).isEqualTo(Long.MAX_VALUE - 2);
        assertThat(calls.get()).isLessThanOrEqualTo(66);
    }
}

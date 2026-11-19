package cn.howxu.mmcr.api.recipe.requirement;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class OutputExpectationTest {
    @Test
    void scales_units_and_parallelism_before_rounding_the_expected_yield() {
        var output = OutputExpectation.sample(0.001F, () -> 0D);
        assertThat(output.amount(1L, 1_000L)).isEqualTo(1L);
        assertThat(output.amount(1_000L, 1L)).isEqualTo(1L);
    }

    @Test
    void samples_only_the_fraction_instead_of_gambling_the_whole_batch() {
        assertThat(OutputExpectation.sample(0.001F, () -> Math.nextDown(0.001D)).amount(1L, 1L)).isEqualTo(1L);
        assertThat(OutputExpectation.sample(0.001F, () -> 0.001D).amount(1L, 1L)).isZero();
        assertThat(new OutputExpectation(0.7315D, 0.2D).amount(2L, 100L)).isEqualTo(147L);
        assertThat(new OutputExpectation(0.7315D, 0.4D).amount(2L, 100L)).isEqualTo(146L);
        assertThat(new OutputExpectation(0.5D, 0D).amount(2L, 1L)).isEqualTo(1L);
    }

    @Test
    void single_outputs_follow_the_declared_chance_near_zero_and_one() {
        long rare = 0L;
        long common = 0L;
        for (int index = 0; index < 1_000; index++) {
            double threshold = (index + 0.5D) / 1_000D;
            rare += OutputExpectation.sample(0.001F, () -> threshold).amount(1L, 1L);
            common += OutputExpectation.sample(0.999F, () -> threshold).amount(1L, 1L);
        }
        assertThat(rare).isEqualTo(1L);
        assertThat(common).isEqualTo(999L);
    }

    @Test
    void skips_random_draws_for_impossible_and_certain_outputs() {
        AtomicInteger draws = new AtomicInteger();
        assertThat(OutputExpectation.sample(0F, () -> { draws.incrementAndGet(); return 1D; })
                .amount(10L, 100L)).isZero();
        assertThat(OutputExpectation.sample(1F, () -> { draws.incrementAndGet(); return 0D; })
                .amount(10L, 100L)).isEqualTo(1_000L);
        assertThat(draws).hasValue(0);
    }

    @Test
    void reuses_tail_sample_and_finds_capacity_limited_parallelism() {
        OutputExpectation output = new OutputExpectation(0.25D, 0.5D);
        assertThat(output.amount(1L, 4L)).isEqualTo(1L);
        assertThat(output.maximum(1L, 100L, 10L, false)).isEqualTo(42L);
        assertThat(output.amount(1L, 42L)).isEqualTo(10L);
        assertThat(output.amount(1L, 43L)).isEqualTo(11L);
        assertThat(output.maximum(1L, 100L, 10L, true)).isEqualTo(100L);
        assertThat(output.maximum(1L, 1L, 0L, true)).isEqualTo(1L);
        long previous = 0L;
        for (long parallelism = 1L; parallelism <= 100L; parallelism++) {
            long amount = output.amount(1L, parallelism);
            assertThat(amount).isGreaterThanOrEqualTo(previous);
            previous = amount;
        }
    }

    @Test
    void applies_probability_before_saturation_and_preserves_long_precision() {
        assertThat(new OutputExpectation(0.25D, 0D).amount(Long.MAX_VALUE, 2L))
                .isEqualTo(4_611_686_018_427_387_904L);
        assertThat(new OutputExpectation(0.5D, 0D).amount(Long.MAX_VALUE, 1L))
                .isEqualTo(4_611_686_018_427_387_904L);
        assertThat(new OutputExpectation(0.75D, 0D).amount(Long.MAX_VALUE, Long.MAX_VALUE))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(new OutputExpectation(1D, 0D).amount(Long.MAX_VALUE, 1L)).isEqualTo(Long.MAX_VALUE);
    }
}

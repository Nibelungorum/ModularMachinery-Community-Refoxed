package cn.howxu.mmcr.api.recipe.requirement;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class OutputExpectationTest {
    @Test
    void perturbs_probability_in_both_directions_before_scaling() {
        assertThat(OutputExpectation.sample(0.7F, () -> 0.65D).amount(2L, 100L)).isEqualTo(146L);
        assertThat(OutputExpectation.sample(0.7F, () -> 0.35D).amount(2L, 100L)).isEqualTo(134L);
    }

    @Test
    void samples_only_the_fraction_instead_of_gambling_the_whole_batch() {
        assertThat(new OutputExpectation(0.73D, 0.2D).amount(1L, 1L)).isEqualTo(1L);
        assertThat(new OutputExpectation(0.73D, 0.8D).amount(1L, 1L)).isZero();
        assertThat(new OutputExpectation(0.27D, 0.2D).amount(1L, 1L)).isEqualTo(1L);
        assertThat(new OutputExpectation(0.27D, 0.3D).amount(1L, 1L)).isZero();
        assertThat(new OutputExpectation(0.7315D, 0.2D).amount(2L, 100L)).isEqualTo(147L);
        assertThat(new OutputExpectation(0.7315D, 0.4D).amount(2L, 100L)).isEqualTo(146L);
        assertThat(new OutputExpectation(0.5D, 0D).amount(2L, 1L)).isEqualTo(1L);
    }

    @Test
    void fraction_sampling_preserves_expected_yield_even_for_low_probability_single_outputs() {
        long total = 0L;
        for (int index = 0; index < 1_000; index++) {
            total += new OutputExpectation(0.273D, index / 1_000D).amount(1L, 1L);
        }
        assertThat(total).isEqualTo(273L);
    }

    @Test
    void clamps_perturbations_and_keeps_certain_outputs_deterministic() {
        AtomicInteger draws = new AtomicInteger();
        assertThat(OutputExpectation.sample(0F, () -> { draws.incrementAndGet(); return 1D; })
                .amount(10L, 100L)).isZero();
        assertThat(OutputExpectation.sample(1F, () -> { draws.incrementAndGet(); return 0D; })
                .amount(10L, 100L)).isEqualTo(1_000L);
        assertThat(draws).hasValue(0);
        assertThat(OutputExpectation.sample(0.05F, () -> 0D).amount(10L, 100L)).isZero();
        assertThat(OutputExpectation.sample(0.95F, () -> 0.999D).amount(10L, 100L)).isEqualTo(1_000L);
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

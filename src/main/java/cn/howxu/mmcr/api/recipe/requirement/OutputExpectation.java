package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.util.SaturatingLong;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.DoubleSupplier;

/**
 * One sampled probability shared by output sizing, capacity checks and reservations.
 *
 * @author howxu <dev@howxu.cn>
 */
public record OutputExpectation(double probability, double roundingSample) {
    public static OutputExpectation sample(float chance, DoubleSupplier random) {
        if (chance <= 0F) return new OutputExpectation(0D, 0D);
        if (chance >= 1F) return new OutputExpectation(1D, 0D);
        double base = Double.parseDouble(Float.toString(chance));
        double probability = Math.clamp(base + (random.getAsDouble() * 2D - 1D) * 0.1D, 0D, 1D);
        return new OutputExpectation(probability, random.getAsDouble());
    }

    public long amount(long singleAmount, long parallelism) {
        if (probability <= 0D || singleAmount <= 0L || parallelism <= 0L) return 0L;
        if (probability >= 1D) return SaturatingLong.multiply(singleAmount, parallelism);
        // Apply probability before saturating: the nominal product may exceed long while the result fits.
        BigDecimal expected = BigDecimal.valueOf(singleAmount).multiply(BigDecimal.valueOf(parallelism))
                .multiply(BigDecimal.valueOf(probability));
        BigDecimal result = expected.setScale(0, RoundingMode.DOWN);
        if (expected.subtract(result).compareTo(BigDecimal.valueOf(roundingSample)) > 0) {
            result = result.add(BigDecimal.ONE);
        }
        return result.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0 ? Long.MAX_VALUE : result.longValue();
    }

    public long maximum(long singleAmount, long requested, long capacity, boolean partial) {
        if (amount(singleAmount, requested) == 0L) return requested;
        if (partial) return capacity > 0L ? requested : 0L;
        if (probability >= 1D) {
            long maximum = Math.min(requested, capacity / singleAmount);
            return maximum == 0L && capacity > 0L ? 1L : maximum;
        }
        long lower = 0L;
        long upper = requested;
        while (lower < upper) {
            long distance = upper - lower;
            long middle = lower + (distance >>> 1) + (distance & 1L);
            if (amount(singleAmount, middle) <= capacity) lower = middle;
            else upper = middle - 1L;
        }
        // Keep the existing full-output failure path when even one craft cannot fit.
        return lower == 0L && capacity > 0L ? 1L : lower;
    }
}

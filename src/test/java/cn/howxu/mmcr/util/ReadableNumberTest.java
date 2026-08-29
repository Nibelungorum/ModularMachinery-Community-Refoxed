package cn.howxu.mmcr.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadableNumberTest {

    @Test
    void formats_ints_with_grouping_below_one_million() {
        assertThat(ReadableNumber.format(0)).isEqualTo("0");
        assertThat(ReadableNumber.format(32_000)).isEqualTo("32,000");
        assertThat(ReadableNumber.format(999_999)).isEqualTo("999,999");
        assertThat(ReadableNumber.format(1_000_000)).isEqualTo("1.00M");
        assertThat(ReadableNumber.format(Integer.MAX_VALUE - 1)).isEqualTo("2.14G");
    }

    @Test
    void formats_large_ints_with_decimal_truncation() {
        assertThat(ReadableNumber.format(1_000_000_000)).isEqualTo("1.00G");
        assertThat(ReadableNumber.format(Integer.MAX_VALUE)).isEqualTo("2.14G");
        assertThat(ReadableNumber.format(9_999_999_999L)).isEqualTo("9.99G");
    }

    @Test
    void formats_compact_values_from_one_thousand() {
        assertThat(ReadableNumber.formatCompact(999)).isEqualTo("999");
        assertThat(ReadableNumber.formatCompact(1_000)).isEqualTo("1k");
        assertThat(ReadableNumber.formatCompact(1_234)).isEqualTo("1.23k");
        assertThat(ReadableNumber.formatCompact(1_000_000)).isEqualTo("1M");
        assertThat(ReadableNumber.formatCompact(Long.MAX_VALUE)).isEqualTo("9.22E");
    }

    @Test
    void formats_slot_quantities_with_at_most_five_characters() {
        assertThat(ReadableNumber.formatForSlot(999, 0, "")).isEqualTo("999");
        assertThat(ReadableNumber.formatForSlot(1_000, 0, "")).isEqualTo("1.00K");
        assertThat(ReadableNumber.formatForSlot(1_234, 0, "")).isEqualTo("1.23K");
        assertThat(ReadableNumber.formatForSlot(10_000, 0, "")).isEqualTo("10.0K");
        assertThat(ReadableNumber.formatForSlot(114_514, 0, "")).isEqualTo("114K");
        assertThat(ReadableNumber.formatForSlot(114_100, 0, "")).isEqualTo("114K");
    }

    @Test
    void formats_scaled_slot_quantities_without_rounding() {
        assertThat(ReadableNumber.formatForSlot(1, 0, "mB")).isEqualTo("1mB");
        assertThat(ReadableNumber.formatForSlot(10, 0, "mB")).isEqualTo("10mB");
        assertThat(ReadableNumber.formatForSlot(11, 3, "B")).isEqualTo("0.01B");
        assertThat(ReadableNumber.formatForSlot(140, 3, "B")).isEqualTo("0.14B");
        assertThat(ReadableNumber.formatForSlot(1_001, 3, "B")).isEqualTo("1.00B");
        assertThat(ReadableNumber.formatForSlot(114_514, 3, "B")).isEqualTo("114B");
    }

    @Test
    void slot_formatter_rejects_invalid_values() {
        assertThatThrownBy(() -> ReadableNumber.formatForSlot(-1, 0, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.formatForSlot(1, -1, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compact_formatter_rejects_negative_values() {
        assertThatThrownBy(() -> ReadableNumber.formatCompact(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formats_exact_long_values_without_si_suffixes() {
        assertThat(ReadableNumber.formatExact(999_999L)).isEqualTo("999,999");
        assertThat(ReadableNumber.formatExact(1_000_000L)).isEqualTo("1,000,000");
        assertThat(ReadableNumber.formatExact(1_200_123_543_243L)).isEqualTo("1,200,123,543,243");
        assertThat(ReadableNumber.formatExact(Long.MAX_VALUE)).isEqualTo("9,223,372,036,854,775,807");
    }

    @Test
    void formats_big_integer_and_big_decimal_values() {
        assertThat(ReadableNumber.format(BigInteger.valueOf(999_999))).isEqualTo("999,999");
        assertThat(ReadableNumber.format(BigInteger.valueOf(1_000_000))).isEqualTo("1.00M");
        assertThat(ReadableNumber.format(BigInteger.valueOf(Long.MAX_VALUE))).isEqualTo("9.22E");
        assertThat(ReadableNumber.format(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))).isEqualTo("9.22E");
        assertThat(ReadableNumber.format(new BigInteger("9".repeat(101)))).isEqualTo("9.99E100");
        assertThat(ReadableNumber.format(BigDecimal.ZERO)).isEqualTo("0");
        assertThat(ReadableNumber.format(new BigDecimal("999999.99"))).isEqualTo("999,999");
        assertThat(ReadableNumber.format(new BigDecimal("1000000"))).isEqualTo("1.00M");
        assertThat(ReadableNumber.format(new BigDecimal("2147483647.89"))).isEqualTo("2.14G");
    }

    @Test
    void formats_fractional_scientific_values_by_truncating_the_fraction() {
        BigDecimal value = new BigDecimal("1000000000000000000000000000.5");

        assertThat(ReadableNumber.format(value)).isEqualTo("1.00E27");
        assertThat(ReadableNumber.formatCompact(value)).isEqualTo("1.00E27");
    }

    @Test
    void rejects_negative_values() {
        assertThatThrownBy(() -> ReadableNumber.format(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.formatExact(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.format(BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.format(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}

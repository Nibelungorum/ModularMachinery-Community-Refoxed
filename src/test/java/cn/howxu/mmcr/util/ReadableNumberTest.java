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
    void rejects_negative_values() {
        assertThatThrownBy(() -> ReadableNumber.format(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.formatExact(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.format(BigInteger.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReadableNumber.format(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}

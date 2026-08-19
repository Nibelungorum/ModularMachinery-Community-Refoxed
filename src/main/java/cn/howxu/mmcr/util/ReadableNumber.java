package cn.howxu.mmcr.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats readable numeric values for UI display.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ReadableNumber {

    private static final BigInteger ONE_BILLION = BigInteger.valueOf(1_000_000_000L);
    private static final BigDecimal ONE_BILLION_DECIMAL = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal THOUSAND_DECIMAL = BigDecimal.valueOf(1_000L);
    private static final String[] SI_PREFIXES = {"", "k", "M", "G", "T", "P", "E", "Z", "Y"};
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);

    private ReadableNumber() {}

    public static String format(int value) {
        return format((long) value);
    }

    public static String format(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value < 1_000_000_000L) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(BigDecimal.valueOf(value));
    }

    public static String format(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value.compareTo(ONE_BILLION) < 0) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(new BigDecimal(value));
    }

    public static String format(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value.compareTo(ONE_BILLION_DECIMAL) < 0) {
            return INTEGER_FORMAT.format(value.setScale(0, RoundingMode.DOWN).toBigIntegerExact());
        }
        return formatBigDecimal(value);
    }

    private static String formatBigDecimal(BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        int prefixIndex = exponent / 3;
        if (prefixIndex >= SI_PREFIXES.length) {
            return formatScientific(value.toBigIntegerExact());
        }
        BigDecimal divisor = BigDecimal.TEN.pow(prefixIndex * 3);
        BigDecimal truncated = value.divide(divisor, 2, RoundingMode.DOWN);
        return truncated.toPlainString() + SI_PREFIXES[prefixIndex];
    }

    private static String formatScientific(BigInteger value) {
        if (value.signum() == 0) {
            return "0";
        }
        String digits = value.toString();
        int exponent = digits.length() - 1;
        BigDecimal mantissa = new BigDecimal(digits.charAt(0) + "." + digits.substring(1));
        return mantissa.setScale(2, RoundingMode.DOWN).toPlainString() + "E" + exponent;
    }
}

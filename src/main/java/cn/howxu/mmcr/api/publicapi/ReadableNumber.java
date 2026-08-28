package cn.howxu.mmcr.api.publicapi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats non-negative numeric values for readable UI display.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ReadableNumber {

    private static final BigInteger ONE_MILLION = BigInteger.valueOf(1_000_000L);
    private static final BigInteger ONE_THOUSAND = BigInteger.valueOf(1_000L);
    private static final BigDecimal ONE_MILLION_DECIMAL = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal ONE_THOUSAND_DECIMAL = BigDecimal.valueOf(1_000L);
    private static final String[] SI_PREFIXES = {"", "k", "M", "G", "T", "P", "E", "Z", "Y"};
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.ROOT);

    private ReadableNumber() {}

    public static String format(int value) {
        return format((long) value);
    }

    public static String format(long value) {
        requireNonNegative(value);
        if (value < 1_000_000L) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(BigDecimal.valueOf(value), false);
    }

    public static String formatExact(long value) {
        requireNonNegative(value);
        return INTEGER_FORMAT.format(value);
    }

    public static String format(BigInteger value) {
        requireNonNegative(value);
        if (value.compareTo(ONE_MILLION) < 0) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(new BigDecimal(value), false);
    }

    public static String format(BigDecimal value) {
        requireNonNegative(value);
        if (value.compareTo(ONE_MILLION_DECIMAL) < 0) {
            return INTEGER_FORMAT.format(value.setScale(0, RoundingMode.DOWN).toBigIntegerExact());
        }
        return formatBigDecimal(value, false);
    }

    public static String formatCompact(int value) {
        return formatCompact((long) value);
    }

    public static String formatCompact(long value) {
        requireNonNegative(value);
        if (value < 1_000L) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(BigDecimal.valueOf(value), true);
    }

    public static String formatCompact(BigInteger value) {
        requireNonNegative(value);
        if (value.compareTo(ONE_THOUSAND) < 0) {
            return INTEGER_FORMAT.format(value);
        }
        return formatBigDecimal(new BigDecimal(value), true);
    }

    public static String formatCompact(BigDecimal value) {
        requireNonNegative(value);
        if (value.compareTo(ONE_THOUSAND_DECIMAL) < 0) {
            return INTEGER_FORMAT.format(value.setScale(0, RoundingMode.DOWN).toBigIntegerExact());
        }
        return formatBigDecimal(value, true);
    }

    private static String formatBigDecimal(BigDecimal value, boolean stripTrailingZeros) {
        int exponent = value.precision() - value.scale() - 1;
        int prefixIndex = exponent / 3;
        if (prefixIndex >= SI_PREFIXES.length) {
            return formatScientific(value.toBigIntegerExact());
        }
        BigDecimal divisor = BigDecimal.TEN.pow(prefixIndex * 3);
        BigDecimal truncated = value.divide(divisor, 2, RoundingMode.DOWN);
        String number = stripTrailingZeros
                ? truncated.stripTrailingZeros().toPlainString()
                : truncated.toPlainString();
        return number + SI_PREFIXES[prefixIndex];
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

    private static void requireNonNegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }

    private static void requireNonNegative(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }

    private static void requireNonNegative(BigDecimal value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }
}

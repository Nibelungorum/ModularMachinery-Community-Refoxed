package cn.howxu.mmcr.api.publicapi;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

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
    private static final String[] SLOT_PREFIXES = {"", "K", "M", "G", "T", "P", "E"};
    private static final long[] POWERS_OF_TEN = {
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L,
            1_000_000_000L, 10_000_000_000L, 100_000_000_000L, 1_000_000_000_000L,
            10_000_000_000_000L, 100_000_000_000_000L, 1_000_000_000_000_000L,
            10_000_000_000_000_000L, 100_000_000_000_000_000L, 1_000_000_000_000_000_000L
    };
    private static final int SLOT_MAX_LENGTH = 5;
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

    /**
     * Formats a scaled quantity for a compact five-character slot overlay.
     *
     * <p>The value is interpreted as {@code value / 10^scale} in the supplied
     * unit. The result is truncated rather than rounded and uses uppercase SI
     * prefixes. For example, {@code formatForSlot(1_001, 3, "B")} returns
     * {@code "1.00B"}.
     */
    public static String formatForSlot(long value, int scale, String unit) {
        requireNonNegative(value);
        Objects.requireNonNull(unit, "unit");
        if (scale < 0 || scale > 18) {
            throw new IllegalArgumentException("scale must be between 0 and 18");
        }

        int prefixIndex = slotPrefixIndex(value, scale);
        String prefix = SLOT_PREFIXES[prefixIndex];
        int decimalExponent = scale + prefixIndex * 3;
        long divisor = POWERS_OF_TEN[decimalExponent];
        long whole = value / divisor;
        int numericWidth = SLOT_MAX_LENGTH - prefix.length() - unit.length();
        if (numericWidth <= 0) {
            throw new IllegalArgumentException("unit is too long for a slot quantity");
        }

        int decimalPlaces = Math.max(0, numericWidth - decimalDigits(whole) - 1);
        decimalPlaces = Math.min(decimalPlaces, decimalExponent);
        long fraction = decimalPlaces == 0
                ? 0L
                : (value % divisor) / POWERS_OF_TEN[decimalExponent - decimalPlaces];

        StringBuilder result = new StringBuilder(SLOT_MAX_LENGTH);
        result.append(whole);
        if (decimalPlaces > 0) {
            result.append('.');
            for (int i = decimalDigits(fraction); i < decimalPlaces; i++) {
                result.append('0');
            }
            result.append(fraction);
        }
        result.append(prefix).append(unit);
        return result.toString();
    }

    private static String formatBigDecimal(BigDecimal value, boolean stripTrailingZeros) {
        int exponent = value.precision() - value.scale() - 1;
        int prefixIndex = exponent / 3;
        if (prefixIndex >= SI_PREFIXES.length) {
            return formatScientific(value);
        }
        BigDecimal divisor = BigDecimal.TEN.pow(prefixIndex * 3);
        BigDecimal truncated = value.divide(divisor, 2, RoundingMode.DOWN);
        String number = stripTrailingZeros
                ? truncated.stripTrailingZeros().toPlainString()
                : truncated.toPlainString();
        return number + SI_PREFIXES[prefixIndex];
    }

    private static String formatScientific(BigDecimal value) {
        BigInteger integerValue = value.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
        if (integerValue.signum() == 0) {
            return "0";
        }
        String digits = integerValue.toString();
        int exponent = digits.length() - 1;
        BigDecimal mantissa = new BigDecimal(digits.charAt(0) + "." + digits.substring(1));
        return mantissa.setScale(2, RoundingMode.DOWN).toPlainString() + "E" + exponent;
    }

    private static int slotPrefixIndex(long value, int scale) {
        int exponent = decimalDigits(value) - 1 - scale;
        return exponent <= 0 ? 0 : Math.min(exponent / 3, SLOT_PREFIXES.length - 1);
    }

    private static int decimalDigits(long value) {
        if (value < 10L) return 1;
        if (value < 100L) return 2;
        if (value < 1_000L) return 3;
        if (value < 10_000L) return 4;
        if (value < 100_000L) return 5;
        if (value < 1_000_000L) return 6;
        if (value < 10_000_000L) return 7;
        if (value < 100_000_000L) return 8;
        if (value < 1_000_000_000L) return 9;
        if (value < 10_000_000_000L) return 10;
        if (value < 100_000_000_000L) return 11;
        if (value < 1_000_000_000_000L) return 12;
        if (value < 10_000_000_000_000L) return 13;
        if (value < 100_000_000_000_000L) return 14;
        if (value < 1_000_000_000_000_000L) return 15;
        if (value < 10_000_000_000_000_000L) return 16;
        if (value < 100_000_000_000_000_000L) return 17;
        if (value < 1_000_000_000_000_000_000L) return 18;
        return 19;
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

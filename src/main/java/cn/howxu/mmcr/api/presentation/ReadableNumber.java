package cn.howxu.mmcr.api.presentation;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Formats readable numeric values for UI display.
 * @author howxu <dev@howxu.cn>
 */
public final class ReadableNumber {

    private ReadableNumber() {}

    public static String format(int value) {
        return cn.howxu.mmcr.util.ReadableNumber.format(value);
    }

    public static String format(long value) {
        return cn.howxu.mmcr.util.ReadableNumber.format(value);
    }

    public static String formatExact(long value) {
        return cn.howxu.mmcr.util.ReadableNumber.formatExact(value);
    }

    public static String format(BigInteger value) {
        return cn.howxu.mmcr.util.ReadableNumber.format(value);
    }

    public static String format(BigDecimal value) {
        return cn.howxu.mmcr.util.ReadableNumber.format(value);
    }

    public static String formatCompact(int value) {
        return cn.howxu.mmcr.util.ReadableNumber.formatCompact(value);
    }

    public static String formatCompact(long value) {
        return cn.howxu.mmcr.util.ReadableNumber.formatCompact(value);
    }

    public static String formatCompact(BigInteger value) {
        return cn.howxu.mmcr.util.ReadableNumber.formatCompact(value);
    }

    public static String formatCompact(BigDecimal value) {
        return cn.howxu.mmcr.util.ReadableNumber.formatCompact(value);
    }

    public static String formatForSlot(long value, int scale, String unit) {
        return cn.howxu.mmcr.util.ReadableNumber.formatForSlot(value, scale, unit);
    }
}

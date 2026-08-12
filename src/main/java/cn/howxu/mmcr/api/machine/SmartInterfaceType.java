package cn.howxu.mmcr.api.machine;

import java.util.Locale;
import java.util.Objects;

/**
 * Machine-level declaration for a smart interface value.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceType(String type, float defaultValue, int priority,
        String headerInfo, String valueInfo, String footerInfo, String notEqualMessage,
        String jeiTooltip, int jeiTooltipArgsCount, ValueType valueType) {

    public enum ValueType {
        FLOAT,
        INTEGER;

        public static ValueType byName(String name) {
            if (name == null || name.isBlank()) return FLOAT;
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "float" -> FLOAT;
                case "int", "integer" -> INTEGER;
                default -> throw new IllegalArgumentException("Unknown smart interface value type: " + name);
            };
        }
    }

    public SmartInterfaceType(String type, float defaultValue, int priority,
            String headerInfo, String valueInfo, String footerInfo, String notEqualMessage,
            String jeiTooltip, int jeiTooltipArgsCount) {
        this(type, defaultValue, priority, headerInfo, valueInfo, footerInfo, notEqualMessage, jeiTooltip,
                jeiTooltipArgsCount, ValueType.FLOAT);
    }

    public SmartInterfaceType {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type blank");
        if (!Float.isFinite(defaultValue)) throw new IllegalArgumentException("defaultValue not finite");
        valueType = valueType == null ? ValueType.FLOAT : valueType;
        if (valueType == ValueType.INTEGER && defaultValue != Math.rint(defaultValue)) {
            throw new IllegalArgumentException("integer smart interface default must be integral");
        }
        headerInfo = Objects.requireNonNullElse(headerInfo, "");
        valueInfo = Objects.requireNonNullElse(valueInfo, "");
        footerInfo = Objects.requireNonNullElse(footerInfo, "");
        notEqualMessage = Objects.requireNonNullElse(notEqualMessage, "");
        jeiTooltip = Objects.requireNonNullElse(jeiTooltip, "");
        jeiTooltipArgsCount = Math.max(0, jeiTooltipArgsCount);
    }

    public boolean accepts(float value) {
        return Float.isFinite(value) && (valueType == ValueType.FLOAT || value == Math.rint(value));
    }
}

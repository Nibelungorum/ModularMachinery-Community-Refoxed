package cn.howxu.mmcr.api.publicapi.machine;

import java.util.Locale;

/** Public declaration of a smart-interface value type.
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceType(String type, float defaultValue, float minValue, float maxValue, int priority,
        ValueType valueType) {
    public enum ValueType {
        FLOAT, INTEGER;

        public static ValueType byName(String name) {
            if (name == null || name.isBlank()) return FLOAT;
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "float" -> FLOAT;
                case "int", "integer" -> INTEGER;
                default -> throw new IllegalArgumentException("Unknown smart interface value type: " + name);
            };
        }
    }

    public SmartInterfaceType(String type, float minValue, float maxValue, int priority) {
        this(type, minValue, minValue, maxValue, priority, ValueType.FLOAT);
    }

    public SmartInterfaceType(String type, float minValue, float maxValue, int priority, ValueType valueType) {
        this(type, minValue, minValue, maxValue, priority, valueType);
    }

    public SmartInterfaceType {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type blank");
        if (!Float.isFinite(defaultValue) || !Float.isFinite(minValue) || !Float.isFinite(maxValue)
                || minValue > maxValue || defaultValue < minValue || defaultValue > maxValue) {
            throw new IllegalArgumentException("invalid smart interface range");
        }
        valueType = valueType == null ? ValueType.FLOAT : valueType;
        if (valueType == ValueType.INTEGER
                && (defaultValue != Math.rint(defaultValue) || minValue != Math.rint(minValue)
                || maxValue != Math.rint(maxValue))) {
            throw new IllegalArgumentException("integer smart interface range must be integral");
        }
    }
}

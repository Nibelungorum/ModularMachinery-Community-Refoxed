package cn.howxu.mmcr.api.machine;

import java.util.Locale;

/**
 * Machine-level declaration for a smart interface value.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceType(String type, float defaultValue, float minValue, float maxValue, int priority,
        ValueType valueType) {

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

    public SmartInterfaceType(String type, float defaultValue, int priority) {
        this(type, defaultValue, priority, ValueType.FLOAT);
    }

    public SmartInterfaceType(String type, float defaultValue, int priority, ValueType valueType) {
        this(type, defaultValue, defaultValue, Float.MAX_VALUE, priority, valueType);
    }

    public SmartInterfaceType(String type, float minValue, float maxValue, int priority) {
        this(type, minValue, maxValue, priority, ValueType.FLOAT);
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
                && (defaultValue != Math.rint(defaultValue) || minValue != Math.rint(minValue) || maxValue != Math.rint(maxValue))) {
            throw new IllegalArgumentException("integer smart interface range must be integral");
        }
    }

    public String translationKey() {
        return "mmcr.smart_interface.type." + type;
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public boolean accepts(float value) {
        return Float.isFinite(value)
                && value >= minValue
                && value <= maxValue
                && (valueType == ValueType.FLOAT || value == Math.rint(value));
    }

    public float validatedValue(float value) {
        return accepts(value) ? value : minValue;
    }
}

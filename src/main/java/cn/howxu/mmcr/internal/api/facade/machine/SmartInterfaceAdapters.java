package cn.howxu.mmcr.internal.api.facade.machine;

import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.publicapi.machine.SmartInterfaceSpec;
import cn.howxu.mmcr.publicapi.machine.SmartInterfaces.ValueType;
import java.util.Objects;

/** Canonical smart interface adapters. @author howxu <dev@howxu.cn> */
public final class SmartInterfaceAdapters {
    private SmartInterfaceAdapters() {}

    public static SmartInterfaceSpec type(String type, float value, int priority) {
        return wrap(new SmartInterfaceType(type, value, priority));
    }
    public static SmartInterfaceSpec type(String type, float value, int priority, ValueType valueType) {
        return wrap(new SmartInterfaceType(type, value, priority, toCore(valueType)));
    }
    public static SmartInterfaceSpec type(String type, float min, float max, int priority) {
        return wrap(new SmartInterfaceType(type, min, max, priority));
    }
    public static SmartInterfaceSpec type(String type, float min, float max, int priority, ValueType valueType) {
        return wrap(new SmartInterfaceType(type, min, max, priority, toCore(valueType)));
    }
    public static SmartInterfaceSpec type(String type, float value, float min, float max, int priority, ValueType valueType) {
        return wrap(new SmartInterfaceType(type, value, min, max, priority, toCore(valueType)));
    }
    public static ValueType valueType(String name) {
        return fromCore(SmartInterfaceType.ValueType.byName(name));
    }
    private static SmartInterfaceType.ValueType toCore(ValueType value) {
        if (value == null) return null;
        return switch (value) {
            case FLOAT -> SmartInterfaceType.ValueType.FLOAT;
            case INTEGER -> SmartInterfaceType.ValueType.INTEGER;
        };
    }
    private static ValueType fromCore(SmartInterfaceType.ValueType value) {
        return switch (value) {
            case FLOAT -> ValueType.FLOAT;
            case INTEGER -> ValueType.INTEGER;
        };
    }
    public static SmartInterfaceSpec wrap(SmartInterfaceType type) {
        return new View(Objects.requireNonNull(type, "type"));
    }
    public static SmartInterfaceType unwrap(SmartInterfaceSpec type) {
        return ((View) Objects.requireNonNull(type, "type")).delegate;
    }

    /** @author howxu <dev@howxu.cn> */
    private record View(SmartInterfaceType delegate) implements SmartInterfaceSpec {
        @Override public String type() { return delegate.type(); }
        @Override public float defaultValue() { return delegate.defaultValue(); }
        @Override public float minValue() { return delegate.minValue(); }
        @Override public float maxValue() { return delegate.maxValue(); }
        @Override public int priority() { return delegate.priority(); }
        @Override public ValueType valueType() { return fromCore(delegate.valueType()); }
        @Override public String translationKey() { return delegate.translationKey(); }
        @Override public String descriptionKey() { return delegate.descriptionKey(); }
        @Override public boolean accepts(float value) { return delegate.accepts(value); }
        @Override public float validatedValue(float value) { return delegate.validatedValue(value); }
    }
}

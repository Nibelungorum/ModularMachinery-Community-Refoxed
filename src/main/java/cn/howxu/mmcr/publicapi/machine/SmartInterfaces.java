package cn.howxu.mmcr.publicapi.machine;

import cn.howxu.mmcr.internal.api.facade.machine.SmartInterfaceAdapters;

/** Smart interface declaration factories. @author howxu <dev@howxu.cn> */
public final class SmartInterfaces {
    private SmartInterfaces() {}

    /** Smart value representation. @author howxu <dev@howxu.cn> */
    public enum ValueType {
        FLOAT, INTEGER;

        public static ValueType byName(String name) {
            return SmartInterfaceAdapters.valueType(name);
        }
    }

    public static SmartInterfaceSpec type(String type, float defaultValue, int priority) {
        return SmartInterfaceAdapters.type(type, defaultValue, priority);
    }

    public static SmartInterfaceSpec type(String type, float defaultValue, int priority, ValueType valueType) {
        return SmartInterfaceAdapters.type(type, defaultValue, priority, valueType);
    }

    public static SmartInterfaceSpec type(String type, float min, float max, int priority) {
        return SmartInterfaceAdapters.type(type, min, max, priority);
    }

    public static SmartInterfaceSpec type(String type, float min, float max, int priority, ValueType valueType) {
        return SmartInterfaceAdapters.type(type, min, max, priority, valueType);
    }

    public static SmartInterfaceSpec type(String type, float defaultValue, float min, float max, int priority, ValueType valueType) {
        return SmartInterfaceAdapters.type(type, defaultValue, min, max, priority, valueType);
    }
}

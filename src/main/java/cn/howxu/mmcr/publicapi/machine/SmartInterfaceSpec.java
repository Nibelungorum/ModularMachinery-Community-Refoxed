package cn.howxu.mmcr.publicapi.machine;

import org.jetbrains.annotations.ApiStatus;

/** Producer-created smart value declaration; consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface SmartInterfaceSpec {
    String type();
    float defaultValue();
    float minValue();
    float maxValue();
    int priority();
    SmartInterfaces.ValueType valueType();
    String translationKey();
    String descriptionKey();
    boolean accepts(float value);
    float validatedValue(float value);
}

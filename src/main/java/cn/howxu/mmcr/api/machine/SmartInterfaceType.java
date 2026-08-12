package cn.howxu.mmcr.api.machine;

import java.util.Objects;

/**
 * Machine-level declaration for a smart interface value.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceType(String type, float defaultValue, int priority,
        String headerInfo, String valueInfo, String footerInfo, String notEqualMessage,
        String jeiTooltip, int jeiTooltipArgsCount) {
    public SmartInterfaceType {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type blank");
        if (!Float.isFinite(defaultValue)) throw new IllegalArgumentException("defaultValue not finite");
        headerInfo = Objects.requireNonNullElse(headerInfo, "");
        valueInfo = Objects.requireNonNullElse(valueInfo, "");
        footerInfo = Objects.requireNonNullElse(footerInfo, "");
        notEqualMessage = Objects.requireNonNullElse(notEqualMessage, "");
        jeiTooltip = Objects.requireNonNullElse(jeiTooltip, "");
        jeiTooltipArgsCount = Math.max(0, jeiTooltipArgsCount);
    }
}

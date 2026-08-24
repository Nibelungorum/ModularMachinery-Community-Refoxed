package cn.howxu.mmcr.api.capability.status;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Describes the status of a capability operation.
 *
 * @param id the status identifier
 * @param severity the status severity
 * @param source the source that produced the status
 * @param details additional immutable status details
 * @author howxu <dev@howxu.cn>
 */
public record ExecutionStatus(
        Identifier id,
        StatusSeverity severity,
        Identifier source,
        Map<String, String> details) {
    public ExecutionStatus {
        details = Map.copyOf(details);
    }
}

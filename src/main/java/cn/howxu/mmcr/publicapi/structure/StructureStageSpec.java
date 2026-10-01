package cn.howxu.mmcr.publicapi.structure;

import org.jetbrains.annotations.ApiStatus;

/** Read-only stage produced by the facade; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StructureStageSpec {
    /** Declaration stage role.
     * @author howxu <dev@howxu.cn>
     */
    enum Kind { FULL, EXPANSION, EXTENSION }
    Kind kind();
    PatternSpec pattern();
    PortLimits portRequirements();
    PortTierLimits portTiers();
    StructureConstraints requirements();
}

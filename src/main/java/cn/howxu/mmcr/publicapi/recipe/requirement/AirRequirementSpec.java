package cn.howxu.mmcr.publicapi.recipe.requirement;

import org.jetbrains.annotations.ApiStatus;

/** Per-tick air demand or production, with an input-only minimum pressure.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface AirRequirementSpec extends RequirementSpec {
    long airPerTick();
    float minPressure();
}

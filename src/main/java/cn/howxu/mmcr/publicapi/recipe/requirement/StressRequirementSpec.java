package cn.howxu.mmcr.publicapi.recipe.requirement;

import org.jetbrains.annotations.ApiStatus;

/** Create-neutral declaration of base stress and signed output rotation.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface StressRequirementSpec extends RequirementSpec {
    double stress();
    double minRpm();
    double rpm();
}

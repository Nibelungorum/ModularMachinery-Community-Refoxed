package cn.howxu.mmcr.publicapi.recipe.requirement;

import org.jetbrains.annotations.ApiStatus;

/** Total mana consumed at recipe start or produced at completion.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface ManaRequirementSpec extends RequirementSpec {
    long amount();
}

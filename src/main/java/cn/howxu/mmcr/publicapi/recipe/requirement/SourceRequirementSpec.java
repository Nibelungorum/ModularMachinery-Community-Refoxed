package cn.howxu.mmcr.publicapi.recipe.requirement;

import org.jetbrains.annotations.ApiStatus;

/** Total source consumed at recipe start or produced at completion.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface SourceRequirementSpec extends RequirementSpec {
    long amount();
}

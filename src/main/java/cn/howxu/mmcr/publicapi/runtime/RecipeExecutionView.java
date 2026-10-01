package cn.howxu.mmcr.publicapi.runtime;

import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** MMCR-provided start snapshot; output stacks are copies, not write-through handles.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeExecutionView {
    int duration();
    List<RequirementSpec> requirements();
    List<OutputView> outputs();
}

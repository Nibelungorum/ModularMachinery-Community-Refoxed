package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.publicapi.runtime.RecipeView;
import cn.howxu.mmcr.publicapi.runtime.IoSnapshot;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** MMCR-provided per-tick recipe reads. This phase has no recipe cancel/edit setter.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeTickContext {
    MachineContext machineContext();
    RecipeView recipe();
    int currentTick();
    int totalTick();
    long parallelism();
    List<RequirementSpec> requirements();
    /** Output stacks are copies, not writable recipe state. */
    List<OutputView> outputs();
    IoSnapshot ioSnapshot();
}

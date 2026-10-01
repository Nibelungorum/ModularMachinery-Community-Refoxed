package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.publicapi.runtime.RecipeExecutionView;
import cn.howxu.mmcr.publicapi.runtime.RecipeView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** MMCR-provided start context with core-controlled recipe edits.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeStartContext {
    RecipeView recipe();
    MachineContext machineContext();
    ResourceLocation recipeId();
    long requestedParallelism();
    long effectiveParallelism();
    int duration();
    void setDuration(int ticks);
    boolean replaceExactItemInputCount(Item item, int expectedCount, int replacementCount);
    List<RequirementSpec> requirements();
    void setRequirements(List<RequirementSpec> requirements);
    /** Safe reads: stacks are copies. Use setOutputs to write changes back. */
    List<OutputView> outputs();
    /** Core updates output requirements/tags as well; custom conversion rules still apply. */
    void setOutputs(List<OutputView> outputs);
    RecipeExecutionView snapshot();
    void cancel();
    boolean cancelled();
}

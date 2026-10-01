package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.publicapi.runtime.RecipeView;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** MMCR-provided finish context. Cancel keeps pending work; discard completes without outputs.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeFinishContext {
    RecipeView recipe();
    MachineContext machineContext();
    ResourceLocation recipeId();
    long requestedParallelism();
    long effectiveParallelism();
    /** Safe reads: stacks are copies. Use setOutputs to write changes back. */
    List<OutputView> outputs();
    void setOutputs(List<OutputView> outputs);
    void discardOutputs();
    boolean outputsDiscarded();
    void cancel();
    boolean cancelled();
}

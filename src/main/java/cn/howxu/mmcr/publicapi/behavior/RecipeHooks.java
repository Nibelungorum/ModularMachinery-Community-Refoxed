package cn.howxu.mmcr.publicapi.behavior;

import org.jetbrains.annotations.ApiStatus;
import java.util.function.Consumer;

/** MMCR-provided recipe configuration handle; callbacks wrap contexts at the Java boundary.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeHooks {
    RecipeHooks idleStart(Consumer<MachineContext> callback);
    RecipeHooks idleEnd(Consumer<MachineContext> callback);
    RecipeHooks beforeStart(Consumer<RecipeStartContext> callback);
    RecipeHooks recipeTick(Consumer<RecipeTickContext> callback);
    RecipeHooks beforeFinish(Consumer<RecipeFinishContext> callback);
    RecipeHooks preServerTick(Consumer<MachineContext> callback);
    RecipeHooks postServerTick(Consumer<MachineContext> callback);
}

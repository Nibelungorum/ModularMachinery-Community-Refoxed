package cn.howxu.mmcr.publicapi.behavior;

import org.jetbrains.annotations.ApiStatus;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Recipe callbacks. Each factory registration creates a separate callback per controller on first use.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface RecipeHooks {
    RecipeHooks idleStart(Consumer<MachineContext> callback);
    RecipeHooks idleStart(Supplier<? extends Consumer<MachineContext>> factory);
    RecipeHooks idleEnd(Consumer<MachineContext> callback);
    RecipeHooks idleEnd(Supplier<? extends Consumer<MachineContext>> factory);
    RecipeHooks beforeStart(Consumer<RecipeStartContext> callback);
    RecipeHooks beforeStart(Supplier<? extends Consumer<RecipeStartContext>> factory);
    RecipeHooks recipeTick(Consumer<RecipeTickContext> callback);
    RecipeHooks recipeTick(Supplier<? extends Consumer<RecipeTickContext>> factory);
    RecipeHooks beforeFinish(Consumer<RecipeFinishContext> callback);
    RecipeHooks beforeFinish(Supplier<? extends Consumer<RecipeFinishContext>> factory);
    RecipeHooks preServerTick(Consumer<MachineContext> callback);
    RecipeHooks preServerTick(Supplier<? extends Consumer<MachineContext>> factory);
    RecipeHooks postServerTick(Consumer<MachineContext> callback);
    RecipeHooks postServerTick(Supplier<? extends Consumer<MachineContext>> factory);

    /** Unbound methods registered together use one object per controller, including all its factory lanes.
     * @author howxu <dev@howxu.cn>
     */
    @ApiStatus.NonExtendable
    interface ForInstance<T> {
        ForInstance<T> idleStart(BiConsumer<T, MachineContext> callback);
        ForInstance<T> idleEnd(BiConsumer<T, MachineContext> callback);
        ForInstance<T> beforeStart(BiConsumer<T, RecipeStartContext> callback);
        ForInstance<T> recipeTick(BiConsumer<T, RecipeTickContext> callback);
        ForInstance<T> beforeFinish(BiConsumer<T, RecipeFinishContext> callback);
        ForInstance<T> preServerTick(BiConsumer<T, MachineContext> callback);
        ForInstance<T> postServerTick(BiConsumer<T, MachineContext> callback);
    }
}

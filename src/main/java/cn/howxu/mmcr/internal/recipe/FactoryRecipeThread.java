package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Factory recipe thread with optional core-thread recipe filtering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeThread extends RecipeThread {
    public static final int IDLE_TIMEOUT_TICKS = 200;

    private final boolean coreThread;
    private final String threadName;
    private final Set<MachineRecipe> recipeSet = new LinkedHashSet<>();
    private int idleTicks;

    private FactoryRecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool,
                                boolean coreThread, String threadName) {
        super(controller, contextPool);
        this.coreThread = coreThread;
        this.threadName = threadName == null ? "" : threadName;
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        return new FactoryRecipeThread(controller, contextPool, false, "");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool,
                                           String threadName, Set<MachineRecipe> recipes) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool, true, threadName);
        thread.recipeSet.addAll(recipes == null ? Set.of() : recipes);
        return thread;
    }

    public Set<MachineRecipe> recipeSet() { return Set.copyOf(recipeSet); }
    public boolean isCoreThread() { return coreThread; }
    public String threadName() { return threadName; }
    public boolean isTimedOut() { return !coreThread && isIdle() && idleTicks >= IDLE_TIMEOUT_TICKS; }
    public void tickIdle() { idleTicks = isIdle() ? idleTicks + 1 : 0; }

    @Override protected void onStarted() { idleTicks = 0; }
    @Override protected void onFinished() { idleTicks = 0; }

    public void setActiveRecipeForTesting(@Nullable ActiveMachineRecipe activeRecipe) {
        this.activeRecipe = activeRecipe;
    }

    public void save(ValueOutput output) {
        output.putBoolean("core", coreThread);
        output.putString("name", threadName);
        output.putInt("idle_ticks", idleTicks);
        output.putBoolean("has_active", activeRecipe != null);
        if (activeRecipe != null) activeRecipe.serialize(output.child("active_recipe"));
    }

    public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller,
                                           RecipeCraftingContextPool contextPool) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool,
                input.getBooleanOr("core", false), input.getStringOr("name", ""));
        thread.idleTicks = input.getIntOr("idle_ticks", 0);
        if (input.getBooleanOr("has_active", false)) {
            thread.activeRecipe = ActiveMachineRecipe.from(input.childOrEmpty("active_recipe"));
            if (thread.activeRecipe.getRecipe() == null) thread.activeRecipe = null;
        }
        if (thread.activeRecipe != null && controller != null) thread.context = contextPool.borrow(thread.activeRecipe, controller);
        return thread;
    }
}

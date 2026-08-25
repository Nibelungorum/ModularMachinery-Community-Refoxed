package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory recipe thread with optional core-thread recipe filtering.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeThread extends RecipeThread {
    public static final int IDLE_TIMEOUT_TICKS = 200;

    private final boolean coreThread;
    private final boolean baseThread;
    private final String threadName;
    private final String laneId;
    private final Set<MachineRecipe> recipeSet = new LinkedHashSet<>();
    private int idleTicks;
    private Runnable finishContinuation = () -> { };

    private FactoryRecipeThread(MachineControllerBlockEntity controller,
                                  boolean coreThread, boolean baseThread, String threadName) {
        super(controller);
        this.coreThread = coreThread;
        this.baseThread = baseThread;
        this.threadName = threadName == null ? "" : threadName;
        this.laneId = baseThread ? "base" : coreThread ? "core-" + this.threadName
                : this.threadName.startsWith("factory-") ? this.threadName : "factory";
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller) {
        return simple(controller, "factory");
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, String laneId) {
        return new FactoryRecipeThread(controller, false, false, laneId);
    }

    public static FactoryRecipeThread base(MachineControllerBlockEntity controller) {
        return new FactoryRecipeThread(controller, false, true, "");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller,
                                           String threadName, Set<MachineRecipe> recipes) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, true, false, threadName);
        thread.recipeSet.addAll(recipes == null ? Set.of() : recipes);
        return thread;
    }

    public Set<MachineRecipe> recipeSet() { return Set.copyOf(recipeSet); }
    public List<MachineRecipe> candidatesFor(List<MachineRecipe> candidates) {
        if (!coreThread || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        return candidates.stream().filter(recipeSet::contains).toList();
    }

    public void replaceRecipeSet(Set<MachineRecipe> recipes) {
        if (!coreThread) return;
        recipeSet.clear();
        recipeSet.addAll(recipes == null ? Set.of() : recipes);
    }

    public boolean isCoreThread() { return coreThread; }
    public boolean isBaseThread() { return baseThread; }
    public String threadName() { return threadName; }
    @Override public String laneId() { return laneId; }
    public boolean isTimedOut(boolean recipeLockUsed) {
        return !baseThread && !coreThread && !recipeLockUsed && isIdle() && idleTicks >= IDLE_TIMEOUT_TICKS;
    }
    public void tickIdle() { idleTicks = isIdle() ? idleTicks + 1 : 0; }

    @Override protected void onStarted() {
        idleTicks = 0;
    }
    @Override protected void onFinished() { idleTicks = 0; }

    public void setFinishContinuation(Runnable finishContinuation) {
        this.finishContinuation = finishContinuation == null ? () -> { } : finishContinuation;
    }

    @Override protected void onRecipeFinished() {
        finishContinuation.run();
    }

    @Override
    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        return searchAndStartRecipe(candidates, availableParallelism, structureVersion, null);
    }

    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                        long structureVersion, @Nullable Identifier lockedRecipeId) {
        return super.searchAndStartRecipe(candidatesFor(candidates), availableParallelism, structureVersion, lockedRecipeId);
    }

    public void setActiveRecipeForTesting(@Nullable ActiveMachineRecipe activeRecipe) {
        if (activeRecipe == null) runtime.invalidate();
        else {
            ControllerRuntimeSnapshot snapshot = controller.runtimeSnapshot();
            runtime.restore(activeRecipe, controller.resourceDomain(), snapshot.structure().version(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
        }
    }

    public void save(ValueOutput output) {
        output.putBoolean("core", coreThread);
        output.putBoolean("base", baseThread);
        output.putString("name", threadName);
        output.putInt("idle_ticks", idleTicks);
        runtime.save(output.child("runtime"));
    }

    public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller,
                input.getBooleanOr("core", false), input.getBooleanOr("base", false), input.getStringOr("name", ""));
        thread.idleTicks = input.getIntOr("idle_ticks", 0);
        thread.runtime.load(input.childOrEmpty("runtime"), controller.resourceDomain());
        return thread;
    }
}

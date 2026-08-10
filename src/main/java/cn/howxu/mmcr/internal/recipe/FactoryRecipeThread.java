package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
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
    private @Nullable MachineRecipe lastRecipe;
    private long lastRecipeStructureVersion = Long.MIN_VALUE;
    private long lastRecipeModifierSnapshotVersion = Long.MIN_VALUE;

    private FactoryRecipeThread(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool,
                                  boolean coreThread, boolean baseThread, String threadName) {
        super(controller, contextPool);
        this.coreThread = coreThread;
        this.baseThread = baseThread;
        this.threadName = threadName == null ? "" : threadName;
        this.laneId = baseThread ? "base" : coreThread ? "core-" + this.threadName
                : this.threadName.startsWith("factory-") ? this.threadName : "factory";
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        return simple(controller, contextPool, "factory");
    }

    public static FactoryRecipeThread simple(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool, String laneId) {
        return new FactoryRecipeThread(controller, contextPool, false, false, laneId);
    }

    public static FactoryRecipeThread base(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool) {
        return new FactoryRecipeThread(controller, contextPool, false, true, "");
    }

    public static FactoryRecipeThread core(MachineControllerBlockEntity controller, RecipeCraftingContextPool contextPool,
                                           String threadName, Set<MachineRecipe> recipes) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool, true, false, threadName);
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
    public boolean isTimedOut() { return !baseThread && !coreThread && isIdle() && idleTicks >= IDLE_TIMEOUT_TICKS; }
    public void tickIdle() { idleTicks = isIdle() ? idleTicks + 1 : 0; }

    @Override protected void onStarted() {
        idleTicks = 0;
        if (activeRecipe != null && controller != null) {
            rememberLastRecipe(activeRecipe.getRecipe(), controller.getStructureVersion(), controller.getModifierSnapshotVersion());
        }
    }
    @Override protected void onFinished() { idleTicks = 0; }

    @Override
    public boolean searchAndStartRecipe(List<MachineRecipe> candidates, int availableParallelism, long structureVersion) {
        return super.searchAndStartRecipe(candidatesFor(candidates), availableParallelism, structureVersion);
    }

    public boolean tryRestartLastRecipe(List<MachineRecipe> candidates, int availableParallelism,
                                        long structureVersion, long modifierSnapshotVersion) {
        if (lastRecipe == null || controller == null || availableParallelism <= 0
                || lastRecipeStructureVersion != structureVersion
                || lastRecipeModifierSnapshotVersion != modifierSnapshotVersion
                || !candidatesFor(candidates).contains(lastRecipe)) return false;
        ActiveMachineRecipe next = new ActiveMachineRecipe(lastRecipe, availableParallelism);
        var nextContext = contextPool.borrow(next, controller);
        return startRecipe(next, nextContext, structureVersion);
    }

    public void rememberLastRecipe(MachineRecipe recipe, long structureVersion, long modifierSnapshotVersion) {
        lastRecipe = recipe;
        lastRecipeStructureVersion = structureVersion;
        lastRecipeModifierSnapshotVersion = modifierSnapshotVersion;
    }

    public void setActiveRecipeForTesting(@Nullable ActiveMachineRecipe activeRecipe) {
        this.activeRecipe = activeRecipe;
    }

    public void save(ValueOutput output) {
        output.putBoolean("core", coreThread);
        output.putBoolean("base", baseThread);
        output.putString("name", threadName);
        output.putInt("idle_ticks", idleTicks);
        output.putBoolean("has_last", lastRecipe != null);
        if (lastRecipe != null) {
            output.putString("last_recipe", lastRecipe.id().toString());
            output.putLong("last_structure_version", lastRecipeStructureVersion);
            output.putLong("last_modifier_snapshot_version", lastRecipeModifierSnapshotVersion);
        }
        output.putBoolean("has_active", activeRecipe != null);
        if (activeRecipe != null) activeRecipe.serialize(output.child("active_recipe"));
    }

    public static FactoryRecipeThread load(ValueInput input, MachineControllerBlockEntity controller,
                                           RecipeCraftingContextPool contextPool) {
        FactoryRecipeThread thread = new FactoryRecipeThread(controller, contextPool,
                input.getBooleanOr("core", false), input.getBooleanOr("base", false), input.getStringOr("name", ""));
        thread.idleTicks = input.getIntOr("idle_ticks", 0);
        if (input.getBooleanOr("has_last", false)) {
            String recipeName = input.getStringOr("last_recipe", "");
            Identifier recipeId = recipeName.isEmpty() ? null : Identifier.parse(recipeName);
            thread.lastRecipe = RecipeRegistry.getRecipe(recipeId);
            if (thread.lastRecipe != null) {
                thread.lastRecipeStructureVersion = input.getLongOr("last_structure_version", Long.MIN_VALUE);
                thread.lastRecipeModifierSnapshotVersion = input.getLongOr("last_modifier_snapshot_version", Long.MIN_VALUE);
            }
        }
        if (input.getBooleanOr("has_active", false)) {
            thread.activeRecipe = ActiveMachineRecipe.from(input.childOrEmpty("active_recipe"));
            if (thread.activeRecipe.getRecipe() == null) thread.activeRecipe = null;
        }
        if (thread.activeRecipe != null && controller != null) thread.context = contextPool.borrow(thread.activeRecipe, controller);
        return thread;
    }
}

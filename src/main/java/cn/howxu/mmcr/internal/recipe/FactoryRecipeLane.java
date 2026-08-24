package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.internal.runtime.CraftingRuntime;

/**
 * Factory lane adapter backed by one crafting runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FactoryRecipeLane implements FactoryRecipeScheduler.Lane {
    private final CraftingRuntime runtime;
    private final Runnable onFinished;
    private boolean closed;

    public FactoryRecipeLane(CraftingRuntime runtime) {
        this(runtime, () -> { });
    }

    public FactoryRecipeLane(CraftingRuntime runtime, Runnable onFinished) {
        if (runtime == null) throw new IllegalArgumentException("runtime null");
        if (onFinished == null) throw new IllegalArgumentException("onFinished null");
        this.runtime = runtime;
        this.onFinished = onFinished;
    }

    public CraftingRuntime runtime() {
        return runtime;
    }

    @Override
    public void start() {
        if (closed) return;
        runtime.tick();
    }

    @Override
    public boolean tick() {
        if (closed) return true;
        boolean active = runtime.active();
        runtime.tick();
        if (runtime.finishPending()) runtime.finish();
        if (active && !runtime.active() && runtime.failure() == null) {
            onFinished.run();
            closed = true;
        }
        return closed;
    }

    @Override
    public void stop() {
        if (closed) return;
        closed = true;
        runtime.invalidate();
    }

    public String lastFailureUnloc() {
        return runtime.failureUnloc();
    }
}

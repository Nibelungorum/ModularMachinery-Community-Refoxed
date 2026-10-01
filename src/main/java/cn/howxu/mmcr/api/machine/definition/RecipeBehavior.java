package cn.howxu.mmcr.api.machine.definition;

import java.util.Objects;

/**
 * Callback strategy for machines driven by recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeBehavior implements MachineBehavior {
    private static final RecipeBehavior DEFAULTS = new Builder().build();

    private final MachineCallback idleStart;
    private final MachineCallback idleEnd;
    private final RecipeStartCallback beforeStart;
    private final RecipeTickCallback recipeTick;
    private final RecipeFinishCallback beforeFinish;
    private final MachineCallback preServerTick;
    private final MachineCallback postServerTick;
    private final boolean idleStartRegistered;
    private final boolean idleEndRegistered;
    private final boolean beforeStartRegistered;
    private final boolean recipeTickRegistered;
    private final boolean beforeFinishRegistered;
    private final boolean preServerTickRegistered;
    private final boolean postServerTickRegistered;

    private RecipeBehavior(Builder builder) {
        idleStart = builder.idleStart;
        idleEnd = builder.idleEnd;
        beforeStart = builder.beforeStart;
        recipeTick = builder.recipeTick;
        beforeFinish = builder.beforeFinish;
        preServerTick = builder.preServerTick;
        postServerTick = builder.postServerTick;
        idleStartRegistered = builder.idleStartRegistered;
        idleEndRegistered = builder.idleEndRegistered;
        beforeStartRegistered = builder.beforeStartRegistered;
        recipeTickRegistered = builder.recipeTickRegistered;
        beforeFinishRegistered = builder.beforeFinishRegistered;
        preServerTickRegistered = builder.preServerTickRegistered;
        postServerTickRegistered = builder.postServerTickRegistered;
    }

    public static RecipeBehavior defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Kind kind() {
        return Kind.RECIPE;
    }

    public MachineCallback idleStart() {
        return idleStart;
    }

    public boolean hasIdleStart() {
        return idleStartRegistered;
    }

    public MachineCallback idleEnd() {
        return idleEnd;
    }

    public boolean hasIdleEnd() {
        return idleEndRegistered;
    }

    public RecipeStartCallback beforeStart() {
        return beforeStart;
    }

    public boolean hasBeforeStart() {
        return beforeStartRegistered;
    }

    public RecipeTickCallback recipeTick() {
        return recipeTick;
    }

    public boolean hasRecipeTick() {
        return recipeTickRegistered;
    }

    public RecipeFinishCallback beforeFinish() {
        return beforeFinish;
    }

    public boolean hasBeforeFinish() {
        return beforeFinishRegistered;
    }

    public MachineCallback preServerTick() {
        return preServerTick;
    }

    public boolean hasPreServerTick() {
        return preServerTickRegistered;
    }

    public MachineCallback postServerTick() {
        return postServerTick;
    }

    public boolean hasPostServerTick() {
        return postServerTickRegistered;
    }

    public static final class Builder {
        private MachineCallback idleStart = context -> { };
        private MachineCallback idleEnd = context -> { };
        private RecipeStartCallback beforeStart = context -> { };
        private RecipeTickCallback recipeTick = context -> { };
        private RecipeFinishCallback beforeFinish = context -> { };
        private MachineCallback preServerTick = context -> { };
        private MachineCallback postServerTick = context -> { };
        private boolean idleStartRegistered;
        private boolean idleEndRegistered;
        private boolean beforeStartRegistered;
        private boolean recipeTickRegistered;
        private boolean beforeFinishRegistered;
        private boolean preServerTickRegistered;
        private boolean postServerTickRegistered;

        public Builder idleStart(MachineCallback callback) {
            idleStart = Objects.requireNonNull(callback, "idleStart");
            idleStartRegistered = true;
            return this;
        }

        public Builder idleEnd(MachineCallback callback) {
            idleEnd = Objects.requireNonNull(callback, "idleEnd");
            idleEndRegistered = true;
            return this;
        }

        public Builder beforeStart(RecipeStartCallback callback) {
            beforeStart = Objects.requireNonNull(callback, "beforeStart");
            beforeStartRegistered = true;
            return this;
        }

        public Builder recipeTick(RecipeTickCallback callback) {
            recipeTick = Objects.requireNonNull(callback, "recipeTick");
            recipeTickRegistered = true;
            return this;
        }

        public Builder beforeFinish(RecipeFinishCallback callback) {
            beforeFinish = Objects.requireNonNull(callback, "beforeFinish");
            beforeFinishRegistered = true;
            return this;
        }

        public Builder preServerTick(MachineCallback callback) {
            preServerTick = Objects.requireNonNull(callback, "preServerTick");
            preServerTickRegistered = true;
            return this;
        }

        public Builder postServerTick(MachineCallback callback) {
            postServerTick = Objects.requireNonNull(callback, "postServerTick");
            postServerTickRegistered = true;
            return this;
        }

        public RecipeBehavior build() {
            return new RecipeBehavior(this);
        }
    }
}

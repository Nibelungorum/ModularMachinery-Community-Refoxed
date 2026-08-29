package cn.howxu.mmcr.api.publicapi.machine;

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

    private RecipeBehavior(Builder builder) {
        idleStart = builder.idleStart;
        idleEnd = builder.idleEnd;
        beforeStart = builder.beforeStart;
        recipeTick = builder.recipeTick;
        beforeFinish = builder.beforeFinish;
        preServerTick = builder.preServerTick;
        postServerTick = builder.postServerTick;
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

    public MachineCallback idleEnd() {
        return idleEnd;
    }

    public RecipeStartCallback beforeStart() {
        return beforeStart;
    }

    public RecipeTickCallback recipeTick() {
        return recipeTick;
    }

    public RecipeFinishCallback beforeFinish() {
        return beforeFinish;
    }

    public MachineCallback preServerTick() {
        return preServerTick;
    }

    public MachineCallback postServerTick() {
        return postServerTick;
    }

    public static final class Builder {
        private MachineCallback idleStart = context -> { };
        private MachineCallback idleEnd = context -> { };
        private RecipeStartCallback beforeStart = context -> { };
        private RecipeTickCallback recipeTick = context -> { };
        private RecipeFinishCallback beforeFinish = context -> { };
        private MachineCallback preServerTick = context -> { };
        private MachineCallback postServerTick = context -> { };

        public Builder idleStart(MachineCallback callback) {
            idleStart = Objects.requireNonNull(callback, "idleStart");
            return this;
        }

        public Builder idleEnd(MachineCallback callback) {
            idleEnd = Objects.requireNonNull(callback, "idleEnd");
            return this;
        }

        public Builder beforeStart(RecipeStartCallback callback) {
            beforeStart = Objects.requireNonNull(callback, "beforeStart");
            return this;
        }

        public Builder recipeTick(RecipeTickCallback callback) {
            recipeTick = Objects.requireNonNull(callback, "recipeTick");
            return this;
        }

        public Builder beforeFinish(RecipeFinishCallback callback) {
            beforeFinish = Objects.requireNonNull(callback, "beforeFinish");
            return this;
        }

        public Builder preServerTick(MachineCallback callback) {
            preServerTick = Objects.requireNonNull(callback, "preServerTick");
            return this;
        }

        public Builder postServerTick(MachineCallback callback) {
            postServerTick = Objects.requireNonNull(callback, "postServerTick");
            return this;
        }

        public RecipeBehavior build() {
            return new RecipeBehavior(this);
        }
    }
}

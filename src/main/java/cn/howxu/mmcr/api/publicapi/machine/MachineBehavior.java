package cn.howxu.mmcr.api.publicapi.machine;

/**
 * Strategy that defines how a machine executes on the server.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineBehavior permits RecipeBehavior, TickBehavior {
    enum Kind {
        RECIPE,
        TICK
    }

    Kind kind();

    @FunctionalInterface
    interface MachineCallback {
        void accept(MachineBehaviorContext context);
    }

    @FunctionalInterface
    interface RecipeStartCallback {
        void accept(RecipeStartContext context);
    }

    @FunctionalInterface
    interface RecipeTickCallback {
        void accept(RecipeTickContext context);
    }

    @FunctionalInterface
    interface RecipeFinishCallback {
        void accept(RecipeFinishContext context);
    }
}

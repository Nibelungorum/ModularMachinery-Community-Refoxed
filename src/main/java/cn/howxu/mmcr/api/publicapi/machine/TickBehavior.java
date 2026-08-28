package cn.howxu.mmcr.api.publicapi.machine;

import java.util.Objects;

/**
 * Callback strategy for machines driven directly by server ticks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TickBehavior implements MachineBehavior {
    private static final TickBehavior DEFAULTS = new TickBehavior(context -> { });

    private final TickCallback serverTick;

    private TickBehavior(TickCallback serverTick) {
        this.serverTick = Objects.requireNonNull(serverTick, "serverTick");
    }

    public static TickBehavior defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Kind kind() {
        return Kind.TICK;
    }

    public TickCallback serverTick() {
        return serverTick;
    }

    public static final class Builder {
        private TickCallback serverTick = context -> { };

        public Builder serverTick(TickCallback callback) {
            serverTick = Objects.requireNonNull(callback, "serverTick");
            return this;
        }

        public TickBehavior build() {
            return new TickBehavior(serverTick);
        }
    }
}

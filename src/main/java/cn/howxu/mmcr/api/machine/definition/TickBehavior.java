package cn.howxu.mmcr.api.machine.definition;

import java.util.Objects;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;

/**
 * Callback strategy for machines driven directly by server ticks.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TickBehavior implements MachineBehavior {
    private static final TickBehavior DEFAULTS = new Builder().build();

    private final TickCallback serverTick;
    private final boolean serverTickRegistered;

    private TickBehavior(Builder builder) {
        serverTick = builder.serverTick;
        serverTickRegistered = builder.serverTickRegistered;
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

    public boolean hasServerTick() {
        return serverTickRegistered;
    }

    public CapabilityTickPhase capabilityTickPhase() {
        return CapabilityTickPhase.IDLE;
    }

    public static final class Builder {
        private TickCallback serverTick = context -> { };
        private boolean serverTickRegistered;

        public Builder serverTick(TickCallback callback) {
            serverTick = Objects.requireNonNull(callback, "serverTick");
            serverTickRegistered = true;
            return this;
        }

        public TickBehavior build() {
            return new TickBehavior(this);
        }
    }
}

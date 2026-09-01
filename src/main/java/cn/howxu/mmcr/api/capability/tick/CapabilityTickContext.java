package cn.howxu.mmcr.api.capability.tick;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable server-thread context supplied while planning capability tick work.
 *
 * @author howxu <dev@howxu.cn>
 */
public record CapabilityTickContext(long gameTime, CapabilityTickPhase phase,
                                    @Nullable RecipeTickContext recipeState, long parallelism,
                                    CapabilitySnapshot capabilitySnapshot,
                                    MachineBehaviorContext machineContext) {
    public CapabilityTickContext {
        if (parallelism <= 0L) throw new IllegalArgumentException("parallelism must be positive");
        phase = Objects.requireNonNull(phase, "phase");
        capabilitySnapshot = Objects.requireNonNull(capabilitySnapshot, "capabilitySnapshot");
        machineContext = Objects.requireNonNull(machineContext, "machineContext");
    }
}

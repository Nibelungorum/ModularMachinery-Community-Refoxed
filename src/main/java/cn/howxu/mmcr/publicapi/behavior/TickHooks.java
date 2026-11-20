package cn.howxu.mmcr.publicapi.behavior;

import org.jetbrains.annotations.ApiStatus;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** MMCR-provided direct-tick configuration handle. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface TickHooks {
    TickHooks serverTick(Consumer<TickContext> callback);

    /**
     * Constructs a fresh callback on first use per controller, e.g. {@code serverTick(HeaterTick::new)}.
     * Instance fields are not saved on unload; {@link MachineContext#dataStorage()} remains available when bound.
     */
    TickHooks serverTick(Supplier<? extends Consumer<TickContext>> factory);
}

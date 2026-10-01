package cn.howxu.mmcr.publicapi.behavior;

import org.jetbrains.annotations.ApiStatus;
import java.util.function.Consumer;

/** MMCR-provided direct-tick configuration handle. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface TickHooks { TickHooks serverTick(Consumer<TickContext> callback); }

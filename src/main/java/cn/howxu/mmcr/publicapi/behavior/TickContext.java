package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.publicapi.runtime.IoTransaction;
import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.Optional;

/** MMCR-provided direct tick context. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface TickContext extends MachineContext {
    int factoryThreadCount();
    long parallelism();
    Optional<Float> smartInterfaceValue(String name);
    Map<String, Float> smartInterfaceValues();
    /** Each call creates an independent one-shot IO plan. */
    IoTransaction ioPlan();
}

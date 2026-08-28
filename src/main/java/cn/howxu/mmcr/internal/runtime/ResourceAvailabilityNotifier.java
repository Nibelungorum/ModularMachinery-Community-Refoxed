package cn.howxu.mmcr.internal.runtime;

import org.jetbrains.annotations.Nullable;

/**
 * Reports a committed resource or connection change to a controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface ResourceAvailabilityNotifier {
    enum Reason { INPUT_AVAILABLE, ENERGY_AVAILABLE, OUTPUT_CAPACITY, MODULE_CONNECTION }

    void notifyAvailability(Reason reason, @Nullable Object resource);
}

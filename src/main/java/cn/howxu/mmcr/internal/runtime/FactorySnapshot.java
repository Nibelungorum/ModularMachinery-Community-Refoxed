package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder for the factory runtime state owned by the controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactorySnapshot(@Nullable FactoryControllerSnapshot controller) {

    public static FactorySnapshot empty() {
        return new FactorySnapshot(null);
    }
}

package cn.howxu.mmcr.api.capability.type;

import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.util.IOType;

import java.util.Optional;

/**
 * Context supplied when a capability factory creates a hosted capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CapabilityCreationContext {
    CapabilityHost host();

    IOType ioType();

    <T> Optional<T> service(Class<T> serviceType);

    Runnable onChanged();
}

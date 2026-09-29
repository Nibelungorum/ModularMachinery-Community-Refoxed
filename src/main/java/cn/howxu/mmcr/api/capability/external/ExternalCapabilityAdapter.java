package cn.howxu.mmcr.api.capability.external;

import cn.howxu.mmcr.api.capability.CapabilityType;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Bridges MMCR capabilities to an optionally available external capability API.
 * @author howxu <dev@howxu.cn>
 */
public interface ExternalCapabilityAdapter {
    ResourceLocation id();

    Set<CapabilityType> capabilityTypes();

    boolean isAvailable();

    void register(ExternalCapabilityContext context);
}

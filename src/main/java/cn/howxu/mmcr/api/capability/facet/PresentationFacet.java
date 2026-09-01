package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;

import java.util.List;

/**
 * Exposes immutable display entries for a capability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface PresentationFacet extends CapabilityFacet {
    List<CapabilityDisplay> displays(CapabilityView view);
}

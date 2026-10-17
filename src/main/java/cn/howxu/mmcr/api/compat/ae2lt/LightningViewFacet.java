package cn.howxu.mmcr.api.compat.ae2lt;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;

/** Internal ME lightning view; not an adjacent transfer capability.
 * @author howxu <dev@howxu.cn>
 */
public interface LightningViewFacet extends CapabilityFacet {
    LightningStorage storage();
}

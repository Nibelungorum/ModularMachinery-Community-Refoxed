package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;

/** Converts this family's resource identities at the server capture and commit boundaries.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningResourceValues {
    private LightningResourceValues() {}

    public static AsyncResourceValue lightning(LightningTier tier) {
        return new AsyncResourceValue(tier.requirementId(), "");
    }

    public static LightningTier lightning(AsyncResourceValue value) {
        if (!value.data().isEmpty()) throw new IllegalArgumentException("Lightning resources have no component data");
        return LightningTier.fromId(value.resourceId());
    }
}

package cn.howxu.mmcr.api.compat.ae2lt;

import net.minecraft.resources.ResourceLocation;

/** Native-independent lightning resource identities.
 * @author howxu <dev@howxu.cn>
 */
public enum LightningTier {
    HIGH_VOLTAGE("high_voltage"),
    EXTREME_HIGH_VOLTAGE("extreme_high_voltage");

    private final ResourceLocation requirementId;

    LightningTier(String path) {
        requirementId = ResourceLocation.fromNamespaceAndPath("ae2lt", path);
    }

    public ResourceLocation requirementId() { return requirementId; }
}

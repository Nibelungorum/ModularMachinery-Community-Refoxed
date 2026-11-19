package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import net.minecraft.resources.ResourceLocation;

/** Native animated lightning sprites on the 1.21.1 block atlas.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningSprites {
    private static final ResourceLocation HIGH_VOLTAGE = ResourceLocation.parse("ae2lt:item/high_voltage_lightning");
    private static final ResourceLocation EXTREME_HIGH_VOLTAGE = ResourceLocation.parse("ae2lt:item/extreme_high_voltage_lightning");

    private LightningSprites() {}

    public static ResourceLocation forTier(LightningTier tier) {
        return switch (tier) {
            case HIGH_VOLTAGE -> HIGH_VOLTAGE;
            case EXTREME_HIGH_VOLTAGE -> EXTREME_HIGH_VOLTAGE;
        };
    }
}

package cn.howxu.mmcr.compat.ae2lt.client;

import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

/** Native animated item sprites used for AE2LT recipe outputs.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningSprites {
    private static final AtlasSprite HIGH_VOLTAGE = itemSprite("item/high_voltage_lightning");
    private static final AtlasSprite EXTREME_HIGH_VOLTAGE = itemSprite("item/extreme_high_voltage_lightning");

    private LightningSprites() {}

    public static AtlasSprite forTier(LightningTier tier) {
        return switch (tier) {
            case HIGH_VOLTAGE -> HIGH_VOLTAGE;
            case EXTREME_HIGH_VOLTAGE -> EXTREME_HIGH_VOLTAGE;
        };
    }

    private static AtlasSprite itemSprite(String path) {
        return new AtlasSprite(AtlasIds.ITEMS, Identifier.fromNamespaceAndPath("ae2lt", path));
    }
}

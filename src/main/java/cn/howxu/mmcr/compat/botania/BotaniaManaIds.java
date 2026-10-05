package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.capability.CapabilityType;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable identities for the optional Botania mana integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaManaIds {
    public static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath("botania", "mana");
    public static final CapabilityType TYPE = new CapabilityType(MANA);
    public static final String INPUT = "botania_mana_input_pool";
    public static final String OUTPUT = "botania_mana_output_pool";
    public static final int CAPACITY = 1_000_000;

    private BotaniaManaIds() {
    }
}

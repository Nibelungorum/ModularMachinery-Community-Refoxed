package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.capability.CapabilityType;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable identities for the optional Ars Nouveau source integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ArsSourceIds {
    public static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source");
    public static final CapabilityType TYPE = new CapabilityType(SOURCE);
    public static final String INPUT = "ars_source_input_interface";
    public static final String OUTPUT = "ars_source_output_interface";
    public static final String MENU = "ars_source_port";

    private ArsSourceIds() {
    }
}

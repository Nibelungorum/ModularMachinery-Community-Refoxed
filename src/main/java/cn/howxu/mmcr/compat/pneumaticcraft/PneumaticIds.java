package cn.howxu.mmcr.compat.pneumaticcraft;

import net.minecraft.resources.ResourceLocation;

/** Stable optional integration identifiers.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticIds {
    public static final String MOD_ID = "pneumaticcraft";
    public static final ResourceLocation AIR = ResourceLocation.fromNamespaceAndPath(MOD_ID, "air");
    public static final String INPUT = "pneumaticcraft_air_input_interface";
    public static final String OUTPUT = "pneumaticcraft_air_output_interface";
    public static final String MENU = "pneumaticcraft_air_port";
    public static final ResourceLocation ADVANCED_PRESSURE_TUBE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "advanced_pressure_tube");

    public static boolean isPneumaticPorts(String id){
        return id.equals(INPUT) || id.equals(OUTPUT);
    }
    private PneumaticIds() {
    }
}

package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import net.minecraft.resources.Identifier;

/**
 * Stable recipe type identities used by the optional Mekanism bridge.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MekanismRecipeTypes {
    public static final Identifier CHEMICAL = MekanismPortFamilies.CHEMICAL;
    public static final Identifier HEAT_TEMPERATURE = MekanismPortFamilies.HEAT_TEMPERATURE;
    public static final Identifier HEAT = MekanismPortFamilies.HEAT;

    private MekanismRecipeTypes() {
    }

    public static void register() {
        MekanismBridge.get().registerRecipeTypes(CHEMICAL, HEAT_TEMPERATURE, HEAT);
    }
}

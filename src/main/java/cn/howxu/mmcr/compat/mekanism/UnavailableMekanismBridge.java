package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import net.minecraft.resources.ResourceLocation;

/**
 * Inert bridge used when Mekanism is unavailable.
 *
 * @author howxu <dev@howxu.cn>
 */
final class UnavailableMekanismBridge implements MekanismBridge {
    static final UnavailableMekanismBridge INSTANCE = new UnavailableMekanismBridge();

    private UnavailableMekanismBridge() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean supportsPortFamily(ResourceLocation familyId) {
        return false;
    }

    @Override
    public ResourceLocation unavailableReason() {
        return MekanismFailureReasons.MEKANISM_UNAVAILABLE.id();
    }

    @Override
    public void registerRecipeTypes(ResourceLocation chemical, ResourceLocation heatTemperature, ResourceLocation heat) {
        MekanismRecipeDeclarations.registerUnavailable();
    }
}

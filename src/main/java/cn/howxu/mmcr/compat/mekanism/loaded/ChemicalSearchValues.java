package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Converts loaded chemical identities to and from immutable worker values.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalSearchValues {
    private ChemicalSearchValues() {}

    public static AsyncResourceValue chemical(ChemicalStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Chemical resource must not be empty");
        return new AsyncResourceValue(ResourceLocation.parse(stack.getChemicalHolder().getRegisteredName()), "");
    }

    public static ChemicalStack chemical(AsyncResourceValue value) {
        Holder.Reference<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, value.resourceId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chemical resource: " + value.resourceId()));
        return new ChemicalStack(chemical, 1L);
    }
}

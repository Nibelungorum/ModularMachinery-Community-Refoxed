package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalResource;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

/** Converts this family's resource identities at the server capture and commit boundaries.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalResourceValues {
    private ChemicalResourceValues() {}

    public static AsyncResourceValue chemical(ChemicalResource resource) {
        return new AsyncResourceValue(Identifier.parse(resource.typeHolder().getRegisteredName()), "");
    }

    public static ChemicalResource chemical(AsyncResourceValue value) {
        Holder.Reference<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, value.resourceId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chemical resource: " + value.resourceId()));
        return ChemicalResource.of(chemical);
    }
}

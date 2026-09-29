package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalResource;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Converts native resources at the main-thread boundary of async planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NativeAsyncResourceValues {
    private NativeAsyncResourceValues() {
    }

    public static AsyncResourceValue item(ItemResource resource) {
        return new AsyncResourceValue(ResourceLocation.parse(resource.typeHolder().getRegisteredName()),
                patch(resource.getComponentsPatch()));
    }

    public static ItemResource item(AsyncResourceValue value) {
        var item = BuiltInRegistries.ITEM.getValue(value.resourceId());
        if (item == null) throw new IllegalArgumentException("Unknown item resource: " + value.resourceId());
        return ItemResource.of(item, patch(value.data()));
    }

    public static AsyncResourceValue fluid(FluidResource resource) {
        return new AsyncResourceValue(ResourceLocation.parse(resource.typeHolder().getRegisteredName()),
                patch(resource.getComponentsPatch()));
    }

    public static FluidResource fluid(AsyncResourceValue value) {
        var fluid = BuiltInRegistries.FLUID.getValue(value.resourceId());
        if (fluid == null) throw new IllegalArgumentException("Unknown fluid resource: " + value.resourceId());
        return FluidResource.of(fluid, patch(value.data()));
    }

    public static AsyncResourceValue chemical(ChemicalResource resource) {
        return new AsyncResourceValue(ResourceLocation.parse(resource.typeHolder().getRegisteredName()), "");
    }

    public static ChemicalResource chemical(AsyncResourceValue value) {
        Holder.Reference<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, value.resourceId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chemical resource: " + value.resourceId()));
        return ChemicalResource.of(chemical);
    }

    private static String patch(DataComponentPatch patch) {
        return DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch).getOrThrow().toString();
    }

    private static DataComponentPatch patch(String value) {
        return DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(value)).getOrThrow();
    }
}

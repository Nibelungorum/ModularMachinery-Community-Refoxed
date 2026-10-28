package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalResource;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Converts native resources at the main-thread boundary of async planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NativeAsyncResourceValues {
    private NativeAsyncResourceValues() {
    }

    public static AsyncResourceValue item(ItemResource resource) {
        return new AsyncResourceValue(Identifier.parse(resource.typeHolder().getRegisteredName()),
                patch(resource.getComponentsPatch()));
    }

    public static ItemResource item(AsyncResourceValue value) {
        var item = BuiltInRegistries.ITEM.getValue(value.resourceId());
        if (item == null) throw new IllegalArgumentException("Unknown item resource: " + value.resourceId());
        return ItemResource.of(item, patch(value.data()));
    }

    public static AsyncResourceValue fluid(FluidResource resource) {
        return new AsyncResourceValue(Identifier.parse(resource.typeHolder().getRegisteredName()),
                patch(resource.getComponentsPatch()));
    }

    public static FluidResource fluid(AsyncResourceValue value) {
        var fluid = BuiltInRegistries.FLUID.getValue(value.resourceId());
        if (fluid == null) throw new IllegalArgumentException("Unknown fluid resource: " + value.resourceId());
        return FluidResource.of(fluid, patch(value.data()));
    }

    public static AsyncResourceValue chemical(ChemicalResource resource) {
        return new AsyncResourceValue(Identifier.parse(resource.typeHolder().getRegisteredName()), "");
    }

    public static ChemicalResource chemical(AsyncResourceValue value) {
        Holder.Reference<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY.get(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, value.resourceId()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown chemical resource: " + value.resourceId()));
        return ChemicalResource.of(chemical);
    }

    public static AsyncResourceValue lightning(LightningTier tier) {
        return new AsyncResourceValue(tier.requirementId(), "");
    }

    public static LightningTier lightning(AsyncResourceValue value) {
        if (!value.data().isEmpty()) throw new IllegalArgumentException("Lightning resources have no component data");
        return LightningTier.fromId(value.resourceId());
    }

    private static String patch(DataComponentPatch patch) {
        if (patch.isEmpty()) return "{}";
        return DataComponentPatch.CODEC.encodeStart(componentOps(), patch).getOrThrow().toString();
    }

    private static DataComponentPatch patch(String value) {
        if (value.equals("{}")) return DataComponentPatch.EMPTY;
        return DataComponentPatch.CODEC.parse(componentOps(), JsonParser.parseString(value)).getOrThrow();
    }

    private static DynamicOps<JsonElement> componentOps() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? JsonOps.INSTANCE : server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }
}

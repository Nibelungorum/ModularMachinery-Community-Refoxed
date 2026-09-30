package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Converts native resources at the main-thread boundary of async planning.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NativeAsyncResourceValues {
    private NativeAsyncResourceValues() {
    }

    public static AsyncResourceValue item(ItemStack resource) {
        if (resource.isEmpty()) throw new IllegalArgumentException("Item resource must not be empty");
        return new AsyncResourceValue(BuiltInRegistries.ITEM.getKey(resource.getItem()), patch(resource.getComponentsPatch()));
    }

    public static ItemStack item(AsyncResourceValue value) {
        var item = BuiltInRegistries.ITEM.get(value.resourceId());
        if (item == null) throw new IllegalArgumentException("Unknown item resource: " + value.resourceId());
        ItemStack stack = new ItemStack(item);
        stack.applyComponents(patch(value.data()));
        return stack;
    }

    public static AsyncResourceValue fluid(FluidStack resource) {
        if (resource.isEmpty()) throw new IllegalArgumentException("Fluid resource must not be empty");
        return new AsyncResourceValue(BuiltInRegistries.FLUID.getKey(resource.getFluid()), patch(resource.getComponentsPatch()));
    }

    public static FluidStack fluid(AsyncResourceValue value) {
        var fluid = BuiltInRegistries.FLUID.get(value.resourceId());
        if (fluid == null) throw new IllegalArgumentException("Unknown fluid resource: " + value.resourceId());
        FluidStack stack = new FluidStack(fluid, 1);
        stack.applyComponents(patch(value.data()));
        return stack;
    }

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

    private static String patch(DataComponentPatch patch) {
        return DataComponentPatch.CODEC.encodeStart(JsonOps.INSTANCE, patch).getOrThrow().toString();
    }

    private static DataComponentPatch patch(String value) {
        return DataComponentPatch.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(value)).getOrThrow();
    }
}

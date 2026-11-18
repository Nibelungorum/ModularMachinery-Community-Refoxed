package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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

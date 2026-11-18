package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
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

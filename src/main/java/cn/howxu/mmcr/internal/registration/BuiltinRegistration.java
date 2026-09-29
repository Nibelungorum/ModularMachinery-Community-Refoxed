package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Internal helpers for MMCR's built-in declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltinRegistration {
    private static final String MOD_ID = "mmcr";

    private BuiltinRegistration() {
    }

    public static ResourceLocation id(String path) {
        return MMCR.id(path);
    }

    public static Supplier<? extends Block> controller(ResourceLocation machineId) {
        return () -> ModBlocks.controllerFor(machineId).get();
    }

    public static Supplier<? extends Block> block(String name) {
        if (name != null && name.indexOf(':') >= 0) return block(ResourceLocation.parse(name));
        return () -> ModBlocks.BLOCKS.get(name).get();
    }

    public static Supplier<? extends Block> block(ResourceLocation id) {
        if (MOD_ID.equals(id.getNamespace())) return block(id.getPath());
        return () -> BuiltInRegistries.BLOCK.getValue(id);
    }
}

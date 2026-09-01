package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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

    public static Identifier id(String path) {
        return MMCR.id(path);
    }

    public static Supplier<? extends Block> controller(Identifier machineId) {
        return () -> ModBlocks.controllerFor(machineId).get();
    }

    public static Supplier<? extends Block> block(String name) {
        if (name != null && name.indexOf(':') >= 0) return block(Identifier.parse(name));
        return () -> ModBlocks.BLOCKS.get(name).get();
    }

    public static Supplier<? extends Block> block(Identifier id) {
        if (MOD_ID.equals(id.getNamespace())) return block(id.getPath());
        return () -> BuiltInRegistries.BLOCK.getValue(id);
    }
}

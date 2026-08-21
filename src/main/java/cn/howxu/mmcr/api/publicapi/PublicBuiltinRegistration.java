package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/** Public registration adapter used by the optional built-in declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinRegistration {
    public static final String MOD_ID = "mmcr";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private PublicBuiltinRegistration() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Logger logger() {
        return LOGGER;
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

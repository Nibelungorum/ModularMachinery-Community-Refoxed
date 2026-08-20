package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Internal lifecycle bridge used by the structure registration event.
 * @author howxu <dev@howxu.cn>
 */
public final class ModifierRegistryBridge {
    private ModifierRegistryBridge() {
    }

    public static void install(Map<Identifier, ModifierDefinition> definitions) {
        ModifierRegistry.install(definitions);
    }
}

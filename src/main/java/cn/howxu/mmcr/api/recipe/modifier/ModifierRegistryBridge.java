package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Deprecated compatibility bridge for modifier snapshot installation.
 *
 * @deprecated use {@link ModifierRegistry#installSnapshot(Map)} instead; this
 * bridge remains only for existing API consumers.
 * @author howxu <dev@howxu.cn>
 */
@Deprecated
public final class ModifierRegistryBridge {
    private ModifierRegistryBridge() {
    }

    public static void install(Map<Identifier, ModifierDefinition> definitions) {
        ModifierRegistry.installSnapshot(definitions);
    }
}

package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import net.minecraft.resources.ResourceLocation;

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

    public static void install(Map<ResourceLocation, ModifierDefinition> definitions) {
        ModifierRegistry.installSnapshot(definitions);
    }
}

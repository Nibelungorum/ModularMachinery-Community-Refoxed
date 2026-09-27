package cn.howxu.mmcr.client.gui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Resolves client-side recipe-pool display names.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipePoolDisplayName {
    private RecipePoolDisplayName() {
    }

    public static Component component(Identifier poolId) {
        if (poolId == null) return Component.empty();
        String key = "recipe_pool." + poolId.getNamespace() + "." + poolId.getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(poolId.toString());
    }
}

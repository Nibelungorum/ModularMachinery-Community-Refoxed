package cn.howxu.mmcr.api.recipe.modifier;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModifierRegistry {

    private static final Map<String, AbstractModifierReplacement> REPLACEMENTS = new HashMap<>();

    private ModifierRegistry() {
    }

    public static void register(AbstractModifierReplacement replacement) {
        if (replacement == null) return;
        REPLACEMENTS.put(replacement.getModifierName(), replacement);
    }

    public static AbstractModifierReplacement get(String name) {
        return REPLACEMENTS.get(name);
    }

    public static List<AbstractModifierReplacement> all() {
        return List.copyOf(REPLACEMENTS.values());
    }

    public static void clear() {
        REPLACEMENTS.clear();
    }
}

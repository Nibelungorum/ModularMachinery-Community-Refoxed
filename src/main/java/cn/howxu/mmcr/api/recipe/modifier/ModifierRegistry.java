package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModifierRegistry {

    private static final Map<String, AbstractModifierReplacement> REPLACEMENTS = new HashMap<>();
    private static Map<Identifier, ModifierDefinition> DEFINITIONS = Map.of();

    private ModifierRegistry() {
    }

    static void register(AbstractModifierReplacement replacement) {
        if (replacement == null) return;
        REPLACEMENTS.put(replacement.getModifierName(), replacement);
    }

    public static AbstractModifierReplacement get(String name) {
        return REPLACEMENTS.get(name);
    }

    public static List<AbstractModifierReplacement> all() {
        return List.copyOf(REPLACEMENTS.values());
    }

    static void clear() {
        REPLACEMENTS.clear();
        DEFINITIONS = Map.of();
    }

    static void install(Map<Identifier, ModifierDefinition> definitions) {
        DEFINITIONS = Map.copyOf(definitions == null ? Map.of() : definitions);
    }

    public static ModifierDefinition get(Identifier id) {
        return DEFINITIONS.get(id);
    }

    public static Map<Identifier, ModifierDefinition> definitions() {
        return DEFINITIONS;
    }
}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * Complete built-in declarations exposed through the public API path.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinDefinitions {

    public static Map<Identifier, MachineDefinition> machineDefinitions() {
        Map<Identifier, MachineDefinition> result = new LinkedHashMap<>();
        return Map.copyOf(result);
    }

    public static Map<Identifier, MachineStructureDefinition> structureDefinitions() {
        Map<Identifier, MachineStructureDefinition> result = new LinkedHashMap<>();
        return Map.copyOf(result);
    }
    public static Map<Identifier, MachineRecipeDefinition> recipeDefinitions() {
        Map<Identifier, MachineRecipeDefinition> result = new LinkedHashMap<>();
        return Map.copyOf(result);
    }
}

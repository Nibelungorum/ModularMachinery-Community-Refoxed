package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical event for collecting static machine recipes.
 * @author howxu <dev@howxu.cn>
 */
public class MMCRMachineRecipesEvent extends Event implements IModBusEvent {
    private boolean frozen;
    private final Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipes = new LinkedHashMap<>();

    public void registerRecipe(MachineRecipeDefinition definition) {
        if (frozen) throw new IllegalStateException("Machine recipes are frozen");
        Objects.requireNonNull(definition, "definition");
        if (recipes.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate machine recipe: " + definition.id());
        }
    }

    public void freeze() {
        frozen = true;
    }

    public Map<net.minecraft.resources.Identifier, MachineRecipeDefinition> recipes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }
}

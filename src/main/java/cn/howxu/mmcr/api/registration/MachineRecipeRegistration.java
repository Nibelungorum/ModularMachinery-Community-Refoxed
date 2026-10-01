package cn.howxu.mmcr.api.registration;

import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Canonical collector for static machine recipes.
 * @author howxu <dev@howxu.cn>
 */
public class MachineRecipeRegistration {
    private boolean frozen;
    private final Map<ResourceLocation, MachineRecipeDefinition> recipes = new LinkedHashMap<>();

    public void registerRecipe(MachineRecipeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        requireAvailable(definition.id());
        recipes.put(definition.id(), definition);
    }

    public void registerRecipe(ResourceLocation id, Consumer<MachineRecipeBuilder> configuration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(configuration, "configuration");
        requireAvailable(id);
        try {
            MachineRecipeBuilder builder = MachineRecipeBuilder.recipe(id);
            configuration.accept(builder);
            registerRecipe(builder.build());
        } catch (RuntimeException exception) {
            throw new ApiRegistrationException("Invalid machine recipe " + id + ": " + exception.getMessage(), exception);
        }
    }

    private void requireAvailable(ResourceLocation id) {
        if (frozen) throw new ApiRegistrationException("Machine recipes are frozen: " + id);
        if (recipes.containsKey(id)) throw new ApiRegistrationException("Duplicate machine recipe: " + id);
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Map<ResourceLocation, MachineRecipeDefinition> recipes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
    }
}

package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import cn.howxu.mmcr.publicapi.registration.RecipeRegistrar;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** Static recipe event posted on NeoForge.EVENT_BUS after item components are bound.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineRecipesEvent extends Event implements IModBusEvent {
    private final RecipeRegistrar registrar;
    public RegisterMachineRecipesEvent() { this(RegistrationAdapters.recipes()); }
    public RegisterMachineRecipesEvent(RecipeRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }
    public RecipeRegistrar registrar() { return registrar; }
    public void registerRecipe(RecipeSpec recipe) { registrar.registerRecipe(recipe); }
    public void registerRecipe(ResourceLocation id, Consumer<RecipeDraft> configuration) { registrar.registerRecipe(id, configuration); }
    public Map<ResourceLocation, RecipeSpec> recipes() { return registrar.recipes(); }
}

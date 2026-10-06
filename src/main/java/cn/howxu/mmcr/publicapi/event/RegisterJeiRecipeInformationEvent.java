package cn.howxu.mmcr.publicapi.event;

import cn.howxu.mmcr.internal.api.facade.client.ClientRegistrationAdapters;
import cn.howxu.mmcr.publicapi.client.jei.RecipeInformation;
import cn.howxu.mmcr.publicapi.client.jei.RecipeInformationRegistrar;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Client event posted on NeoForge.EVENT_BUS during JEI category registration to add localized information.
 * Component arguments are snapshotted at registration; reading entries cannot mutate frozen metadata.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterJeiRecipeInformationEvent extends Event {
    private final RecipeInformationRegistrar registrar;
    public RegisterJeiRecipeInformationEvent() { registrar = ClientRegistrationAdapters.information(); }
    public RecipeInformationRegistrar registrar() { return registrar; }
    public void registerRecipePool(ResourceLocation id, String key, Object... args) { registrar.registerRecipePool(id, key, args); }
    public void registerRecipe(ResourceLocation id, String key, Object... args) { registrar.registerRecipe(id, key, args); }
    public List<RecipeInformation> entries() { return registrar.entries(); }
}

package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;

/** Installs public built-in declarations into the internal runtime models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinRuntime {
    private PublicBuiltinRuntime() {
    }

    public static void registerStructures(DynamicContentReloadService.Candidate candidate) {
        PublicBuiltinDefinitions.structureDefinitions().values().stream()
                .map(PublicMachineAdapter::toStructureDefinition)
                .forEach(candidate::registerStructure);
    }

    public static void registerRecipes(MMCRMachineRecipesEvent event) {
        PublicBuiltinDefinitions.recipeDefinitions().values().stream()
                .forEach(event::registerRecipe);
    }
}

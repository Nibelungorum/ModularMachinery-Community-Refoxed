package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;

/** Narrow internal bridge from public startup adapters to runtime registries.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicRegistryBridge {
    private PublicRegistryBridge() {
    }

    public static void registerMachine(MachineRegistration registration) {
        MachineDefinitions.register(registration);
    }

    public static void registerRecipe(MachineRecipe recipe) {
        RecipeRegistry.registerStatic(recipe);
    }
}

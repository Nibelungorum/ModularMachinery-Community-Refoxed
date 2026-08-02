package cn.howxu.mmcr.kubejs;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class MMCRKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MMCR_MACHINES", MachineRegistry.class);
        bindings.add("MMCR_RECIPES", RecipeRegistry.class);
    }
}

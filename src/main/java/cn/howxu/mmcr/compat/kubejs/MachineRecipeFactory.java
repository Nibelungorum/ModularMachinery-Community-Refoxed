package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;

public final class MachineRecipeFactory {
    public static final KubeRecipeFactory INSTANCE = new KubeRecipeFactory(MMCR.id("machine_recipe"), KubeRecipe.class, KubeRecipe::new);

    private MachineRecipeFactory() {
    }
}

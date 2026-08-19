package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;

import net.minecraft.resources.Identifier;

public final class MachineRecipeFactory {
    public static final Identifier TYPE = MMCR.id("machine_recipe");
    public static final KubeRecipeFactory INSTANCE = new KubeRecipeFactory(TYPE, KubeRecipe.class, KubeRecipe::new);

    private MachineRecipeFactory() {
    }

    public static boolean allowPartialOutputs(KubeRecipe recipe) {
        JsonObject json = recipe.json;
        return json != null && json.has("allow_partial_outputs") && json.get("allow_partial_outputs").getAsBoolean();
    }
}

package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class Plugin implements dev.latvian.mods.kubejs.plugin.KubeJSPlugin {
    static final String RECIPE_BUILDER_BINDING = "MMCR_RECIPE_BUILDER";
    static final Class<MachineRecipeBuilderJS> RECIPE_BUILDER_CLASS = MachineRecipeBuilderJS.class;

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MMCR_MACHINES", MachineRegistry.class);
        bindings.add("MMCR_RECIPES", RecipeRegistry.class);
        bindings.add("MMCR_MACHINE_BUILDER", MachineBuilderJS.class);
        bindings.add("MMCR_STRUCTURE_BUILDER", MachineStructureBuilderJS.class);
        bindings.add(RECIPE_BUILDER_BINDING, RECIPE_BUILDER_CLASS);
    }

    @Override
    public void registerRecipeFactories(RecipeFactoryRegistry registry) {
        registry.register(MachineRecipeFactory.INSTANCE);
    }

    @Override
    public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        registry.unit(MachineRecipeSchema.JSON_ELEMENT);
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        MachineRecipeSchema.register(registry);
    }
}

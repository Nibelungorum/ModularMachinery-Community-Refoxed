package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import org.nibelungorum.DefaultMachineLevels;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;

public class Plugin implements dev.latvian.mods.kubejs.plugin.KubeJSPlugin {
    static final String RECIPE_BUILDER_BINDING = "MMCR_RECIPE_BUILDER";
    static final Class<MachineRecipeBuilderJS> RECIPE_BUILDER_CLASS = MachineRecipeBuilderJS.class;

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.STARTUP) {
            MachineLevelRegistry.beginRegistration();
            DefaultMachineLevels.register();
        }
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.STARTUP) {
            MachineLevelRegistry.freezeRegistration();
        }
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MMCR_MACHINES", MachineRegistry.class);
        bindings.add("MMCR_RECIPES", RecipeRegistry.class);
        bindings.add("MMCR_MACHINE_BUILDER", MachineBuilderJS.class);
        bindings.add("MMCR_STRUCTURE_BUILDER", MachineStructureBuilderJS.class);
        bindings.add("MMCR_LEVEL_TYPE_BUILDER", LevelTypeBuilderJS.class);
        bindings.add("MMCR_MACHINE_LEVEL_BUILDER", MachineLevelBuilderJS.class);
        bindings.add("MMCR", new Bindings());
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

    /**
     * Startup and server-script helpers exposed as {@code MMCR}.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static class Bindings {
        public LevelTypes levelTypes() {
            return new LevelTypes();
        }

        public Levels levels() {
            return new Levels();
        }

        public LevelSlot levelSlot(String typeId) {
            var id = net.minecraft.resources.Identifier.parse(typeId);
            if (MachineLevelRegistry.getType(id) == null) {
                throw new IllegalArgumentException("Unknown machine level type: " + typeId);
            }
            return new LevelSlot(id);
        }
    }
    public static class LevelTypes {
        public LevelTypeBuilderJS create(String id) {
            return new LevelTypeBuilderJS(id);
        }
    }

    public static class Levels {
        public MachineLevelBuilderJS create(String id) {
            return new MachineLevelBuilderJS(id);
        }
    }
}

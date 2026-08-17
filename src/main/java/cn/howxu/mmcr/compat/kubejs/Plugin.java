package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.loading.FMLLoader;

import java.util.IdentityHashMap;
import java.util.Map;

public class Plugin implements dev.latvian.mods.kubejs.plugin.KubeJSPlugin {
    static final String RECIPE_BUILDER_BINDING = "MMCR_RECIPE_BUILDER";
    static final Class<MachineRecipeBuilderJS> RECIPE_BUILDER_CLASS = MachineRecipeBuilderJS.class;
    private static final Map<Object, ServerReload> SERVER_RELOADS = new IdentityHashMap<>();

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            beginServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            MachineLevelRegistry.beginRegistration();
            registerDevelopmentMachineLevels();
        }
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            completeServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            MachineLevelRegistry.freezeRegistration();
        }
    }

    private record ServerReload(KubeJSContentReloadTransaction transaction, int errorCount) {
    }

    static void beginServerReload(Object manager, int errorCount) {
        var transaction = new KubeJSContentReloadTransaction();
        SERVER_RELOADS.put(manager, new ServerReload(transaction, errorCount));
        KubeJSContentReloadTransaction.activate(transaction);
    }

    static void completeServerReload(Object manager, int errorCount) {
        ServerReload reload = SERVER_RELOADS.remove(manager);
        try {
            if (reload != null && errorCount == reload.errorCount()) {
                reload.transaction().commit();
            }
        } finally {
            KubeJSContentReloadTransaction.deactivate();
        }
    }

    static void abortServerReload(Object manager) {
        SERVER_RELOADS.remove(manager);
        KubeJSContentReloadTransaction.deactivate();
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MMCR_API", new KubeJSApi());
        bindings.add("MMCR_MACHINE_DEFINITIONS", MachineDefinitions.class);
        bindings.add("MMCR_MACHINE_STRUCTURES", MachineStructureRegistry.class);
        bindings.add("MMCR_RECIPE_REGISTRY", RecipeRegistry.class);
        bindings.add("MMCR_BLOCK_ARRAY", BlockArray.class);
        bindings.add("MMCR_BLOCK_PREDICATE", BlockPredicate.class);
        bindings.add("MMCR_MACHINE_REGISTRATION", MachineRegistration.class);
        bindings.add("MMCR_STRUCTURE_DEFINITION", MachineStructureDefinition.class);
        bindings.add("MMCR_PORT_REQUIREMENTS", PortRequirementSpec.class);
        bindings.add("MMCR_PORT_TIER_REQUIREMENTS", PortTierRequirementSpec.class);
        bindings.add("MMCR_MACHINE_INGREDIENT", MachineIngredient.class);
        bindings.add("MMCR_MACHINE_RECIPE", MachineRecipe.class);
        bindings.add("MMCR_RECIPE_MODIFIER", RecipeModifier.class);
        bindings.add("MMCR_SINGLE_BLOCK_MODIFIER", SingleBlockModifierReplacement.class);
        bindings.add("MMCR_LEVEL_REQUIREMENT", LevelRequirement.class);
        bindings.add("MMCR_SMART_INTERFACE_REQUIREMENT", SmartInterfaceRequirement.class);
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
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(SmartInterfaceEvents.group());
    }

    static Map<String, String> events() {
        return Map.of(SmartInterfaceEvents.UPDATED_ID, SmartInterfaceEvents.UPDATED_ID);
    }

    private static void registerDevelopmentMachineLevels() {
        if (FMLLoader.getCurrent().isProduction()) return;
        try {
            Class.forName("org.nibelungorum.DefaultMachineLevels").getMethod("register").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // Development machine levels are absent from the production JAR.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register default machine levels", e);
        }
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

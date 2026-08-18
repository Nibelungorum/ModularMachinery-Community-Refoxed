package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
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
    private static final Map<Object, ServerReload> SERVER_RELOADS = new IdentityHashMap<>();

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            beginServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            beginStartupRegistryPhase();
            MachineLevelRegistry.beginRegistration();
            registerDevelopmentMachineLevels();
        }
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            MMCREvents.postServer();
            completeServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            MMCREvents.postStartup();
            MachineLevelRegistry.freezeRegistration();
            freezeStartupRegistryPhase();
        }
    }

    private static void beginStartupRegistryPhase() {
        if (!MachineDefinitions.allRegistrations().isEmpty() && !MachineDefinitions.isRegistryPhaseOpen()) return;
        MachineDefinitions.beginRegistryPhase();
    }

    private static void freezeStartupRegistryPhase() {
        if (!MachineDefinitions.isRegistryPhaseOpen()) return;
        MachineDefinitions.bootstrapBuiltins();
        MachineDefinitions.freezeRegistryPhase();
    }

    static void beginStartupRegistryPhaseForTesting() {
        beginStartupRegistryPhase();
    }

    static void freezeStartupRegistryPhaseForTesting() {
        freezeStartupRegistryPhase();
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
            if (reload != null && errorCount == reload.errorCount() && !reload.transaction().isEmpty()) {
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
        bindings.add("mmcrAPI", new KubeJSApi());
        bindings.add("MMCREvents", new EventGroupWrapper(bindings.type(), MMCREvents.group()));
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MMCREvents.group());
        registry.register(SmartInterfaceEvents.group());
    }

    static Map<String, String> events() {
        Map<String, String> events = new java.util.LinkedHashMap<>();
        events.putAll(MMCREvents.events());
        events.put(SmartInterfaceEvents.UPDATED_ID, SmartInterfaceEvents.UPDATED_ID);
        return events;
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

}

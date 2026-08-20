package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.network.RuntimeContentServerBridge;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class Plugin implements dev.latvian.mods.kubejs.plugin.KubeJSPlugin {
    private static final Map<Object, ServerReload> SERVER_RELOADS = new IdentityHashMap<>();
    private static Consumer<cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot> currentServerSync =
            RuntimeContentServerBridge::sendToCurrentServer;

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            beginServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            beginStartupRegistryPhase();
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
        }
    }

    private static void beginStartupRegistryPhase() {
        PublicApiBootstrap.begin();
    }

    static void beginStartupRegistryPhaseForTesting() {
        beginStartupRegistryPhase();
    }

    static void freezeStartupRegistryPhaseForTesting() {
        if (!ContentRegistrationCoordinator.isCommitted()) {
            ContentRegistrationCoordinator.commitStartup();
        } else if (MachineDefinitions.isRegistryPhaseOpen()) {
            MachineDefinitions.freezeRegistryPhase();
        }
    }

    static void registerStartupMachine(MachineDefinition definition) {
        ContentRegistrationCoordinator.collectMachine(definition);
    }

    private record ServerReload(KubeJSContentReloadTransaction transaction, int errorCount) {
    }

    static void beginServerReload(Object manager, int errorCount) {
        var transaction = new KubeJSContentReloadTransaction();
        SERVER_RELOADS.put(manager, new ServerReload(transaction, errorCount));
        KubeJSContentReloadTransaction.activate(transaction);
    }

    static void completeServerReload(Object manager, int errorCount) {
        completeServerReload(manager, errorCount, currentServerSync);
    }

    static void completeServerReload(Object manager, int errorCount, MinecraftServer server) {
        completeServerReload(manager, errorCount, snapshot -> {
            if (server != null) RuntimeContentSync.sendToAll(server, snapshot);
        });
    }

    static void completeServerReloadForTesting(Object manager, int errorCount, Runnable afterCommit) {
        completeServerReload(manager, errorCount, afterCommit);
    }

    private static void completeServerReload(Object manager, int errorCount, BooleanSupplier afterCommit) {
        Runnable sync = afterCommit::getAsBoolean;
        completeServerReload(manager, errorCount, sync);
    }

    private static void completeServerReload(Object manager, int errorCount, Runnable afterCommit) {
        completeServerReload(manager, errorCount, snapshot -> afterCommit.run());
    }

    private static void completeServerReload(Object manager, int errorCount,
                                              Consumer<cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot> afterCommit) {
        ServerReload reload = SERVER_RELOADS.remove(manager);
        try {
            if (reload != null && errorCount == reload.errorCount()) {
                afterCommit.accept(reload.transaction().commit().snapshot());
            }
        } finally {
            KubeJSContentReloadTransaction.deactivate();
        }
    }

    static void abortServerReload(Object manager) {
        SERVER_RELOADS.remove(manager);
        KubeJSContentReloadTransaction.deactivate();
    }

    static void setCurrentServerForTesting(MinecraftServer server) {
        currentServerSync = snapshot -> {
            if (server == null) return;
            RuntimeContentSync.sendToAll(server, snapshot);
        };
    }

    static void clearCurrentServerForTesting() {
        currentServerSync = RuntimeContentServerBridge::sendToCurrentServer;
    }

    static void setCurrentServerSyncForTesting(BooleanSupplier sync) {
        currentServerSync = ignored -> sync.getAsBoolean();
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("MMCR", new MMCRKubeJS());
        bindings.add("MMCREvents", new EventGroupWrapper(bindings.type(), MMCREvents.group()));
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MMCREvents.group());
        registry.register(SmartInterfaceEvents.group());
    }

    static Map<String, String> events() {
        Map<String, String> events = new LinkedHashMap<>();
        events.putAll(MMCREvents.events());
        events.put(SmartInterfaceEvents.UPDATED_ID, SmartInterfaceEvents.UPDATED_ID);
        return events;
    }

    private static void registerDevelopmentMachineLevels() {
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

package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.internal.network.RuntimeContentServerBridge;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.registration.ContentRegistrationCoordinator;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import dev.latvian.mods.kubejs.event.EventGroupWrapper;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeFactoryRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class Plugin implements KubeJSPlugin {
    private static boolean startupScriptsLoaded;
    private static final Map<Object, ServerReload> SERVER_RELOADS = new IdentityHashMap<>();
    private static final Map<Object, Integer> CLIENT_RELOADS = new IdentityHashMap<>();
    private static Consumer<RuntimeContentSnapshot> currentServerSync =
            RuntimeContentServerBridge::sendToCurrentServer;

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) {
            beginServerReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.CLIENT) {
            beginClientReload(manager, manager.scriptType.console.errors.size());
        }
        if (manager.scriptType == ScriptType.STARTUP) {
            StartupContentRegistration.bindItemComponentsForEarlyRegistration();
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
            startupScriptsLoaded = true;
            StartupContentRegistration.completeKubeJSStartupIfReady();
        }
        if (manager.scriptType == ScriptType.CLIENT) {
            completeClientReload(manager, isJeiLoaded(), MMCREvents::postClient,
                    () -> manager.scriptType.console.errors.size());
        }
    }

    public static boolean startupScriptsLoaded() {
        return startupScriptsLoaded;
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
        StartupContentRegistration.registerKubeJSStartupMachine(definition);
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
                                              Consumer<RuntimeContentSnapshot> afterCommit) {
        ServerReload reload = SERVER_RELOADS.remove(manager);
        try {
            if (reload != null && (errorCount == reload.errorCount() || reload.transaction().hasPublishableContent())) {
                var committed = reload.transaction().commit();
                committed.result().errors().forEach(error -> MMCR.LOG.warn(
                        "Skipping KubeJS recipe {} at {}: {}", error.recipeId(), error.path(), error.getMessage()));
                afterCommit.accept(committed.snapshot());
            }
        } finally {
            KubeJSContentReloadTransaction.deactivate();
        }
    }

    static void abortServerReload(Object manager) {
        SERVER_RELOADS.remove(manager);
        KubeJSContentReloadTransaction.deactivate();
    }

    static void beginClientReload(Object manager, int errorCount) {
        CLIENT_RELOADS.put(manager, errorCount);
    }

    static void completeClientReloadForTesting(Object manager, boolean jeiAvailable,
                                               Consumer<RecipeInformationEventJS> publisher,
                                               IntSupplier errorCount) {
        completeClientReload(manager, jeiAvailable, publisher, errorCount);
    }

    private static void completeClientReload(Object manager, boolean jeiAvailable,
                                             Consumer<RecipeInformationEventJS> publisher,
                                             IntSupplier errorCount) {
        Integer initialErrors = CLIENT_RELOADS.remove(manager);
        if (initialErrors == null) return;
        RecipeInformationEventJS event = new RecipeInformationEventJS(jeiAvailable);
        publisher.accept(event);
        if (jeiAvailable && errorCount.getAsInt() == initialErrors) {
            RecipeInformationRegistry.replaceKubeJS(event.entries());
        }
    }

    private static boolean isJeiLoaded() {
        return ModList.get().isLoaded("jei");
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
        registry.register(MachineRecipeSchema.JSON_ELEMENT.type());
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        MachineRecipeSchema.register(registry);
    }

}

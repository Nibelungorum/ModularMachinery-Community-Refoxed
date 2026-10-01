package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.internal.api.facade.registration.RegistrationAdapters;
import cn.howxu.mmcr.api.machine.definition.MachineDefinition;
import cn.howxu.mmcr.compat.kubejs.Plugin;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineDefinitionProviders;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Set;
import java.util.function.Consumer;

/** Owns startup content collection and its delayed completion lifecycle.
 * @author howxu &lt;dev@howxu.cn&gt;
 */
public final class StartupContentRegistration {
    private static StartupPhase startupPhase = StartupPhase.NOT_STARTED;
    private static boolean structureCollectionDeferred;
    private static boolean productionStructuresInitialized;
    private static boolean productionStructuresCollected;
    private static RegisterMachineDefinitionsEvent pendingProductionDefinitions;
    private static boolean productionRecipesCollecting;
    private static boolean productionRecipesCollected;

    private StartupContentRegistration() {
    }

    public static void registerProduction() {
        registerProduction(true, true, NeoForge.EVENT_BUS);
    }

    public static void registerProductionForModStartup() {
        registerProductionForModStartup(NeoForge.EVENT_BUS);
    }

    public static void registerProductionForModStartup(IEventBus eventBus) {
        if (startupPhase == StartupPhase.COLLECTING || startupPhase == StartupPhase.COMMITTED) return;
        startupPhase = StartupPhase.COLLECTING;
        structureCollectionDeferred = false;
        productionStructuresInitialized = false;
        productionStructuresCollected = false;
        productionRecipesCollected = false;
        productionRecipesCollecting = false;
        PublicApiBootstrap.begin();
        ContentRegistrationCoordinator.beginStartup();
        RegisterMachineDefinitionsEvent definitions = new RegisterMachineDefinitionsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        registerGameTestBuiltins("registerMachineDefinitions",
                new Class<?>[]{RegisterMachineDefinitionsEvent.class}, definitions);
        eventBus.post(definitions);
        registerDynamicControllers(definitions.definitions().keySet());
        RegistrationAdapters.freeze(definitions);
        ContentRegistrationCoordinator.collectMachines(RegistrationAdapters.core(definitions));
        pendingProductionDefinitions = definitions;
    }

    public static void completeProductionForModStartup(IEventBus eventBus) {
        if (pendingProductionDefinitions == null || productionStructuresInitialized) return;
        productionStructuresInitialized = true;
        boolean initialized = false;
        try {
            boolean deferStructures = ModList.get() != null && ModList.get().isLoaded("kubejs")
                    && !Plugin.startupScriptsLoaded();
            bindItemComponentsForEarlyRegistration();
            RegisterMachineStructuresEvent structures = RegistrationAdapters.prepare(
                    pendingProductionDefinitions.definitions().keySet());
            registerGameTestBuiltins("registerMachineStructures",
                    new Class<?>[]{RegisterMachineStructuresEvent.class}, structures);
            eventBus.post(structures);
            structureCollectionDeferred = deferStructures;
            if (!deferStructures) {
                RegistrationAdapters.freeze(structures);
                ContentRegistrationCoordinator.collectStructures(RegistrationAdapters.core(structures));
                ContentRegistrationCoordinator.commitStructures();
                productionStructuresCollected = true;
            }
            initialized = true;
        } finally {
            if (!initialized) {
                productionStructuresInitialized = false;
                structureCollectionDeferred = false;
                productionStructuresCollected = false;
            }
        }
        tryCommitProductionStartup();
    }

    public static void completeProductionRecipesAfterComponentsBound() {
        completeProductionRecipesAfterComponentsBound(NeoForge.EVENT_BUS);
    }

    public static void completeProductionRecipesAfterComponentsBound(IEventBus eventBus) {
        if (pendingProductionDefinitions == null || productionRecipesCollecting || productionRecipesCollected) return;
        productionRecipesCollecting = true;
        boolean collected = false;
        try {
            RegisterMachineRecipesEvent recipes = new RegisterMachineRecipesEvent();
            registerGameTestBuiltins("registerRecipes", new Class<?>[]{RegisterMachineRecipesEvent.class}, recipes);
            eventBus.post(recipes);
            RegistrationAdapters.freeze(recipes);
            ContentRegistrationCoordinator.collectRecipes(RegistrationAdapters.core(recipes));
            productionRecipesCollected = true;
            collected = true;
        } finally {
            productionRecipesCollecting = false;
            if (!collected) productionRecipesCollected = false;
        }
        tryCommitProductionStartup();
    }

    private static void tryCommitProductionStartup() {
        if (pendingProductionDefinitions == null || !productionStructuresInitialized || !productionStructuresCollected
                || productionRecipesCollecting || !productionRecipesCollected) return;
        ContentRegistrationCoordinator.commitRecipes();
        registerDynamicControllers(MachineDefinitions.effectiveSnapshot().keySet());
        pendingProductionDefinitions = null;
        startupPhase = StartupPhase.COMMITTED;
    }

    private static void registerProduction(boolean begin, boolean commit, IEventBus eventBus) {
        registerProduction(begin, commit, false, eventBus);
    }

    private static void registerProduction(boolean begin, boolean commit, boolean deferStructures, IEventBus eventBus) {
        registerStartupContent(
                definitions -> {
                    registerGameTestBuiltins("registerMachineDefinitions",
                            new Class<?>[]{RegisterMachineDefinitionsEvent.class}, definitions);
                },
                structures -> {
                    registerGameTestBuiltins("registerMachineStructures",
                            new Class<?>[]{RegisterMachineStructuresEvent.class}, structures);
                },
                recipes -> {
                    registerGameTestBuiltins("registerRecipes",
                            new Class<?>[]{RegisterMachineRecipesEvent.class}, recipes);
                }, begin, commit, deferStructures, eventBus);
    }

    public static void registerForTesting() {
        registerForTesting(event -> { }, event -> { }, event -> { });
    }

    public static void registerForTesting(Consumer<MachineDefinitionRegistration> definitionsSource,
                                          Consumer<StructureRegistration> structuresSource,
                                          Consumer<MachineRecipeRegistration> recipesSource) {
        registerStartupContent(event -> definitionsSource.accept(RegistrationAdapters.core(event)),
                event -> structuresSource.accept(RegistrationAdapters.core(event)),
                event -> recipesSource.accept(RegistrationAdapters.core(event)), NeoForge.EVENT_BUS);
    }

    /** Exercises the production event contracts while keeping the core testing seam available. */
    public static void registerPublicForTesting(Consumer<RegisterMachineDefinitionsEvent> definitionsSource,
                                               Consumer<RegisterMachineStructuresEvent> structuresSource,
                                               Consumer<RegisterMachineRecipesEvent> recipesSource) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, NeoForge.EVENT_BUS);
    }

    public static void completeKubeJSStartup() {
        if (ContentRegistrationCoordinator.isCommitted()) return;
        if (structureCollectionDeferred) {
            ContentRegistrationCoordinator.collectStructures(StructureRegistration.current());
            ContentRegistrationCoordinator.commitStructures();
            structureCollectionDeferred = false;
            productionStructuresCollected = true;
        }
        if (pendingProductionDefinitions != null) {
            tryCommitProductionStartup();
            return;
        }
        ContentRegistrationCoordinator.commitStartup();
        registerDynamicControllers(MachineDefinitions.effectiveSnapshot().keySet());
        startupPhase = StartupPhase.COMMITTED;
    }

    public static void completeKubeJSStartupIfReady() {
        if (ContentRegistrationCoordinator.isCommitted()) return;
        if (startupPhase == StartupPhase.COLLECTING || startupPhase == StartupPhase.REGISTERS_ATTACHED) {
            completeKubeJSStartup();
        }
    }

    public static void registerKubeJSStartupMachine(MachineDefinition definition) {
        ContentRegistrationCoordinator.collectMachine(definition);
        registerDynamicControllers(Set.of(definition.id()));
    }

    public static String startupPhaseForTesting() {
        return startupPhase.name();
    }

    public static void resetForTesting() {
        startupPhase = StartupPhase.NOT_STARTED;
        structureCollectionDeferred = false;
        productionStructuresInitialized = false;
        productionStructuresCollected = false;
        pendingProductionDefinitions = null;
        productionRecipesCollecting = false;
        productionRecipesCollected = false;
    }

    public static void markCollectingForTesting() {
        startupPhase = StartupPhase.COLLECTING;
    }

    public static void invokeOptionalSourceForTesting(String className, String methodName,
                                                       Class<?>[] parameterTypes, Object... arguments) {
        GameTestRegistration.invokeOptionalSourceForTesting(className, methodName, parameterTypes, arguments);
    }

    public static void markRegistersAttached() {
        startupPhase = StartupPhase.REGISTERS_ATTACHED;
    }

    private static void registerStartupContent(
            Consumer<RegisterMachineDefinitionsEvent> definitionsSource,
            Consumer<RegisterMachineStructuresEvent> structuresSource,
            Consumer<RegisterMachineRecipesEvent> recipesSource) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, NeoForge.EVENT_BUS);
    }

    private static void registerStartupContent(
            Consumer<RegisterMachineDefinitionsEvent> definitionsSource,
            Consumer<RegisterMachineStructuresEvent> structuresSource,
            Consumer<RegisterMachineRecipesEvent> recipesSource,
            IEventBus eventBus) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, true, true, false, eventBus);
    }

    private static void registerStartupContent(
            Consumer<RegisterMachineDefinitionsEvent> definitionsSource,
            Consumer<RegisterMachineStructuresEvent> structuresSource,
            Consumer<RegisterMachineRecipesEvent> recipesSource,
            boolean begin,
            boolean commit,
            boolean deferStructures,
            IEventBus eventBus) {
        startupPhase = StartupPhase.COLLECTING;
        PublicApiBootstrap.begin();
        if (begin) ContentRegistrationCoordinator.beginStartup();
        RegisterMachineDefinitionsEvent definitions = new RegisterMachineDefinitionsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        definitionsSource.accept(definitions);
        eventBus.post(definitions);
        registerDynamicControllers(definitions.definitions().keySet());
        RegistrationAdapters.freeze(definitions);
        ContentRegistrationCoordinator.collectMachines(RegistrationAdapters.core(definitions));

        bindItemComponentsForEarlyRegistration();
        RegisterMachineStructuresEvent structures = RegistrationAdapters.prepare(definitions.definitions().keySet());
        structuresSource.accept(structures);
        eventBus.post(structures);
        structureCollectionDeferred = deferStructures;
        if (!deferStructures) {
            RegistrationAdapters.freeze(structures);
            ContentRegistrationCoordinator.collectStructures(RegistrationAdapters.core(structures));
        }
        RegisterMachineRecipesEvent recipes = new RegisterMachineRecipesEvent();
        recipesSource.accept(recipes);
        eventBus.post(recipes);
        RegistrationAdapters.freeze(recipes);
        ContentRegistrationCoordinator.collectRecipes(RegistrationAdapters.core(recipes));
        if (commit) {
            ContentRegistrationCoordinator.commitStartup();
            startupPhase = StartupPhase.COMMITTED;
        }
    }

    private static void registerDynamicControllers(Set<ResourceLocation> machineIds) {
        ModBlocks.registerMachineControllers(machineIds);
        ModBlockEntities.registerMachineControllers(machineIds);
        ModItems.registerMachineControllerItems(machineIds);
    }

    /**
     * Ensures item component maps are available to startup declarations.
     */
    public static void bindItemComponentsForEarlyRegistration() {
        for (Item item : BuiltInRegistries.ITEM) {
            item.components();
        }
    }

    private static void registerGameTestBuiltins(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        GameTestRegistration.invokeOptionalSourceForTesting("cn.howxu.mmcr.GameTestRegistry", methodName,
                parameterTypes, arguments);
    }

    private enum StartupPhase {
        NOT_STARTED, COLLECTING, COMMITTED, REGISTERS_ATTACHED
    }
}

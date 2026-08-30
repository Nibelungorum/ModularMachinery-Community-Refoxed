package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.compat.kubejs.Plugin;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineDefinitionProviders;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.material.Fluids;
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
    private static MMCRMachineDefinationsEvent pendingProductionDefinitions;

    private StartupContentRegistration() {
    }

    public static void registerProduction() {
        registerProduction(true, true, NeoForge.EVENT_BUS);
    }

    public static void registerProductionForModStartup() {
        registerProductionForModStartup(NeoForge.EVENT_BUS);
    }

    public static void registerProductionForModStartup(IEventBus eventBus) {
        startupPhase = StartupPhase.COLLECTING;
        PublicApiBootstrap.begin();
        ContentRegistrationCoordinator.beginStartup();
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        registerGameTestBuiltins("registerMachineDefinitions",
                new Class<?>[]{MMCRMachineDefinationsEvent.class}, definitions);
        eventBus.post(definitions);
        registerDynamicControllers(definitions.definitions().keySet());
        definitions.freeze();
        ContentRegistrationCoordinator.collectMachines(definitions);
        pendingProductionDefinitions = definitions;
    }

    public static void completeProductionForModStartup(IEventBus eventBus) {
        if (pendingProductionDefinitions == null) return;
        boolean deferStructures = ModList.get() != null && ModList.get().isLoaded("kubejs")
                && !Plugin.startupScriptsLoaded();
        bindItemComponentsForStartup();
        MMCRMachineStructuresEvent structures = MMCRMachineStructuresEvent.prepare(
                pendingProductionDefinitions.definitions().keySet());
        registerGameTestBuiltins("registerMachineStructures",
                new Class<?>[]{MMCRMachineStructuresEvent.class}, structures);
        eventBus.post(structures);
        structureCollectionDeferred = deferStructures;
        if (!deferStructures) {
            structures.freeze();
            ContentRegistrationCoordinator.collectStructures(structures);
        }
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        registerGameTestBuiltins("registerRecipes",
                new Class<?>[]{MMCRMachineRecipesEvent.class}, recipes);
        eventBus.post(recipes);
        recipes.freeze();
        ContentRegistrationCoordinator.collectRecipes(recipes);
        if (!deferStructures) {
            ContentRegistrationCoordinator.commitStartup();
            startupPhase = StartupPhase.COMMITTED;
        }
        pendingProductionDefinitions = null;
    }

    private static void registerProduction(boolean begin, boolean commit, IEventBus eventBus) {
        registerProduction(begin, commit, false, eventBus);
    }

    private static void registerProduction(boolean begin, boolean commit, boolean deferStructures, IEventBus eventBus) {
        registerStartupContent(
                definitions -> {
                    registerGameTestBuiltins("registerMachineDefinitions",
                            new Class<?>[]{MMCRMachineDefinationsEvent.class}, definitions);
                },
                structures -> {
                    registerGameTestBuiltins("registerMachineStructures",
                            new Class<?>[]{MMCRMachineStructuresEvent.class}, structures);
                },
                recipes -> {
                    registerGameTestBuiltins("registerRecipes",
                            new Class<?>[]{MMCRMachineRecipesEvent.class}, recipes);
                }, begin, commit, deferStructures, eventBus);
    }

    public static void registerForTesting() {
        registerForTesting(event -> { }, event -> { }, event -> { });
    }

    public static void registerForTesting(Consumer<MMCRMachineDefinationsEvent> definitionsSource,
                                          Consumer<MMCRMachineStructuresEvent> structuresSource,
                                          Consumer<MMCRMachineRecipesEvent> recipesSource) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, NeoForge.EVENT_BUS);
    }

    public static void completeKubeJSStartup() {
        if (ContentRegistrationCoordinator.isCommitted()) return;
        if (pendingProductionDefinitions != null) return;
        if (structureCollectionDeferred) {
            ContentRegistrationCoordinator.collectStructures(MMCRMachineStructuresEvent.current());
            structureCollectionDeferred = false;
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

    public static void registerKubeJSStartupMachine(cn.howxu.mmcr.api.publicapi.machine.MachineDefinition definition) {
        ContentRegistrationCoordinator.collectMachine(definition);
        registerDynamicControllers(Set.of(definition.id()));
    }

    public static String startupPhaseForTesting() {
        return startupPhase.name();
    }

    public static void resetForTesting() {
        startupPhase = StartupPhase.NOT_STARTED;
        structureCollectionDeferred = false;
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
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, NeoForge.EVENT_BUS);
    }

    private static void registerStartupContent(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource,
            IEventBus eventBus) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource, true, true, false, eventBus);
    }

    private static void registerStartupContent(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource,
            boolean begin,
            boolean commit,
            boolean deferStructures,
            IEventBus eventBus) {
        startupPhase = StartupPhase.COLLECTING;
        PublicApiBootstrap.begin();
        if (begin) ContentRegistrationCoordinator.beginStartup();
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        definitionsSource.accept(definitions);
        eventBus.post(definitions);
        registerDynamicControllers(definitions.definitions().keySet());
        definitions.freeze();
        ContentRegistrationCoordinator.collectMachines(definitions);

        bindItemComponentsForStartup();
        MMCRMachineStructuresEvent structures = MMCRMachineStructuresEvent.prepare(definitions.definitions().keySet());
        structuresSource.accept(structures);
        eventBus.post(structures);
        structureCollectionDeferred = deferStructures;
        if (!deferStructures) {
            structures.freeze();
            ContentRegistrationCoordinator.collectStructures(structures);
        }
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        recipesSource.accept(recipes);
        eventBus.post(recipes);
        recipes.freeze();
        ContentRegistrationCoordinator.collectRecipes(recipes);
        if (commit) {
            ContentRegistrationCoordinator.commitStartup();
            startupPhase = StartupPhase.COMMITTED;
        }
    }

    private static void registerDynamicControllers(Set<net.minecraft.resources.Identifier> machineIds) {
        ModBlocks.registerMachineControllers(machineIds);
        ModBlockEntities.registerMachineControllers(machineIds);
        ModItems.registerMachineControllerItems(machineIds);
    }

    /**
     * Ensures item holders can be used by startup declarations before the game has bound components.
     */
    public static void bindItemComponentsForStartup() {
        try {
            Fluids.WATER.builtInRegistryHolder().components();
        } catch (NullPointerException ignored) {
            Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
        for (Item item : BuiltInRegistries.ITEM) {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            try {
                holder.components();
            } catch (NullPointerException ignored) {
                holder.bindComponents(DataComponentMap.EMPTY);
            }
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

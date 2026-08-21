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

    private StartupContentRegistration() {
    }

    public static void registerProduction() {
        registerProduction(true, true);
    }

    public static void registerProductionForModStartup() {
        registerProduction(false, ModList.get() == null || !ModList.get().isLoaded("kubejs")
                || Plugin.startupScriptsLoaded());
    }

    private static void registerProduction(boolean begin, boolean commit) {
        registerStartupContent(
                definitions -> {
                    org.nibelungorum.builtin.PublicBuiltinMachineDefinitions.registerDefinitions(definitions);
                    registerGameTestBuiltins("registerMachineDefinitions",
                            new Class<?>[]{MMCRMachineDefinationsEvent.class}, definitions);
                },
                structures -> {
                    org.nibelungorum.builtin.PublicBuiltinMachineDefinitions.registerStructures(structures);
                    registerGameTestBuiltins("registerMachineStructures",
                            new Class<?>[]{MMCRMachineStructuresEvent.class}, structures);
                },
                recipes -> {
                    org.nibelungorum.builtin.PublicBuiltinRecipeDefinitions.register(recipes);
                    registerGameTestBuiltins("registerRecipes",
                            new Class<?>[]{MMCRMachineRecipesEvent.class}, recipes);
                }, begin, commit);
    }

    public static void registerForTesting() {
        registerForTesting(event -> { }, event -> { }, event -> { });
    }

    public static void registerForTesting(Consumer<MMCRMachineDefinationsEvent> definitionsSource,
                                          Consumer<MMCRMachineStructuresEvent> structuresSource,
                                          Consumer<MMCRMachineRecipesEvent> recipesSource) {
        registerStartupContent(definitionsSource, structuresSource, recipesSource);
    }

    public static void completeKubeJSStartup() {
        if (ContentRegistrationCoordinator.isCommitted()) return;
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

    public static String startupPhaseForTesting() {
        return startupPhase.name();
    }

    public static void resetForTesting() {
        startupPhase = StartupPhase.NOT_STARTED;
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
        registerStartupContent(definitionsSource, structuresSource, recipesSource, true, true);
    }

    private static void registerStartupContent(
            Consumer<MMCRMachineDefinationsEvent> definitionsSource,
            Consumer<MMCRMachineStructuresEvent> structuresSource,
            Consumer<MMCRMachineRecipesEvent> recipesSource,
            boolean begin,
            boolean commit) {
        startupPhase = StartupPhase.COLLECTING;
        PublicApiBootstrap.begin();
        if (begin) ContentRegistrationCoordinator.beginStartup();
        MMCRMachineDefinationsEvent definitions = new MMCRMachineDefinationsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        definitionsSource.accept(definitions);
        registerDynamicControllers(definitions.definitions().keySet());
        NeoForge.EVENT_BUS.post(definitions);
        definitions.freeze();
        ContentRegistrationCoordinator.collectMachines(definitions);

        MMCRMachineStructuresEvent structures = MMCRMachineStructuresEvent.prepare(definitions.definitions().keySet());
        structuresSource.accept(structures);
        NeoForge.EVENT_BUS.post(structures);
        structures.freeze();
        ContentRegistrationCoordinator.collectStructures(structures);
        bindVanillaItemComponents();
        MMCRMachineRecipesEvent recipes = new MMCRMachineRecipesEvent();
        recipesSource.accept(recipes);
        NeoForge.EVENT_BUS.post(recipes);
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

    private static void bindVanillaItemComponents() {
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

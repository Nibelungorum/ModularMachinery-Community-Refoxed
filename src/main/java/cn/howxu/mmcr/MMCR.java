package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ExportCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
import cn.howxu.mmcr.internal.reload.MachineRecipeDataReloadListener;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.event.StructureDirtyEvents;
import cn.howxu.mmcr.internal.event.SharedIoEvents;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineAppearancePayload;
import cn.howxu.mmcr.internal.network.PktControllerSpecsPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktMultiblockMismatchHighlightPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockPreviewPayload;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.network.PktEjectPortContentsPayload;
import cn.howxu.mmcr.internal.network.PktRecipeLockPayload;
import cn.howxu.mmcr.internal.network.PktRuntimeContentPayload;
import cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload;
import cn.howxu.mmcr.internal.network.RuntimeContentServerBridge;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.ModRecipeTypes;

import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineDefinitionProviders;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterRecipesEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        PublicApiBootstrap.begin();
        MachineDefinitions.beginRegistryPhase();
        registerDevelopmentBuiltins("cn.howxu.mmcr.GameTestRegistry", "registerMachineSuppliers");
        MachineDefinitions.bootstrapBuiltins();
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModUIs.register(modBus);
        ModRecipeTypes.register(modBus);
        modBus.addListener(MMCR::onCommonSetup);
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlocksPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onFluidPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkUnloaded);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkLoaded);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(RuntimeContentServerBridge::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(RuntimeContentServerBridge::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MMCR::onDefaultDataComponentsBound);
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> MachineRecipeDataReloadListener.register(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RuntimeContentSync.sendTo(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RuntimeContentSync.sendTo(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent ev) -> {
            ReloadCommand.register(ev.getDispatcher());
            BuildCommand.register(ev.getDispatcher());
            ExportCommand.register(ev.getDispatcher());
        });
        modBus.addListener((RegisterPayloadHandlersEvent ev) -> {
            ev.registrar("1").playToClient(
                    PktMachineStatePayload.TYPE,
                    PktMachineStatePayload.STREAM_CODEC,
                    PktMachineStatePayload::handle)
                    .playToClient(
                            PktFactoryControllerStatePayload.TYPE,
                            PktFactoryControllerStatePayload.STREAM_CODEC,
                            PktFactoryControllerStatePayload::handle)
                    .playToClient(
                            PktControllerSpecsPayload.TYPE,
                            PktControllerSpecsPayload.STREAM_CODEC,
                            PktControllerSpecsPayload::handle)
                    .playToClient(
                            PktMachineAppearancePayload.TYPE,
                            PktMachineAppearancePayload.STREAM_CODEC,
                            PktMachineAppearancePayload::handle)
                    .playToClient(
                            PktRuntimeContentPayload.TYPE,
                            PktRuntimeContentPayload.STREAM_CODEC,
                            PktRuntimeContentPayload::handle)
                    .playToClient(
                            PktMultiblockMismatchHighlightPayload.TYPE,
                            PktMultiblockMismatchHighlightPayload.STREAM_CODEC,
                            PktMultiblockMismatchHighlightPayload::handle)
                    .playToClient(
                            PktMultiblockPreviewPayload.TYPE,
                            PktMultiblockPreviewPayload.STREAM_CODEC,
                            PktMultiblockPreviewPayload::handle)
                    .playToServer(
                            PktMultiblockDetectorPickPayload.TYPE,
                            PktMultiblockDetectorPickPayload.STREAM_CODEC,
                            PktMultiblockDetectorPickPayload::handle)
                    .playToServer(
                            PktSmartInterfaceUpdatePayload.TYPE,
                            PktSmartInterfaceUpdatePayload.STREAM_CODEC,
                            PktSmartInterfaceUpdatePayload::handle)
                     .playToServer(
                             PktAutoIOConfigPayload.TYPE,
                             PktAutoIOConfigPayload.STREAM_CODEC,
                             PktAutoIOConfigPayload::handle)
                     .playToServer(
                             PktEjectPortContentsPayload.TYPE,
                             PktEjectPortContentsPayload.STREAM_CODEC,
                             PktEjectPortContentsPayload::handle)
                     .playToServer(
                             PktRecipeLockPayload.TYPE,
                             PktRecipeLockPayload.STREAM_CODEC,
                             PktRecipeLockPayload::handle);
        });
        modBus.addListener((RegisterGameTestsEvent ev) -> registerGameTests(ev));
        CREATIVE_TABS.register(MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> ModItems.ITEMS.get("basic_casing").get().getDefaultInstance())
                .displayItems((params, output) ->
                        ModItems.ITEMS.values().forEach(h -> output.accept(h.get())))
                .build());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void registerRuntimeBuiltins() {
        registerPublicApiLifecycle();
        PublicApiBootstrap.freezeAndInstallMachines();
        DynamicContentReloadService.reload(candidate -> {
            cn.howxu.mmcr.internal.api.PublicBuiltinRuntime.registerStructures(candidate);
        });
        MachineRegistry.rebuildCompiledCache();
    }

    private static void registerRuntimeRecipes() {
        MMCRRegisterRecipesEvent recipes = new MMCRRegisterRecipesEvent();
        NeoForge.EVENT_BUS.post(recipes);
        registerDevelopmentBuiltins("cn.howxu.mmcr.GameTestRegistry", "registerRecipes",
                new Class<?>[]{MMCRRegisterRecipesEvent.class}, recipes);
        recipes.freeze();
        PublicApiBootstrap.registerRecipes(recipes);
        PublicApiBootstrap.installRecipes();
    }

    private static void registerPublicApiLifecycle() {
        PublicApiBootstrap.begin();
        RegisterMachineDefinationsEvent definitions = new RegisterMachineDefinationsEvent();
        PublicMachineDefinitionProviders.registerAll(definitions);
        registerDevelopmentBuiltins("cn.howxu.mmcr.GameTestRegistry", "registerMachineDefinitions",
                new Class<?>[]{RegisterMachineDefinationsEvent.class}, definitions);
        NeoForge.EVENT_BUS.post(definitions);
        definitions.freeze();
        PublicApiBootstrap.registerDefinitions(definitions);

        RegisterMachineStructuresEvent structures = RegisterMachineStructuresEvent.prepare(definitions.definitions().keySet());
        registerDefaultMachineLevels(structures);
        registerDevelopmentBuiltins("cn.howxu.mmcr.GameTestRegistry", "registerMachineStructures",
                new Class<?>[]{RegisterMachineStructuresEvent.class}, structures);
        NeoForge.EVENT_BUS.post(structures);
        structures.freeze();
        PublicApiBootstrap.composeMachineRegistrations(definitions, structures);
        MachineLevelRegistry.install(structures.levelTypes().values(), structures.levels().values());
        MachineDefinitions.validateRegistryPhase();
        MachineDefinitions.freezeRegistryPhase();
    }

    public static void registerPublicApiLifecycleForTesting() {
        registerPublicApiLifecycle();
        MMCRRegisterRecipesEvent recipes = new MMCRRegisterRecipesEvent();
        NeoForge.EVENT_BUS.post(recipes);
        registerDevelopmentBuiltins("cn.howxu.mmcr.GameTestRegistry", "registerRecipes",
                new Class<?>[]{MMCRRegisterRecipesEvent.class}, recipes);
        recipes.freeze();
    }

    private static void registerDefaultMachineLevels(RegisterMachineStructuresEvent event) {
        if (FMLLoader.getCurrent().isProduction()
                || event.levelTypes().containsKey(id("thermal_smelting_coil"))) return;
        registerDevelopmentBuiltins("org.nibelungorum.DefaultMachineLevels", "register",
                new Class<?>[]{RegisterMachineStructuresEvent.class}, event);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerPublicApiLifecycle();
            PublicApiBootstrap.freezeAndInstallMachines();
        });
    }

    private static void onDefaultDataComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.shouldUpdateStaticData()) {
            registerRuntimeRecipes();
        }
    }

    private static void registerDevelopmentBuiltins(String className, String methodName) {
        registerDevelopmentBuiltins(className, methodName, new Class<?>[0]);
    }

    private static void registerDevelopmentBuiltins(String className, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (FMLLoader.getCurrent().isProduction()) return;
        try {
            Class.forName(className).getMethod(methodName, parameterTypes).invoke(null, arguments);
        } catch (ClassNotFoundException ignored) {
            // GameTest classes are only present on the GameTest classpath.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to register development builtins from " + className, e);
        }
    }

    private static void registerGameTests(RegisterGameTestsEvent event) {
        try {
            Class.forName("cn.howxu.mmcr.GameTestRegistry")
                    .getMethod("registerAll", RegisterGameTestsEvent.class)
                    .invoke(null, event);
        } catch (ReflectiveOperationException ignored) {
            // GameTest classes are only present in the gametest source set.
        }
    }
}

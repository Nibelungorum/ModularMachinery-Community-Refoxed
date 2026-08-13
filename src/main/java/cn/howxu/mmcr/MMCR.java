package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.sound.MachineSoundRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ExportCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.reload.DynamicContentReloadService;
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
import cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import org.nibelungorum.BuiltinMachines;
import org.nibelungorum.DefaultMachines;
import org.nibelungorum.DefaultMachineLevels;
import org.nibelungorum.DefaultRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        MachineDefinitions.beginRegistryPhase();
        BuiltinMachines.register();
        registerGameTestMachineDefinitionsIfPresent();
        MachineDefinitions.bootstrapBuiltins();
        MachineDefinitions.freezeRegistryPhase();
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModUIs.register(modBus);
        ModRecipeTypes.register(modBus);
        modBus.addListener(MachineSoundRegistry::onRegister);
        modBus.addListener(MMCR::onCommonSetup);
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlocksPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onFluidPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkUnloaded);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(SharedIoEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                cn.howxu.mmcr.internal.network.ControllerSpecSync.sendTo(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                cn.howxu.mmcr.internal.network.ControllerSpecSync.sendTo(player);
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
                            PktAutoIOConfigPayload::handle);
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
        registerDefaultMachineLevels();
        DynamicContentReloadService.reload(candidate -> {
            DefaultMachines.structures().values().forEach(candidate::registerStructure);
            registerGameTestMachineStructuresIfPresent(candidate);
        });
        DefaultRecipes.registerStatic(DefaultRecipes.recipes().values().stream().toList());
        MachineRegistry.rebuildCompiledCache();
    }

    private static void registerDefaultMachineLevels() {
        if (MachineLevelRegistry.getType(DefaultMachineLevels.THERMAL_SMELTING_COIL_TYPE) != null) return;

        MachineLevelRegistry.beginRegistration();
        DefaultMachineLevels.register();
        MachineLevelRegistry.freezeRegistration();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MMCR::registerRuntimeBuiltins);
    }

    static void registerGameTestMachineStructuresIfPresent(DynamicContentReloadService.Candidate candidate) {
        try {
            Class.forName("cn.howxu.mmcr.GameTestRegistry");
        } catch (ClassNotFoundException ignored) {
            return;
        }
        registerGameTestMachineStructures(candidate);
    }

    static void registerGameTestMachineDefinitionsIfPresent() {
        try {
            Class.forName("cn.howxu.mmcr.GameTestRegistry");
        } catch (ClassNotFoundException ignored) {
            return;
        }
        registerGameTestMachineDefinitions();
    }

    public static void registerGameTestMachineDefinitions() {
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("test_cube")).displayNameKey("machine.mmcr.test_cube").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("controller_tick")).displayNameKey("machine.mmcr.controller_tick").build());
        MachineDefinitions.addBuiltinSupplier(() -> MachineRegistration.builder(id("iron_compressor")).displayNameKey("machine.mmcr.iron_compressor").build());
    }

    public static void registerGameTestMachineStructures(DynamicContentReloadService.Candidate candidate) {
        candidate.registerStructure(new MachineStructureDefinition(
                id("test_cube"), org.nibelungorum.TestMachines.casingCubePattern(),
                cn.howxu.mmcr.api.machine.PortRequirementSpec.none(), java.util.List.of(), java.util.Map.of()));
        candidate.registerStructure(new MachineStructureDefinition(
                id("controller_tick"), org.nibelungorum.TestMachines.casingCubePattern(),
                cn.howxu.mmcr.api.machine.PortRequirementSpec.none(), java.util.List.of(), java.util.Map.of()));
        candidate.registerStructure(new MachineStructureDefinition(
                id("iron_compressor"), org.nibelungorum.TestMachines.ironCompressorPattern(),
                cn.howxu.mmcr.api.machine.PortRequirementSpec.none(), java.util.List.of(), java.util.Map.of()));
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

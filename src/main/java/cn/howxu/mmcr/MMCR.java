package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ExportCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.event.StructureDirtyEvents;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.network.PktControllerSpecsPayload;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import org.nibelungorum.BuiltinMachines;
import org.nibelungorum.DefaultRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
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
        BuiltinMachines.register();
        registerGameTestMachineDefinitionsIfPresent();
        MachineDefinitions.bootstrapBuiltins();
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModUIs.register(modBus);
        ModRecipeTypes.register(modBus);
        registerRuntimeBuiltins();
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlocksPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onFluidPlaced);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(StructureDirtyEvents::onChunkUnloaded);
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
                            PktControllerSpecsPayload.TYPE,
                            PktControllerSpecsPayload.STREAM_CODEC,
                            PktControllerSpecsPayload::handle)
                    .playToServer(
                            PktMultiblockDetectorPickPayload.TYPE,
                            PktMultiblockDetectorPickPayload.STREAM_CODEC,
                            PktMultiblockDetectorPickPayload::handle);
        });
        modBus.addListener((RegisterGameTestsEvent ev) -> registerGameTests(ev));
        CREATIVE_TABS.register(MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> ModItems.ITEMS.get("basic_casing").get().getDefaultInstance())
                .displayItems((params, output) ->
                        ModItems.ITEMS.values().forEach(h -> output.accept(h.get())))
                .build());
        LOG.info("MMCR {} loaded", modContainer.getModInfo().getVersion());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    public static void registerRuntimeBuiltins() {
        DefaultRecipes.ensureRegistered();
        MachineRegistry.rebuildCompiledCache();
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
        MachineDefinitions.addBuiltinSupplier(() -> new cn.howxu.mmcr.api.machine.DynamicMachine(
                id("test_cube"), "Test", new cn.howxu.mmcr.api.machine.BlockArray(java.util.Map.of())));
        MachineDefinitions.addBuiltinSupplier(() -> new cn.howxu.mmcr.api.machine.DynamicMachine(
                id("controller_tick"), "Controller Tick", new cn.howxu.mmcr.api.machine.BlockArray(java.util.Map.of())));
        MachineDefinitions.addBuiltinSupplier(() -> new cn.howxu.mmcr.api.machine.DynamicMachine(
                id("iron_compressor"), "Iron Compressor", new cn.howxu.mmcr.api.machine.BlockArray(java.util.Map.of())));
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

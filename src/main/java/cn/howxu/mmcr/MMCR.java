package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.config.Config;
import cn.howxu.mmcr.internal.command.BuildCommand;
import cn.howxu.mmcr.internal.command.ReloadCommand;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModRecipeTypes;
import org.nibelungorum.BuiltinMachines;
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
        MachineDefinitions.bootstrapBuiltins();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModRecipeTypes.register(modBus);
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent ev) -> {
            ReloadCommand.register(ev.getDispatcher());
            BuildCommand.register(ev.getDispatcher());
        });
        modBus.addListener((RegisterPayloadHandlersEvent ev) -> {
            ev.registrar("1").playToClient(
                    PktMachineStatePayload.TYPE,
                    PktMachineStatePayload.STREAM_CODEC,
                    (payload, ctx) -> MMCR.LOG.debug("Received machine state: {}", payload));
        });
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
}

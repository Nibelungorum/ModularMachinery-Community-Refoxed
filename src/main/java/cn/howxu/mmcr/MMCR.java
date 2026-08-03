package cn.howxu.mmcr;

import cn.howxu.mmcr.config.MMCRConfig;
import cn.howxu.mmcr.internal.command.MMCRReloadCommand;
import cn.howxu.mmcr.internal.event.MMCRCapabilities;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.MMCRBlockEntities;
import cn.howxu.mmcr.registry.MMCRBlocks;
import cn.howxu.mmcr.registry.MMCRItems;
import cn.howxu.mmcr.registry.MMCRRecipeTypes;
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
        MMCRBlocks.register(modBus);
        MMCRItems.register(modBus);
        MMCRBlockEntities.register(modBus);
        MMCRRecipeTypes.register(modBus);
        CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, MMCRConfig.SPEC);
        modBus.addListener(MMCRCapabilities::register);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent ev) ->
                MMCRReloadCommand.register(ev.getDispatcher()));
        modBus.addListener((RegisterPayloadHandlersEvent ev) -> {
            ev.registrar("1").playToClient(
                    PktMachineStatePayload.TYPE,
                    PktMachineStatePayload.STREAM_CODEC,
                    (payload, ctx) -> MMCR.LOG.debug("Received machine state: {}", payload));
        });
        CREATIVE_TABS.register(MODID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmcr"))
                .icon(() -> MMCRItems.ITEMS.get("casing").get().getDefaultInstance())
                .displayItems((params, output) ->
                        MMCRItems.ITEMS.values().forEach(h -> output.accept(h.get())))
                .build());
        LOG.info("MMCR {} loaded", modContainer.getModInfo().getVersion());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

package cn.howxu.mmcr;

import cn.howxu.mmcr.config.MMCRConfig;
import cn.howxu.mmcr.registry.MMCRRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MMCR.MODID)
public class MMCR {
    public static final String MODID = "mmcr";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public MMCR(IEventBus modBus, ModContainer modContainer) {
        MMCRRegistries.BLOCKS.register(modBus);
        MMCRRegistries.ITEMS.register(modBus);
        MMCRRegistries.BLOCK_ENTITIES.register(modBus);
        MMCRRegistries.RECIPE_TYPES.register(modBus);
        MMCRRegistries.RECIPE_SERIALIZERS.register(modBus);
        MMCRRegistries.CREATIVE_TABS.register(modBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, MMCRConfig.SPEC);
        LOG.info("MMCR {} loaded", modContainer.getModInfo().getVersion());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}

package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.create.CreateBridge;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = MMCR.MODID)
public final class DataGen {
    private DataGen() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        if (event.includeServer()) {
            var blockTags = event.createProvider(ModBlockTags::new);
            event.addProvider(new ModItemTags(output, event.getLookupProvider(), blockTags.contentsGetter()));
            event.addProvider(new LootTableGen(output, event.getLookupProvider()));
            event.addProvider(new ModRecipeProvider(output, event.getLookupProvider()));
        }
        if (event.includeClient()) event.addProvider(new ModelGen(output, modelFiles(event)));
    }

    private static ExistingFileHelper modelFiles(GatherDataEvent event) {
        ExistingFileHelper existing = event.getExistingFileHelper();
        if (!CreateBridge.get().available()) return existing;
        // GatherData's resource manager includes loaded mods even without --existing-mod.
        // Keep the shared helper's existing/generated resources and validation enabled.
        return new ExistingFileHelper(List.of(), Set.of(), existing.isEnabled(), null, null) {
            @Override
            public boolean exists(ResourceLocation location, PackType packType) {
                return super.exists(location, packType) || existing.exists(location, packType)
                        || location.getNamespace().equals("create")
                        && event.getResourceManager(packType).getResource(location).isPresent();
            }

            @Override
            public Resource getResource(ResourceLocation location, PackType packType) throws FileNotFoundException {
                if (location.getNamespace().equals("create")) {
                    return event.getResourceManager(packType).getResourceOrThrow(location);
                }
                return existing.getResource(location, packType);
            }
        };
    }
}

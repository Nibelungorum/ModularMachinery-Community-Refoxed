package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

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
        if (event.includeClient()) event.addProvider(new ModelGen(output, event.getExistingFileHelper()));
    }
}

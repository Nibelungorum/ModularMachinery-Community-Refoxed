package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = MMCR.MODID)
public final class DataGen {
    private DataGen() {
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.addProvider(new ModBlockTags(output, event.getLookupProvider()));
        event.addProvider(new ModItemTags(output, event.getLookupProvider()));
        event.addProvider(new LootTableGen(output, event.getLookupProvider()));
        event.addProvider(new ModelGen(output));
        event.addProvider(new RecipeProvider.Runner(output, event.getLookupProvider()) {
            @Override
            protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput recipeOutput) {
                return new ModRecipeProvider(registries, recipeOutput);
            }

            @Override
            public String getName() {
                return "MMCR Recipes";
            }
        });
    }
}

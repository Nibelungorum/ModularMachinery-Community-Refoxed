package org.nibelungorum.client;

import cn.howxu.mmcr.api.publicapi.event.MMCRJeiRecipeInformationEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/9/26 11:42
 */
@EventBusSubscriber
public class JEI_EXTRA_INFORMATION {
    @SubscribeEvent
    public static void registerRecipeInformation(MMCRJeiRecipeInformationEvent event) {
        event.registerRecipePool(
                ResourceLocation.parse("mmcr:blast_furnace"),
                "jei.mmcr_test.blast_furnace.info_1"
        );

        event.registerRecipePool(
                ResourceLocation.parse("mmcr:blast_furnace"),
                "jei.mmcr_test.blast_furnace.info_2",
                1200
        );

        event.registerRecipe(
                ResourceLocation.parse("mmcr:blast_furnace_recipe_1"),
                "jei.mmcr_test.blast_furnace.info_3"
        );

        event.registerRecipe(
                ResourceLocation.parse("mmcr:blast_furnace_recipe_1"),
                "jei.mmcr_test.blast_furnace.info_4",
                "Character"
        );
    }
}

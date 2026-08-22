package org.nibelungorum.builtin.recipe;

import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/22 21:42
 */
public class BLAST_FURNACE {

    private static final Identifier BLAST_FURNACE = id("blast_furnace"); // equal to mmcr:blast_furnace

    public static MachineRecipeDefinition get(){
        return MachineRecipeBuilder
                .recipe(BLAST_FURNACE.withSuffix("_recipe_1"),BLAST_FURNACE)
                .inputItem(Ingredient.of(Items.IRON_INGOT),9)
                .outputItem(Items.IRON_NUGGET,10)
                .inputEnergy(20)
                .build();
    }
}

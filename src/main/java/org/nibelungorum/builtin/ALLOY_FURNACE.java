package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierScope;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:23
 */
@EventBusSubscriber
public class ALLOY_FURNACE {

    private static final ResourceLocation ALLOY_FURNACE = id("alloy_furnace");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(ALLOY_FURNACE)) {
            MachineSpec machine = Machines
                    .machine(ALLOY_FURNACE)
                    .recipePool(ALLOY_FURNACE)
                    .allowModifiers()
                    .displayNameKey("machine.mmcr.alloy_furnace")
                    .appearance(appearance -> appearance.machineBasicBlock(ResourceLocation.parse("minecraft:bricks")))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {

        // register your modifier first
        event.registerModifier(
                id("alloy_furnace_diamond_speedup"),
                Modifiers.bundle(Modifiers.numeric(
                        "duration",
                        ModifierScope.INPUT,
                        0.5F,
                        ModifierOperation.MULTIPLY,
                        false
                )));
        event.registerModifierItem(new ItemStack(Items.DIAMOND_BLOCK), id("alloy_furnace_diamond_speedup"));

        event.registerModifier(
                id("alloy_furnace_gold_doubling"),
                Modifiers.bundle(Modifiers.numeric(
                        "output",
                        ModifierScope.OUTPUT,
                        2.0F,
                        ModifierOperation.MULTIPLY,
                        false
                )));
        event.registerModifierItem(new ItemStack(Items.GOLD_BLOCK), id("alloy_furnace_gold_doubling"));


        if (!event.structures().containsKey(ALLOY_FURNACE)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                .layer("XXX", "XIX", "XXX")
                                .layer("XMX", "I I", "XMX")
                                .layer("XXX", "XCX", "XXX")
                                .where('X', block(Blocks.BRICKS))
                                    .where('I', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.energyInput()
                                    ))
                                    .where('M', block(Blocks.BLAST_FURNACE))
                                    .controller('C'))
                            .requirements(r -> r
                                    .modifier('M', id("alloy_furnace_diamond_speedup"), block(Blocks.DIAMOND_BLOCK))
                                    .modifier('M', id("alloy_furnace_gold_doubling"), block(Blocks.GOLD_BLOCK))
                            ))
                    .build(ALLOY_FURNACE);
            event.registerStructure(structure);
        }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(ALLOY_FURNACE.withSuffix("_recipe_1"))
                .recipePool(ALLOY_FURNACE)
                .inputItem(Ingredient.of(Items.GOLD_INGOT),1)
                .outputItem(Items.GOLD_NUGGET,10)
                .inputEnergy(20)
                .duration(200)
                .build();
        event.registerRecipe(recipe);

    }
}

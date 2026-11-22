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
import net.minecraft.resources.Identifier;
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

    private static final Identifier ALLOY_FURNACE = id("alloy_furnace");

    private static final Identifier DIAMOND_SPEEDUP = id("alloy_furnace_diamond_speedup");
    private static final Identifier GOLD_DOUBLING = id("alloy_furnace_gold_doubling");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(ALLOY_FURNACE)) {
            event.registerModifier(
                    DIAMOND_SPEEDUP,
                    Modifiers.bundle(Modifiers.numeric(
                            "duration",
                            ModifierScope.INPUT,
                            0.5F,
                            ModifierOperation.MULTIPLY,
                            false
                    )));
            event.registerModifierItem(Identifier.parse("minecraft:diamond_block"), DIAMOND_SPEEDUP);

            event.registerModifier(
                    GOLD_DOUBLING,
                    Modifiers.bundle(Modifiers.numeric(
                            "output",
                            ModifierScope.OUTPUT,
                            2.0F,
                            ModifierOperation.MULTIPLY,
                            false
                    )));
            event.registerModifierItem(Identifier.parse("minecraft:gold_block"), GOLD_DOUBLING);

            MachineSpec machine = Machines
                    .machine(ALLOY_FURNACE)
                    .recipePool(ALLOY_FURNACE)
                    .allowModifier(DIAMOND_SPEEDUP)
                    .allowModifier(GOLD_DOUBLING)
                    .displayNameKey("machine.mmcr.alloy_furnace")
                    .appearance(appearance -> appearance.machineBasicBlock(Identifier.parse("minecraft:bricks")))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {

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
                                    .modifier('M', DIAMOND_SPEEDUP, block(Blocks.DIAMOND_BLOCK))
                                    .modifier('M', GOLD_DOUBLING, block(Blocks.GOLD_BLOCK))
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

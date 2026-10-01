package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:45
 */
@EventBusSubscriber
public class CRACKER {
    private static final ResourceLocation CRACKER = id("cracker");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(CRACKER)) {
            MachineSpec machine = Machines
                    .machine(CRACKER)
                    .recipePool(CRACKER)
                    .displayNameKey("machine.mmcr.cracker")
                    .controller(builder -> builder
                            .id(CRACKER.withSuffix("_controller"))
                            .allowVerticalFacing(true)
                            .fullyRotationallySymmetric(true)
                    )
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(CRACKER)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("AAA", "AAA", "AAA")
                                    .layer("XBX", "B B", "XBX")
                                    .layer("XDX", "D D", "XDX")
                                    .layer("XEX", "ECE", "XEX")
                                    .where('X', block(Blocks.POLISHED_DIORITE))
                                    .where('A', block(Blocks.POLISHED_ANDESITE))
                                    .where('B', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.fluidOutput(),
                                            BlockConditions.energyInput(),
                                            block(Blocks.BONE_BLOCK)
                                    ))
                                    .where('D', block(Blocks.BLUE_ICE))
                                    .where('E', block(Blocks.LAPIS_BLOCK))
                                    .controller('C')
                            )
                            .portTiers(t -> t
                                    .minEnergyInput(PortTierLimits.EnergyTier.NORMAL)
                                    .minItemInput(PortTierLimits.ItemTier.NORMAL)
                                    .anyItemOutput()
                            )
                    )
                    .build(CRACKER);
            event.registerStructure(structure);
        }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(CRACKER.withSuffix("_recipe_1"))
                .recipePool(CRACKER)
                .inputItem(Ingredient.of(Items.LAPIS_LAZULI),8)
                .inputItem(Items.COAL,1)
                .outputFluid(Fluids.WATER,500)
                .outputItem(Items.DIAMOND,2)
                .inputEnergy(20)
                .duration(240)
                .build();
        event.registerRecipe(recipe);

    }
}

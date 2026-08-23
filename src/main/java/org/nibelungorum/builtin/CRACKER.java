package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:45
 */
@EventBusSubscriber
public class CRACKER {
    private static final Identifier CRACKER = id("cracker");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(CRACKER)) {
            var machine = MachineBuilder
                    .machine(CRACKER)
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
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(CRACKER)) {
            var structure = MachineStructureBuilder
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
                                            InterfacePredicates.anyItemInput(),
                                            InterfacePredicates.anyItemOutput(),
                                            InterfacePredicates.anyFluidOutput(),
                                            InterfacePredicates.anyEnergyInput(),
                                            block(Blocks.BONE_BLOCK)
                                    ))
                                    .where('D', block(Blocks.BLUE_ICE))
                                    .where('E', block(Blocks.LAPIS_BLOCK))
                                    .controller('C')
                            )
                            .portTiers(t -> t
                                    .minEnergyInput(PortTiers.EnergyTier.NORMAL)
                                    .minItemInput(PortTiers.ItemTier.NORMAL)
                                    .anyItemOutput()
                            )
                    )
                    .build(CRACKER);
            event.registerStructure(structure);
        }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(CRACKER.withSuffix("_recipe_1"),CRACKER)
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

package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;
import static cn.howxu.mmcr.api.publicapi.machine.ModifierUse.of;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:23
 */
@EventBusSubscriber
public class ALLOY_FURNACE {

    private static final Identifier ALLOY_FURNACE = id("alloy_furnace");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(ALLOY_FURNACE)) {
            var machine = MachineBuilder
                    .machine(ALLOY_FURNACE)
                    .allowModifiers()
                    .displayNameKey("machine.mmcr.alloy_furnace")
                    .appearance(appearance -> appearance.machineBasicBlock(Identifier.parse("minecraft:bricks")))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {

        // register your modifier first
        event.registerModifier(
                id("alloy_furnace_diamond_speedup"),
                ModifierDefinition.of(
                        "duration",
                        "input",
                        0.5F,
                        "multiply",
                        false
                ));

        event.registerModifier(
                id("alloy_furnace_gold_doubling"),
                ModifierDefinition.of(
                        "item",
                        "output",
                        2.0F,
                        "multiply",
                        false
                ));


        if (!event.structures().containsKey(ALLOY_FURNACE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                .layer("XXX", "XIX", "XXX")
                                .layer("XMX", "I I", "XMX")
                                .layer("XXX", "XCX", "XXX")
                                .where('X', block(Blocks.BRICKS))
                                    .where('I', any(
                                            InterfacePredicates.anyItemInput(),
                                            InterfacePredicates.anyItemOutput(),
                                            InterfacePredicates.anyEnergyInput()
                                    ))
                                    .where('M', block(Blocks.BLAST_FURNACE))
                                    .controller('C'))
                            .requirements(r -> r
                                    .modifier('M', of(id("alloy_furnace_diamond_speedup"), block(Blocks.DIAMOND_BLOCK)))
                                    .modifier('M', of(id("alloy_furnace_gold_doubling"), block(Blocks.GOLD_BLOCK)))
                            ))
                    .build(ALLOY_FURNACE);
            event.registerStructure(structure);
        }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(ALLOY_FURNACE.withSuffix("_recipe_1"),ALLOY_FURNACE)
                .inputItem(Ingredient.of(Items.GOLD_INGOT),1)
                .outputItem(Items.GOLD_NUGGET,10)
                .inputEnergy(20)
                .duration(200)
                .build();
        event.registerRecipe(recipe);

    }
}

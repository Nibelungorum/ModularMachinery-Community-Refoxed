package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.*;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 08:57
 */
@EventBusSubscriber(modid = PublicBuiltinRegistration.MOD_ID)
public class BLAST_FURNACE {

    private static final Identifier BLAST_FURNACE = id("blast_furnace"); // equal to mmcr:blast_furnace

    static {
        PublicBuiltinRegistration.logger().info("[MMCR/Temp] Loaded BLAST_FURNACE event subscriber");
    }

    @SubscribeEvent
    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        PublicBuiltinRegistration.logger().info("[MMCR/Temp] Registering built-in machine {}", BLAST_FURNACE);
        if (!event.definitions().containsKey(BLAST_FURNACE)) {
            var machine = MachineBuilder
                    .machine(BLAST_FURNACE)
                    .displayNameKey("machine.mmcr.blast_furnace")
                    .allowMultithreading()
                    .maxParallelAmount(Integer.MAX_VALUE)
                    .parallelizable(true)
                    .factory(factory -> factory.hasFactory(true).threadLimit(4)) // allow multi thread limit 4
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        PublicBuiltinRegistration.logger().info("[MMCR/Temp] Registering built-in structure {}", BLAST_FURNACE);
            if (!event.structures().containsKey(BLAST_FURNACE)) {
                var structure = MachineStructureBuilder
                        .structure()
                        .fullStructure(s -> s
                                .pattern(p -> p
                                        .layer("AXA", "XIX", "XXX")
                                        .layer("XXX", "I I", "XBX")
                                        .layer("AXA", "XCX", "XXX")
                                        .where('X', block(ModBlocks.CASING.get()))
                                        .where('A', any(
                                                block(Blocks.IRON_BLOCK),
                                                InterfacePredicates.parallelControllers()
                                        ))
                                        .where('B', block(Blocks.FURNACE))
                                        .where('I', any(
                                                InterfacePredicates.anyItemInput(),
                                                InterfacePredicates.anyItemOutput(),
                                                InterfacePredicates.anyEnergyInput()
                                        ))
                                        .controller('C'))
                                .portTiers(t -> t
                                        .minEnergyInput(PortTiers.EnergyTier.NORMAL)
                                        .minItemInput(PortTiers.ItemTier.NORMAL)
                                        .anyItemOutput())
                        )
                        .build(BLAST_FURNACE);
                event.registerStructure(structure);
            }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        PublicBuiltinRegistration.logger().info("[MMCR/Temp] Registering built-in recipe for {}", BLAST_FURNACE);
        var recipe = MachineRecipeBuilder
                .recipe(BLAST_FURNACE.withSuffix("_recipe_1"),BLAST_FURNACE)
                .inputItem(Ingredient.of(Items.IRON_INGOT),9)
                .outputItem(Items.IRON_NUGGET,10)
                .inputEnergy(20)
                .build();
        event.registerRecipe(recipe);

    }
}

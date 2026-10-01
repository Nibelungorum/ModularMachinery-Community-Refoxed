package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.event.RegisterJeiWorkstationsEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
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
 * @date: 2026/8/23 08:57
 */
@EventBusSubscriber
public class BLAST_FURNACE {

    private static final ResourceLocation BLAST_FURNACE = id("blast_furnace"); // equal to mmcr:blast_furnace

    // cause we have to register controller block
    // this must work before ModBlocs, it's not suggested to use mixin to change the neoforge lifecycle
    // so you must use a provider
    // see resources/META-INF/services how to use provider to register machine defination
    // see org/nibelungorum/provider/BuiltInProvider.java
    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(BLAST_FURNACE)) {
            MachineSpec machine = Machines
                    .machine(BLAST_FURNACE)
                    .recipePool(BLAST_FURNACE)
                    .displayNameKey("machine.mmcr.blast_furnace")
                    .allowMultithreading()
                    .maxParallelism(Integer.MAX_VALUE)
                    .parallelizable(true)
                    .factory(factory -> factory.hasFactory(true).threadLimit(4)) // allow multi thread limit 4
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
            if (!event.structures().containsKey(BLAST_FURNACE)) {
                StructureSpec structure = Structures
                        .structure()
                        .fullStructure(s -> s
                                .pattern(p -> p
                                        .layer("AXA", "XIX", "XXX")
                                        .layer("XXX", "I I", "XBX")
                                        .layer("AXA", "XCX", "XXX")
                                        .where('X', block("mmcr:basic_casing"))
                                        .where('A', any(
                                                block(Blocks.IRON_BLOCK),
                                                BlockConditions.parallelControllers()
                                        ))
                                        .where('B', block(Blocks.FURNACE))
                                        .where('I', any(
                                                BlockConditions.itemInput(),
                                                BlockConditions.itemOutput(),
                                                BlockConditions.energyInput()
                                        ))
                                        .controller('C'))
                                .portTiers(t -> t
                                        .minEnergyInput(PortTierLimits.EnergyTier.NORMAL)
                                        .minItemInput(PortTierLimits.ItemTier.NORMAL)
                                        .anyItemOutput())
                        )
                        .build(BLAST_FURNACE);
                event.registerStructure(structure);
            }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(BLAST_FURNACE.withSuffix("_recipe_1"))
                .recipePool(BLAST_FURNACE)
                .inputItem(Ingredient.of(Items.IRON_INGOT),9)
                .outputItem(Items.IRON_NUGGET,10)
                .inputEnergy(20)
                .duration(240)
                .build();
        event.registerRecipe(recipe);

        recipe = Recipes
                .recipe(BLAST_FURNACE.withSuffix("_recipe_2"))
                .recipePool(BLAST_FURNACE)
                .inputItem(Ingredient.of(Items.GOLD_INGOT),9)
                .outputItem(Items.GOLD_NUGGET,10)
                .inputEnergy(20)
                .duration(240)
                .build();
        event.registerRecipe(recipe);

    }

    @SubscribeEvent
    public static void registerWorkstations(RegisterJeiWorkstationsEvent event) {
        // 普通方块/物品 -> MMCR recipe pool 页
        event.addRecipePoolWorkstation(
                BLAST_FURNACE,
                Blocks.BLAST_FURNACE
        );

        // MMCR 机器控制器 -> 任意 JEI 配方类型
        event.addMachineWorkstation(
                BLAST_FURNACE,
                ResourceLocation.parse("minecraft:smelting")
        );
    }

    private BLAST_FURNACE() {
    }

}

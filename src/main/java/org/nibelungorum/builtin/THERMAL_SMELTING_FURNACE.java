package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.*;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

import static cn.howxu.mmcr.internal.registration.BuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:58
 */
@EventBusSubscriber
public class THERMAL_SMELTING_FURNACE {
    private static final Identifier THERMAL_SMELTING_FURNACE = id("thermal_smelting_furnace");

    public static final Identifier THERMAL_SMELTING_COIL_TYPE = id("thermal_smelting_coil");
    public static final Identifier IRON_COIL = id("thermal_smelting_coil_iron");
    public static final Identifier GOLD_COIL = id("thermal_smelting_coil_gold");
    public static final Identifier DIAMOND_COIL = id("thermal_smelting_coil_diamond");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(THERMAL_SMELTING_FURNACE)) {
            var machine = MachineBuilder
                    .machine(THERMAL_SMELTING_FURNACE)
                    .displayNameKey("machine.mmcr.thermal_smelting_furnace")
                    .appearance(a -> a.machineBasicBlock(Identifier.parse("minecraft:smooth_basalt")))
                    .parallelizable(true)
                    .maxParallelism(4)
                    .allowMultithreading()
                    // although it set allowMultithreading, it must work with factory controller
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {

        // do not forget register level first

        event.registerLevelType(new LevelType(THERMAL_SMELTING_COIL_TYPE, Component.translatable("level.mmcr.thermal_smelting_coil")));

        event.registerLevel(new MachineLevel(
                IRON_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                1,
                BlockPredicate.blockState(Blocks.IRON_BLOCK.defaultBlockState()),
                DisplayStack.of(new ItemStack(Holder.direct(Blocks.IRON_BLOCK.asItem(), DataComponentMap.EMPTY))),
                new LevelModifier(0.9d, 1D, 1D, 0, 0))
        );

        event.registerLevel(new MachineLevel(
                DIAMOND_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                3,
                BlockPredicate.blockState(Blocks.DIAMOND_BLOCK.defaultBlockState()),
                DisplayStack.of(new ItemStack(Holder.direct(Blocks.DIAMOND_BLOCK.asItem(), DataComponentMap.EMPTY))),
                new LevelModifier(0.7d, 0.8D, 1D, 4, 1))
        );

        event.registerLevel(new MachineLevel(
                GOLD_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                2,
                BlockPredicate.blockState(Blocks.GOLD_BLOCK.defaultBlockState()),
                DisplayStack.of(new ItemStack(Holder.direct(Blocks.GOLD_BLOCK.asItem(), DataComponentMap.EMPTY))),
                new LevelModifier(0.6d, 0.7D, 2D, 6, 2))
        );

        if (!event.structures().containsKey(THERMAL_SMELTING_FURNACE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("AAA", "XXX", "XXX", "AAA")
                                    .layer("AAA", "X X", "X X", "ADA")
                                    .layer("ABA", "XXX", "XXX", "AAA")
                                    .where('X', any(
                                            block(Blocks.IRON_BLOCK),
                                            block(Blocks.GOLD_BLOCK),
                                            block(Blocks.DIAMOND_BLOCK)
                                    ))
                                    .where('A', any(
                                            block(Blocks.SMOOTH_BASALT),
                                            InterfacePredicates.ports()
                                    ))
                                    .where('D', block(Blocks.REINFORCED_DEEPSLATE))
                                    .controller('B')
                            )
                            .requirements(r -> r
                                    .levelSlot('X', THERMAL_SMELTING_COIL_TYPE)
                            )
                            .portTiers(t -> t
                                    .anyItemInput()
                                    .anyItemOutput()
                                    .anyEnergyInput()
                            ))
                    .build(THERMAL_SMELTING_FURNACE);

            event.registerStructure(structure);
        }
    }

    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_1"),THERMAL_SMELTING_FURNACE)
                .inputItem(Items.RAW_IRON,8)
                .inputItem(Items.COAL,1)
                .outputItem(Items.IRON_INGOT,9)
                .inputEnergy(40)
                .parallelized(true) // allow parallelized
                .duration(200)
                .levelRequirement(THERMAL_SMELTING_COIL_TYPE,IRON_COIL)
                .build();

        event.registerRecipe(recipe);

        recipe = MachineRecipeBuilder
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_2"),THERMAL_SMELTING_FURNACE)
                .inputItem(Items.RAW_GOLD,8)
                .inputItem(Items.COAL,1)
                .outputItem(Items.GOLD_INGOT,9)
                .inputEnergy(40)
                .parallelized(true)
                .duration(200)
                .levelRequirement(THERMAL_SMELTING_COIL_TYPE,GOLD_COIL)
                .build();

        event.registerRecipe(recipe);

        ItemStack output = new ItemStack(Items.DIAMOND,9);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("What a magic recipe")); // some simple data are usable directly

        // some build register data, like enchantment, must use JSON
        JsonObject enchantments_data = new JsonObject();
        enchantments_data.addProperty("minecraft:sharpness", 4);
        DataComponentPredicateSet data_extra = new DataComponentPredicateSet(Map.of(Identifier.parse("minecraft:enchantments"), ComponentPredicate.exact(enchantments_data)));

        recipe = MachineRecipeBuilder
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_3"),THERMAL_SMELTING_FURNACE)
                .inputItem(Items.GOLD_INGOT,8)
                .inputItem(Items.COAL,1)
                .outputItem(output,data_extra)
                .inputEnergy(40)
                .parallelized(true)
                .duration(200)
                .levelRequirement(THERMAL_SMELTING_COIL_TYPE,DIAMOND_COIL)
                .build();

        event.registerRecipe(recipe);
    }
}

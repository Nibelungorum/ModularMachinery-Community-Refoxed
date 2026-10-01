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
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import cn.howxu.mmcr.publicapi.recipe.RecipeSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierScope;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierOperation;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConditions;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 11:58
 */
@EventBusSubscriber
public class THERMAL_SMELTING_FURNACE {
    private static final ResourceLocation THERMAL_SMELTING_FURNACE = id("thermal_smelting_furnace");

    public static final ResourceLocation THERMAL_SMELTING_COIL_TYPE = id("thermal_smelting_coil");
    public static final ResourceLocation IRON_COIL = id("thermal_smelting_coil_iron");
    public static final ResourceLocation GOLD_COIL = id("thermal_smelting_coil_gold");
    public static final ResourceLocation DIAMOND_COIL = id("thermal_smelting_coil_diamond");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(THERMAL_SMELTING_FURNACE)) {
            MachineSpec machine = Machines
                    .machine(THERMAL_SMELTING_FURNACE)
                    .recipePool(THERMAL_SMELTING_FURNACE)
                    .displayNameKey("machine.mmcr.thermal_smelting_furnace")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:smooth_basalt")))
                    .allowModifiers()
                    .parallelizable(true)
                    .maxParallelism(4)
                    .allowMultithreading()
                    // although it set allowMultithreading, it must work with factory controller
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {

        // do not forget register level first

        event.registerLevelType(Levels.type(THERMAL_SMELTING_COIL_TYPE, Component.translatable("level.mmcr.thermal_smelting_coil")));

        event.registerLevel(Levels.level(
                IRON_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                1,
                BlockConditions.blockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new ItemStack(Blocks.IRON_BLOCK),
                Modifiers.bundle(Modifiers.numeric("duration", ModifierScope.INPUT, 0.9D, ModifierOperation.MULTIPLY, false)))
        );

        event.registerLevel(Levels.level(
                DIAMOND_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                3,
                BlockConditions.blockState(Blocks.DIAMOND_BLOCK.defaultBlockState()),
                new ItemStack(Blocks.DIAMOND_BLOCK),
                Modifiers.bundle(
                        Modifiers.numeric("duration", ModifierScope.INPUT, 0.7D, ModifierOperation.MULTIPLY, false),
                        Modifiers.numeric("energy", ModifierScope.INPUT, 0.8D, ModifierOperation.MULTIPLY, false),
                        Modifiers.numeric("parallelism", ModifierScope.MACHINE, 4D, ModifierOperation.ADD, false),
                        Modifiers.numeric("factory_threads", ModifierScope.MACHINE, 1D, ModifierOperation.ADD, false)))
        );

        event.registerLevel(Levels.level(
                GOLD_COIL,
                THERMAL_SMELTING_COIL_TYPE,
                2,
                BlockConditions.blockState(Blocks.GOLD_BLOCK.defaultBlockState()),
                new ItemStack(Blocks.GOLD_BLOCK),
                Modifiers.bundle(
                        Modifiers.numeric("duration", ModifierScope.INPUT, 0.6D, ModifierOperation.MULTIPLY, false),
                        Modifiers.numeric("energy", ModifierScope.INPUT, 0.7D, ModifierOperation.MULTIPLY, false),
                        Modifiers.numeric("output", ModifierScope.OUTPUT, 2D, ModifierOperation.MULTIPLY, false),
                        Modifiers.numeric("parallelism", ModifierScope.MACHINE, 6D, ModifierOperation.ADD, false),
                        Modifiers.numeric("factory_threads", ModifierScope.MACHINE, 2D, ModifierOperation.ADD, false)))
        );

        if (!event.structures().containsKey(THERMAL_SMELTING_FURNACE)) {
            StructureSpec structure = Structures
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
                                            BlockConditions.ports()
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
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_1"))
                .recipePool(THERMAL_SMELTING_FURNACE)
                .inputItem(Items.RAW_IRON,8)
                .inputItem(Items.COAL,1)
                .outputItem(Items.IRON_INGOT,9)
                .inputEnergy(40)
                .parallelized(true) // allow parallelized
                .duration(200)
                .levelRequirement(THERMAL_SMELTING_COIL_TYPE,IRON_COIL)
                .build();

        event.registerRecipe(recipe);

        recipe = Recipes
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_2"))
                .recipePool(THERMAL_SMELTING_FURNACE)
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
        ComponentConstraints data_extra = ComponentConstraints.ofIds(Map.of(ResourceLocation.parse("minecraft:enchantments"), ComponentConditions.exact(enchantments_data)));

        recipe = Recipes
                .recipe(THERMAL_SMELTING_FURNACE.withSuffix("_recipe_3"))
                .recipePool(THERMAL_SMELTING_FURNACE)
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

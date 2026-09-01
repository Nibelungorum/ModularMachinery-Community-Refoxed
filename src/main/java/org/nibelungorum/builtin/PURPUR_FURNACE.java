package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.*;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.*;
import static cn.howxu.mmcr.internal.registration.BuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement.input;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 12:41
 */
@EventBusSubscriber
public class PURPUR_FURNACE {

    private static final Identifier PURPUR_FURNACE = id("purpur_furnace");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(PURPUR_FURNACE)) {
            var machine = MachineBuilder
                    .machine(PURPUR_FURNACE)
                    .displayNameKey("machine.mmcr.purpur_furnace")
                    .appearance(a -> a.machineBasicBlock(Identifier.parse("end_stone_bricks")))
                    .maxParallelism(32).parallelizable(true)
                    .smartInterface(new SmartInterfaceType("mode", 1F, 3F, 1, SmartInterfaceType.ValueType.INTEGER))
                    .smartInterface(new SmartInterfaceType("conversation", 0F, 1F, 0))
                    .smartInterfaceModifier(SmartInterfaceModifier.energy("mode", 1F, 2F, 1F, 2F, RecipeModifier.Operation.MULTIPLY))
                    .smartInterfaceModifier(SmartInterfaceModifier.energy("mode", 2F, 3F, 2F, 4F, RecipeModifier.Operation.MULTIPLY))
                    .smartInterfaceModifier(SmartInterfaceModifier.duration("conversation", 0F, .5F, 1F, 1.5F, RecipeModifier.Operation.MULTIPLY))
                    .smartInterfaceModifier(SmartInterfaceModifier.duration("conversation", .5F, 1F, 1.5F, 2.5F, RecipeModifier.Operation.MULTIPLY))
                    .runningSound(Identifier.parse("minecraft:block.furnace.fire_crackle"))
                    .finishSound(Identifier.parse("minecraft:entity.ender_dragon.growl"))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(PURPUR_FURNACE)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer(" ABBBD ", "       ", "       ", "       ", "  EEE  ", "       ", "       ", "       ")
                                    .layer("AFXXXGD", "  HHH  ", "  III  ", "  JJJ  ", " EHHHE ", " KLLLM ", "       ", "       ")
                                    .layer("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PXXXQ ", "  EHE  ", "   R   ")
                                    .layer("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PX XQ ", "  H H  ", "  R R  ")
                                    .layer("NXXXXXO", " H   H ", " I   I ", " J   J ", "EH   HE", " PXXXQ ", "  EHE  ", "   R   ")
                                    .layer("STXXXUV", "  HCH  ", "  III  ", "  JJJ  ", " EHHHE ", " WYYYZ ", "       ", "       ")
                                    .layer(" abbbV ", "       ", "       ", "       ", "  EEE  ", "       ", "       ", "       ")
                                    .where('X', block("minecraft:end_stone_bricks"))
                                    .where('A', state("minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('B', state("minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('D', state("minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('E', block("minecraft:end_stone_brick_slab"))
                                    .where('F', state("minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=inner_right,waterlogged=false]"))
                                    .where('G', state("minecraft:end_stone_brick_stairs[facing=south,half=bottom,shape=inner_right,waterlogged=false]"))
                                    .where('H', any(
                                            block("minecraft:purpur_pillar"),
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput(),
                                            InterfacePredicates.parallelControllers(),
                                            InterfacePredicates.smartInterface() // Allow a smart interface at this position.
                                    ))
                                    .where('I', block("minecraft:purple_terracotta"))
                                    .where('J', block("minecraft:purpur_block"))
                                    .where('K', state("minecraft:purpur_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('L', state("minecraft:purpur_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('M', state("minecraft:purpur_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('N', state("minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('O', state("minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('P', state("minecraft:purpur_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('Q', state("minecraft:purpur_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('R', block("minecraft:purpur_slab"))
                                    .where('S', state("minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('T', state("minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]"))
                                    .where('U', state("minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]"))
                                    .where('V', state("minecraft:end_stone_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('W', state("minecraft:purpur_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('Y', state("minecraft:purpur_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('Z', state("minecraft:purpur_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('a', state("minecraft:end_stone_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('b', state("minecraft:end_stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
                                    .controller('C')
                            )
                    )
                    .build(PURPUR_FURNACE);
            event.registerStructure(structure);
        }
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(PURPUR_FURNACE.withSuffix("_recipe_1"),PURPUR_FURNACE)
                .inputItem(Ingredient.of(Items.IRON_INGOT),1)
                .outputItem(Items.IRON_NUGGET,10)
                .inputEnergy(20)
                .smartInterface(input("mode",1))
                .duration(200)
                .build();
        event.registerRecipe(recipe);

        recipe = MachineRecipeBuilder
                .recipe(PURPUR_FURNACE.withSuffix("_recipe_2"),PURPUR_FURNACE)
                .inputItem(Ingredient.of(Items.IRON_INGOT),1)
                .outputItem(Items.GOLD_NUGGET,10)
                .inputEnergy(20)
                .smartInterface(input("mode",2))
                .duration(200)
                .build();
        event.registerRecipe(recipe);

        recipe = MachineRecipeBuilder
                .recipe(PURPUR_FURNACE.withSuffix("_recipe_3"),PURPUR_FURNACE)
                .inputItem(Ingredient.of(Items.APPLE),1)
                .outputItem(Items.DIAMOND,2)
                .inputEnergy(40)
                .smartInterface(input("mode",3))
                .smartInterface(input("conversation",0f,0.31f))
                .duration(200)
                .build();
        event.registerRecipe(recipe);

    }

}

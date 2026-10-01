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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.state;
import static cn.howxu.mmcr.publicapi.ApiIds.id;

/**
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public class REACTOR {

    private static final ResourceLocation REACTOR = id("reactor");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(REACTOR)) {
            MachineSpec machine = Machines
                    .machine(REACTOR)
                    .recipePool(REACTOR)
                    .displayNameKey("machine.mmcr.reactor")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("minecraft:blue_ice")))
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(REACTOR)) {
            StructureSpec structure = Structures
                    .structure()
                    .stateSensitive()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("  ABBBD  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                                    .layer(" AEXXXFD ", "   XXX   ", "         ", "         ", "         ", "         ", "         ", "         ")
                                    .layer("AEXXXXXFD", "  GHHHG  ", "  GHHHG  ", "  GHHHG  ", "  IJJJK  ", "         ", "         ", "         ")
                                    .layer("LXXXXXXXM", " XHNONHX ", "  HNONH  ", "  HNONH  ", "  PXXXQ  ", "   RRR   ", "         ", "         ")
                                    .layer("LXXXXXXXM", " XHOXOHX ", "  HOXOH  ", "  HOXOH  ", "  PXXXQ  ", "   RSR   ", "    S    ", "    T    ")
                                    .layer("LXXXXXXXM", " XHNONHX ", "  HNONH  ", "  HNONH  ", "  PXXXQ  ", "   RRR   ", "         ", "         ")
                                    .layer("UVXXXXXWY", "  GHHHG  ", "  GHHHG  ", "  GHHHG  ", "  Zaaab  ", "         ", "         ", "         ")
                                    .layer(" UVXXXWY ", "   XCX   ", "         ", "         ", "         ", "         ", "         ", "         ")
                                    .layer("  UcccY  ", "         ", "         ", "         ", "         ", "         ", "         ", "         ")
                                    .where('X', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.fluidOutput(),
                                            BlockConditions.fluidInput(),
                                            BlockConditions.energyOutput(),
                                            block(Blocks.BLUE_ICE)
                                    ))
                                    .where('A', state("minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('B', state("minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('D', state("minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('E', state("minecraft:deepslate_brick_stairs[facing=south,half=bottom,shape=inner_left,waterlogged=false]"))
                                    .where('F', state("minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=false]"))
                                    .where('G', block(Blocks.POLISHED_DEEPSLATE))
                                    .where('H', block(Blocks.BLACK_STAINED_GLASS))
                                    .where('I', state("minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('J', state("minecraft:polished_deepslate_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('K', state("minecraft:polished_deepslate_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('L', state("minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('M', state("minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('N', block(Blocks.EMERALD_BLOCK))
                                    .where('O', block(Blocks.LAPIS_BLOCK))
                                    .where('P', state("minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('Q', state("minecraft:polished_deepslate_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"))
                                    .where('R', block(Blocks.DEEPSLATE_BRICK_SLAB))
                                    .where('S', block(Blocks.DEEPSLATE_TILES))
                                    .where('T', block(Blocks.LIGHTNING_ROD))
                                    .where('U', state("minecraft:deepslate_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('V', state("minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]"))
                                    .where('W', state("minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]"))
                                    .where('Y', state("minecraft:deepslate_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]"))
                                    .where('Z', state("minecraft:polished_deepslate_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('a', block(Blocks.POLISHED_DEEPSLATE_STAIRS))
                                    .where('b', state("minecraft:polished_deepslate_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]"))
                                    .where('c', state("minecraft:deepslate_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"))
                                    .controller('C')
                            )
                    )
                    .build(REACTOR);
            event.registerStructure(structure);
        }
    }

    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(REACTOR.withSuffix("_recipe_1"))
                .recipePool(REACTOR)
                .inputItem(Items.APPLE, 3)
                .inputFluid(Fluids.WATER, 1)
                .outputItem(Items.DIAMOND, 10)
                .outputFluid(Fluids.LAVA, 250)
                .outputEnergy(200)
                .duration(300)
                .build();
        event.registerRecipe(recipe);

        recipe = Recipes
                .recipe(REACTOR.withSuffix("_recipe_2"))
                .recipePool(REACTOR)
                .inputItem(Items.GOLDEN_APPLE, 2)
                .inputFluid(Fluids.WATER, 800)
                .outputItem(Items.GOLD_INGOT, 2)
                .outputFluid(Fluids.LAVA, 450)
                .inputEnergy(10)
                .outputEnergy(200)
                .duration(200)
                .build();
        event.registerRecipe(recipe);
    }
}

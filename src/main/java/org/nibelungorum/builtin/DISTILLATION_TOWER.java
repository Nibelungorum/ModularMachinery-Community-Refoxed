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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.any;
import static cn.howxu.mmcr.publicapi.structure.BlockConditions.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 13:28
 */
@EventBusSubscriber
public class DISTILLATION_TOWER {

    private static final ResourceLocation DISTILLATION_TOWER = id("distillation_tower");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(DISTILLATION_TOWER)) {
            MachineSpec machine = Machines
                    .machine(DISTILLATION_TOWER)
                    .recipePool(DISTILLATION_TOWER)
                    .displayNameKey("machine.mmcr.distillation_tower")
                    .appearance(a -> a.machineBasicBlock(ResourceLocation.parse("polished_blackstone")))
                    .maxParallelism(32)
                    .parallelizable(true)
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(DISTILLATION_TOWER)) {
            StructureSpec structure = Structures
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("  XXX  ", "  AAA  ", "       ", "       ")
                                    .layer(" XXXXX ", " B   B ", "  ACA  ", "       ")
                                    .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", "  DDD  ")
                                    .layer(" XXXXX ", " B   B ", "  BBB  ", "       ")
                                    .layer("  XXX  ", "  BEB  ", "       ", "       ")
                                    .where('C', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.energyInput(),
                                            block("minecraft:deepslate_bricks")
                                    ))
                                    .where('X', block("minecraft:polished_blackstone"))
                                    .where('A', block("minecraft:deepslate_bricks"))
                                    .where('B', block("minecraft:polished_blackstone_bricks"))
                                    .where('D', block("minecraft:gilded_blackstone"))
                                    .controller('E')
                            )
                    )
                    .expandStructure(s -> s
                            .pattern(p -> p
                                    .layer("  XXX  ", "  AAA  ", "       ", "       ", "       ")
                                    .layer(" XXXXX ", " B   B ", "  ACA  ", "  ACA  ", "       ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", "  DDD  ")
                                    .layer(" XXXXX ", " B   B ", "  BBB  ", "  BBB  ", "       ")
                                    .layer("  XXX  ", "  BEB  ", "       ", "       ", "       ")
                                    .where('C', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.energyInput(),
                                            block("minecraft:deepslate_bricks")
                                    ))
                                    .where('X', block("minecraft:polished_blackstone"))
                                    .where('A', block("minecraft:deepslate_bricks"))
                                    .where('B', block("minecraft:polished_blackstone_bricks"))
                                    .where('D', block("minecraft:gilded_blackstone"))
                                    .controller('E')
                            )
                    )
                    .expandStructure(s -> s
                            .pattern(p -> p
                                    .layer("  XXX  ", "  AAA  ", "       ", "       ", "       ", "       ")
                                    .layer(" XXXXX ", " B   B ", "  ACA  ", "  ACA  ", "  ACA  ", "       ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                                    .layer("XXXXXXX", "A     A", " B   B ", " B   B ", " B   B ", "  DDD  ")
                                    .layer(" XXXXX ", " B   B ", "  BBB  ", "  BBB  ", "  BBB  ", "       ")
                                    .layer("  XXX  ", "  BEB  ", "       ", "       ", "       ", "       ")
                                    .where('C', any(
                                            BlockConditions.itemInput(),
                                            BlockConditions.itemOutput(),
                                            BlockConditions.energyInput(),
                                            block("minecraft:deepslate_bricks")
                                    ))
                                    .where('X', block("minecraft:polished_blackstone"))
                                    .where('A', block("minecraft:deepslate_bricks"))
                                    .where('B', block("minecraft:polished_blackstone_bricks"))
                                    .where('D', block("minecraft:gilded_blackstone"))
                                    .controller('E')
                            )
                    )
                    .build(DISTILLATION_TOWER);
            event.registerStructure(structure);
        }
    }

    @SubscribeEvent
    public static void register(RegisterMachineRecipesEvent event) {
        RecipeSpec recipe = Recipes
                .recipe(DISTILLATION_TOWER.withSuffix("_recipe_1"))
                .recipePool(DISTILLATION_TOWER)
                .inputItem(ItemTags.LOGS, 1)
                .outputItem(Items.COAL, 4)
                .outputItem(Items.GUNPOWDER,3)
                .outputChance(new ItemStack(Items.STICK,2),0.5f)
                .inputEnergy(20)
                .allowPartialOutputs(true) // 允许丢弃
                .duration(200)
                .build();
        event.registerRecipe(recipe);
    }
}

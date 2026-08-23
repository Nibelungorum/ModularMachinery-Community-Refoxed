package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 13:28
 */
@EventBusSubscriber
public class DISTILLATION_TOWER {

    private static final Identifier DISTILLATION_TOWER = id("distillation_tower");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(DISTILLATION_TOWER)) {
            var machine = MachineBuilder
                    .machine(DISTILLATION_TOWER)
                    .displayNameKey("machine.mmcr.distillation_tower")
                    .appearance(a -> a.machineBasicBlock(Identifier.parse("polished_blackstone")))
                    .maxParallelAmount(32)
                    .parallelizable(true)
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(DISTILLATION_TOWER)) {
            var structure = MachineStructureBuilder
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
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput(),
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
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput(),
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
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput(),
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
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(DISTILLATION_TOWER.withSuffix("_recipe_1"), DISTILLATION_TOWER)
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

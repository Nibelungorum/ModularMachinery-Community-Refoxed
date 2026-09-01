package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRecipesEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.*;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

import static cn.howxu.mmcr.internal.registration.BuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.*;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 13:37
 */
@EventBusSubscriber
public class SPACE {
    private static final Identifier SPACE_ELEVATOR = id("space_elevator");
    private static final Identifier SPACE_REASSEMBLER = id("space_reassembler");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(SPACE_ELEVATOR) && !event.definitions().containsKey(SPACE_REASSEMBLER)) {
            var machine = MachineBuilder
                    .machine(SPACE_ELEVATOR)
                    .displayNameKey("machine.mmcr.space_elevator")
                    .appearance(a -> a
                            .machineBasicBlock("smooth_quartz")
                            .controllerBaseTexture(Identifier.parse("block/quartz_block_bottom"))
                            .formedPortBaseTexture(Identifier.parse("block/quartz_block_bottom"))
                    )
                    .role(MachineRole.HOST)
                    .acceptedModule(SPACE_REASSEMBLER)
                    .build();
            event.registerMachine(machine);

            machine = MachineBuilder
                    .machine(SPACE_REASSEMBLER)
                    .displayNameKey("machine.mmcr.space_reassembler")
                    .appearance(a -> a.machineBasicBlock("quartz_pillar"))
                    .role(MachineRole.MODULE)
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {

        if (!event.structures().containsKey(SPACE_ELEVATOR) && !event.structures().containsKey(SPACE_REASSEMBLER)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("      XXXXX      ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("     XXAAAXX     ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("    XXXAAAXXX    ", "        B        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("   XXXXAAAXXXX   ", "                 ", "                 ", "                 ", "                 ", "        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("  XXXXXXXXXXXXX  ", "                 ", "                 ", "                 ", "        X        ", "       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer(" XXAAAXXXXXAAAXX ", "       XXX       ", "       DDD       ", "       XXX       ", "       XXX       ", "      XXXXX      ", "       XXX       ", "       X X       ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("XXXAAAXXXXXAAAXXX", "    B  X X  B    ", "       D D       ", "       X X       ", "      XX XX      ", "     XXX XXX     ", "       XXX       ", "        X        ", "        X        ", "        X        ", "        X        ", "        X        ")
                                    .layer(" XXAAAXXXXXAAAXX ", "       XXX       ", "       DED       ", "       XXX       ", "       XXX       ", "      XXXXX      ", "       XXX       ", "       X X       ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("  XXXXXXXXXXXXX  ", "                 ", "                 ", "                 ", "        X        ", "       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("   XXXXXXXXXXX   ", "                 ", "                 ", "                 ", "                 ", "        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("    XXXXXXXXX    ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("     XXXXXXX     ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("      XXXXX      ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("       XXX       ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .layer("        X        ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ")
                                    .where('X', block("minecraft:smooth_quartz"))
                                    .where('A', block("minecraft:amethyst_block"))
                                    .where('B', coupler())
                                    .where('D', any(
                                            block("minecraft:smooth_quartz"),
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput()
                                    ))
                                    .controller('E')
                            )
                    )
                    .build(SPACE_ELEVATOR);
            event.registerStructure(structure);

            structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("AAA", "XBX", "XBX", "XDX")
                                    .layer("AAA", "BEB", "B B", "DDD")
                                    .layer("AAA", "XFX", "XBX", "XDX")
                                    .where('X', block("minecraft:quartz_pillar"))
                                    .where('A', block("minecraft:amethyst_block"))
                                    .where('B', any(
                                            block("minecraft:smooth_quartz"),
                                            InterfacePredicates.anyOfItemInput(),
                                            InterfacePredicates.anyOfItemOutput(),
                                            InterfacePredicates.anyOfEnergyInput()
                                    ))
                                    .where('D', block("minecraft:glass"))
                                    .where('E', coupler())
                                    .controller('F')
                            )
                    )
                    .build(SPACE_REASSEMBLER);
            event.registerStructure(structure);

        }
    }

    private static DataComponentPredicateSet potion(String potionId) {
        JsonObject contents = new JsonObject();
        contents.addProperty("potion", potionId);

        return new DataComponentPredicateSet(Map.of(
                Identifier.parse("minecraft:potion_contents"),
                ComponentPredicate.exact(contents)));
    }

    // recipe has multiple id use, do not use event.recipes().containsKey(BLAST_FURNACE)
    @SubscribeEvent
    public static void register(MMCRMachineRecipesEvent event) {
        var recipe = MachineRecipeBuilder
                .recipe(SPACE_REASSEMBLER.withSuffix("_space_reassembler_1"), SPACE_REASSEMBLER)
                .inputItem(Ingredient.of(Items.POTION), 1, potion("minecraft:water"), 1F)
                .outputItem(new ItemStack(Items.POTION), potion("minecraft:healing"))
                .inputEnergy(100)
                .parallelized(true)
                .duration(100)
                .requiredHost(SPACE_ELEVATOR)
                .build();

        event.registerRecipe(recipe);

        recipe = MachineRecipeBuilder
                .recipe(SPACE_ELEVATOR.withSuffix("_recipe_1"), SPACE_ELEVATOR)
                .inputItem(Items.APPLE, 1)
                .outputItem(Items.GOLDEN_APPLE, 3)
                .inputEnergy(100)
                .parallelized(true)
                .duration(1000)
                .build();

        event.registerRecipe(recipe);

    }

}

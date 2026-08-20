package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.MachineApi;
import cn.howxu.mmcr.api.publicapi.RecipeApi;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeModifierValue;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in declarations exercised through the public API consumer path.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinDefinitions {
    private static final Identifier BLAST_FURNACE = id("blast_furnace");
    private static final Identifier ALLOY_FURNACE = id("alloy_furnace");
    private static final Identifier CRACKER = id("cracker");

    private PublicBuiltinDefinitions() {
    }

    public static void register() {
        machineDefinitions().values().forEach(MachineApi::registerMachine);
    }

    public static void registerRecipes() {
        recipeDefinitions().values().forEach(RecipeApi::registerRecipe);
    }

    public static Map<Identifier, MachineDefinition> machineDefinitions() {
        Map<Identifier, MachineDefinition> definitions = new LinkedHashMap<>();
        definitions.put(BLAST_FURNACE, MachineBuilder.machine(BLAST_FURNACE)
                .displayNameKey("machine.mmcr.blast_furnace")
                .pattern(pattern -> pattern.layer("XXX", "XCX", "XXX")
                        .where('X', BlockPredicate.block(Blocks.IRON_BLOCK))
                        .where('C', BlockPredicate.block(Blocks.BLAST_FURNACE)).controller('C'))
                .portTiers(tiers -> tiers.minEnergyInput(PortTiers.EnergyTier.LUDICROUS)
                        .minItemInput(PortTiers.ItemTier.NORMAL).anyItemOutput())
                .maxParallelism(Integer.MAX_VALUE).parallelizable(true)
                .factory(factory -> factory.hasFactory(true).threadLimit(4)).build());
        definitions.put(ALLOY_FURNACE, MachineBuilder.machine(ALLOY_FURNACE)
                .displayNameKey("machine.mmcr.alloy_furnace")
                .appearance(appearance -> appearance.machineBasicBlock(Identifier.withDefaultNamespace("bricks")))
                .pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', BlockPredicate.block(Blocks.BRICKS))
                        .where('M', BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))
                .stage(stage -> stage.extension().pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', BlockPredicate.block(Blocks.BRICKS))
                        .where('M', BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))).build());
        definitions.put(CRACKER, MachineBuilder.machine(CRACKER)
                .displayNameKey("machine.mmcr.cracker")
                .pattern(pattern -> pattern.layer("XXX", "XCX", "XXX")
                        .where('X', BlockPredicate.block(Blocks.POLISHED_DIORITE))
                        .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))
                .build());
        return Map.copyOf(definitions);
    }

    public static Map<Identifier, MachineRecipeDefinition> recipeDefinitions() {
        Map<Identifier, MachineRecipeDefinition> definitions = new LinkedHashMap<>();
        definitions.put(id("blast_furnace_iron_to_nugget"), MachineRecipeBuilder.recipe(
                id("blast_furnace_iron_to_nugget"), BLAST_FURNACE).duration(200)
                .inputItem(Items.IRON_INGOT, 1).inputEnergy(1).outputItem(Items.IRON_NUGGET, 1).build());
        definitions.put(id("cracker_coal_lapis"), MachineRecipeBuilder.recipe(id("cracker_coal_lapis"), id("cracker"))
                .duration(160).inputItem(Items.COAL, 8).inputItem(Items.LAPIS_LAZULI, 1).inputEnergy(100)
                .outputItem(Items.REDSTONE, 4).outputFluid(Fluids.WATER, 500).build());
        return Map.copyOf(definitions);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mmcr", path);
    }
}

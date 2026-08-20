package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
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
    private static final Identifier REACTOR = id("reactor");
    private static final Identifier PURPUR_FURNACE = id("purpur_furnace");

    private PublicBuiltinDefinitions() {
    }

    public static Map<Identifier, MachineDefinition> machineDefinitions() {
        Map<Identifier, MachineDefinition> definitions = new LinkedHashMap<>();
        definitions.put(BLAST_FURNACE, MachineBuilder.machine(BLAST_FURNACE)
                .displayNameKey("machine.mmcr.blast_furnace")
                .maxParallelism(Integer.MAX_VALUE).parallelizable(true)
                .factory(factory -> factory.hasFactory(true).threadLimit(4)).build());
        definitions.put(ALLOY_FURNACE, MachineBuilder.machine(ALLOY_FURNACE)
                .displayNameKey("machine.mmcr.alloy_furnace")
                .appearance(appearance -> appearance.machineBasicBlock(Identifier.withDefaultNamespace("bricks")))
                .build());
        definitions.put(CRACKER, MachineBuilder.machine(CRACKER)
                .displayNameKey("machine.mmcr.cracker")
                .build());
        definitions.put(REACTOR, MachineBuilder.machine(REACTOR)
                .displayNameKey("machine.mmcr.reactor")
                .appearance(appearance -> appearance.machineBasicBlock(Identifier.withDefaultNamespace("blue_ice")))
                .build());
        definitions.put(PURPUR_FURNACE, MachineBuilder.machine(PURPUR_FURNACE)
                .displayNameKey("machine.mmcr.purpur_furnace")
                .appearance(appearance -> appearance.machineBasicBlock(Identifier.withDefaultNamespace("end_stone_bricks")))
                .build());
        return Map.copyOf(definitions);
    }

    public static Map<Identifier, MachineStructureDefinition> structureDefinitions() {
        Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
        structures.put(BLAST_FURNACE, MachineStructureBuilder.structure().fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("XXX", "XCX", "XXX")
                .where('X', BlockPredicate.block(Blocks.IRON_BLOCK))
                .where('C', BlockPredicate.block(Blocks.BLAST_FURNACE)).controller('C'))
                .portTiers(tiers -> tiers.minEnergyInput(cn.howxu.mmcr.api.publicapi.machine.PortTiers.EnergyTier.LUDICROUS)
                        .minItemInput(cn.howxu.mmcr.api.publicapi.machine.PortTiers.ItemTier.NORMAL).anyItemOutput()))
                .build(BLAST_FURNACE));
        structures.put(ALLOY_FURNACE, MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', BlockPredicate.block(Blocks.BRICKS)).where('M', BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("XXX", "XMX", "XCX")
                        .where('X', BlockPredicate.block(Blocks.BRICKS)).where('M', BlockPredicate.block(Blocks.BLAST_FURNACE))
                        .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))).build(ALLOY_FURNACE));
        structures.put(CRACKER, MachineStructureBuilder.structure().fullStructure(stage -> stage.pattern(pattern -> pattern
                .layer("XXX", "XCX", "XXX").where('X', BlockPredicate.block(Blocks.POLISHED_DIORITE))
                .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))).build(CRACKER));
        structures.put(REACTOR, MachineStructureBuilder.structure().fullStructure(stage -> stage.pattern(pattern -> pattern
                .pattern("AAAAA", "AXXXA", "AXXXA", "AXXXA", "AAAAA")
                .pattern("AAAAA", "AXXXA", "AXXXA", "AXXXA", "AAAAA")
                .pattern("AAAAA", "AXXXA", "AXCXA", "AXXXA", "AAAAA")
                .where('A', BlockPredicate.block(Blocks.DEEPSLATE_BRICK_STAIRS)).where('X', BlockPredicate.block(Blocks.BLUE_ICE))
                .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))).build(REACTOR));
        structures.put(PURPUR_FURNACE, MachineStructureBuilder.structure().fullStructure(stage -> stage.pattern(pattern -> pattern
                .pattern("AAAAA", "AXXXA", "AXXXA", "AXXXA", "AAAAA")
                .pattern("AAAAA", "AXXXA", "AXXXA", "AXXXA", "AAAAA")
                .pattern("AAAAA", "AXXXA", "AXCXA", "AXXXA", "AAAAA")
                .where('A', BlockPredicate.block(Blocks.END_STONE_BRICK_STAIRS)).where('X', BlockPredicate.block(Blocks.END_STONE_BRICKS))
                .where('C', BlockPredicate.block(Blocks.FURNACE)).controller('C'))).build(PURPUR_FURNACE));
        return Map.copyOf(structures);
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

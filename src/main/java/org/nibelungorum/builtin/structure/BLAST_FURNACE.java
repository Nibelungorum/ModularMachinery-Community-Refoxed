package org.nibelungorum.builtin.structure;

import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.any;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.block;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/22 20:57
 */
public class BLAST_FURNACE {

    private static final Identifier BLAST_FURNACE = id("blast_furnace"); // equal to mmcr:blast_furnace

    public static MachineStructureDefinition get(){
        return MachineStructureBuilder
                .structure()
                .fullStructure(s -> s
                        .pattern(p -> p
                                .layer("AXA", "XIX", "XXX")
                                .layer("XXX", "I I", "XBX")
                                .layer("AXA", "XCX", "XXX")
                                .where('X', any(block(Blocks.IRON_BLOCK)))
                                .where('A', any(block(Blocks.IRON_BLOCK)))
                                .where('B', any(block(Blocks.IRON_BLOCK)))
                                .where('I', any(block(Blocks.IRON_BLOCK)))
                                .controller('C'))
                        .portTiers(t -> t
                                .minEnergyInput(PortTiers.EnergyTier.NORMAL)
                                .minItemInput(PortTiers.ItemTier.NORMAL)
                                .anyItemOutput())
                )
                .build(BLAST_FURNACE);
    }
}

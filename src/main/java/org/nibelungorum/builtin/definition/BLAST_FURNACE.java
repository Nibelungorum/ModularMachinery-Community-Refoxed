package org.nibelungorum.builtin.definition;

import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import net.minecraft.resources.Identifier;

import static cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.id;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/22 20:49
 */
public class BLAST_FURNACE {

    private static final Identifier BLAST_FURNACE = id("blast_furnace"); // equal to mmcr:blast_furnace

    public static MachineDefinition get(){
        return MachineBuilder
                .machine(BLAST_FURNACE)
                .displayNameKey("machine.mmcr.blast_furnace")
                .allowMultithreading()
                .maxParallelAmount(Integer.MAX_VALUE)
                .parallelizable(true)
                .factory(factory -> factory.hasFactory(true).threadLimit(4)) // allow multi thread limit 4
                .build();
    }
}

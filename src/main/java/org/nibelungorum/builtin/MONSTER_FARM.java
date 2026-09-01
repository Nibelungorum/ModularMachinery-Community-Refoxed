package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.internal.registration.BuiltinRegistration.id;
import static cn.howxu.mmcr.api.publicapi.machine.BlockPredicate.*;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/8/23 13:53
 */
@EventBusSubscriber
public class MONSTER_FARM {
    private static final Identifier MONSTER_FARM = id("monster_farm");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(MONSTER_FARM)) {
            var machine = MachineBuilder
                    .machine(MONSTER_FARM)
                    .displayNameKey("machine.mmcr.monster_farm")
                    .controller(builder -> builder
                            .id(MONSTER_FARM.withSuffix("_controller"))
                            .allowVerticalFacing(true)
                            .fullyRotationallySymmetric(false)
                            .requireVerticalFacing(true)
                    )
                    .build();
            event.registerMachine(machine);
        }
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        if (!event.structures().containsKey(MONSTER_FARM)) {
            var structure = MachineStructureBuilder
                    .structure()
                    .fullStructure(s -> s
                            .pattern(p -> p
                                    .layer("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
                                    .layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA")
                                    .layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA")
                                    .layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA")
                                    .layer("ABBBA", "B   B", "B   B", "B   B", "ABBBA")
                                    .layer("XXXXX", "XXXXX", "XXDXX", "XXXXX", "XXXXX")
                                    .where('X', any(
                                            tag(BlockTags.LOGS)
                                        )
                                    )
                                    .where('A', block(Blocks.OAK_LOG))
                                    .where('B', block(Blocks.IRON_BARS))
                                    .controller('D'))
                    )
                    .build(MONSTER_FARM);
            event.registerStructure(structure);
        }
    }

}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import net.minecraft.resources.ResourceLocation;

import static cn.howxu.mmcr.api.publicapi.ApiIds.id;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/9/4 16:20
 */
public class ARTIFICIAL_STAR {
    public static final ResourceLocation ARTIFICIAL_STAR = id("artificial_star");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(ARTIFICIAL_STAR)) {
            var machine = MachineBuilder
                    .machine(ARTIFICIAL_STAR)
                    .recipePool(ARTIFICIAL_STAR)
                    .displayNameKey("machine.mmcr.artificial_star")
                    .build();
            event.registerMachine(machine);
        }
    }

}

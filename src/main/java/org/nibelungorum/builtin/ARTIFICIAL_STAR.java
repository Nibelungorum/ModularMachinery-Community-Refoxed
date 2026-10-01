package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.machine.MachineSpec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/9/4 16:20
 */
public class ARTIFICIAL_STAR {
    public static final ResourceLocation ARTIFICIAL_STAR = id("artificial_star");

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(ARTIFICIAL_STAR)) {
            MachineSpec machine = Machines
                    .machine(ARTIFICIAL_STAR)
                    .recipePool(ARTIFICIAL_STAR)
                    .displayNameKey("machine.mmcr.artificial_star")
                    .build();
            event.registerMachine(machine);
        }
    }

}

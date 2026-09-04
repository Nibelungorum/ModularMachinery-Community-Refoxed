package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRendersEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import org.nibelungorum.client.ArtificialStarRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.internal.registration.BuiltinRegistration.id;

/**
 * @description: TODO
 * @author: HowXu
 * @date: 2026/9/4 16:20
 */
@EventBusSubscriber
public class ARTIFICIAL_STAR {
    public static final Identifier ARTIFICIAL_STAR = id("artificial_star");

    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        if (!event.definitions().containsKey(ARTIFICIAL_STAR)) {
            var machine = MachineBuilder
                    .machine(ARTIFICIAL_STAR)
                    .displayNameKey("machine.mmcr.artificial_star")
                    .build();
            event.registerMachine(machine);
        }
    }

}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterMachinesEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/** Public built-in machine subscriber.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public final class PublicBuiltinMachineDefinitions {
    private PublicBuiltinMachineDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineDefinition> machineDefinitions() {
        return PublicBuiltinDefinitions.machineDefinitions();
    }

    @SubscribeEvent
    public static void register(MMCRRegisterMachinesEvent event) {
        machineDefinitions().values().forEach(event::registerMachine);
    }

}

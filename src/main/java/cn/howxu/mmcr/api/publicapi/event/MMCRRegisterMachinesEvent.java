package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.MachineApi;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import net.neoforged.bus.api.Event;

/** Event used to register public machine definitions during startup.
 * @author howxu <dev@howxu.cn>
 */
public final class MMCRRegisterMachinesEvent extends Event {
    public void registerMachine(MachineDefinition definition) {
        MachineApi.registerMachine(definition);
    }
}

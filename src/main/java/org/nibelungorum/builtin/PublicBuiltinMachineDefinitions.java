package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.publicapi.event.MMCRRegisterMachinesEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;

/** Public built-in machine definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicBuiltinMachineDefinitions {
    private PublicBuiltinMachineDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineDefinition> machineDefinitions() {
        return PublicBuiltinDefinitions.machineDefinitions();
    }

    public static void registerDefaults() {
        machineDefinitions().values().forEach(definition ->
                MachineDefinitions.register(PublicMachineAdapter.toStartupRegistration(definition,
                        PublicBuiltinDefinitions.structureDefinitions().get(definition.id()))));
    }

    public static void register(MMCRRegisterMachinesEvent event) {
        machineDefinitions().values().forEach(event::registerMachine);
    }

}

package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.MMCR;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Public built-in machine definitions.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID)
public final class PublicBuiltinMachineDefinitions {
    private PublicBuiltinMachineDefinitions() {
    }

    public static java.util.Map<net.minecraft.resources.Identifier, MachineDefinition> machineDefinitions() {
        return PublicBuiltinDefinitions.machineDefinitions();
    }

    @SubscribeEvent
    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        machineDefinitions().forEach((id, definition) -> {
            if (!event.definitions().containsKey(id)) event.registerMachine(definition);
        });
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        java.util.Map<net.minecraft.resources.Identifier, MachineStructureDefinition> structures =
                PublicBuiltinDefinitions.structureDefinitions();
        structures.forEach((id, structure) -> {
            if (!event.structures().containsKey(id)) event.registerStructure(structure);
        });
    }

}

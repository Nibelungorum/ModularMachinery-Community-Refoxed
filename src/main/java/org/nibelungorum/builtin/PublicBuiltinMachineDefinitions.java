package org.nibelungorum.builtin;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;

/** Public built-in machine definitions.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = PublicBuiltinRegistration.MOD_ID)
public final class PublicBuiltinMachineDefinitions {
    private PublicBuiltinMachineDefinitions() {
    }

    public static Map<Identifier, MachineDefinition> machineDefinitions() {
        return PublicBuiltinDefinitions.machineDefinitions();
    }

    @SubscribeEvent
    public static void registerDefinitions(MMCRMachineDefinationsEvent event) {
        java.util.Map<net.minecraft.resources.Identifier, MachineDefinition> definitions = machineDefinitions();
        PublicBuiltinRegistration.logger().debug("Registering {} built-in machines", definitions.size());
        definitions.forEach((id, definition) -> {
            if (!event.definitions().containsKey(id)) event.registerMachine(definition);
        });
    }

    @SubscribeEvent
    public static void registerStructures(MMCRMachineStructuresEvent event) {
        // PublicBuiltinDefinitions.modifierDefinitions().forEach(event::registerModifier);
        java.util.Map<net.minecraft.resources.Identifier, MachineStructureDefinition> structures =
                PublicBuiltinDefinitions.structureDefinitions();
        structures.forEach((id, structure) -> {
            if (!event.structures().containsKey(id)) event.registerStructure(structure);
        });
    }

}

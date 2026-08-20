package cn.howxu.mmcr.api.publicapi.event;

import net.minecraft.resources.Identifier;

import java.util.Collection;

/** Canonical event for collecting complete machine structures and their requirements.
 * @author howxu <dev@howxu.cn>
 */
public class MMCRMachineStructuresEvent extends RegisterMachineStructuresEvent {
    public MMCRMachineStructuresEvent(Collection<Identifier> machineIds) {
        super(machineIds);
    }

    public static MMCRMachineStructuresEvent prepare(Collection<Identifier> machineIds) {
        MMCRMachineStructuresEvent prepared = new MMCRMachineStructuresEvent(machineIds);
        if (current != null) {
            prepared.levelTypes.putAll(current.levelTypes);
            prepared.levels.putAll(current.levels);
            prepared.modifiers.putAll(current.modifiers);
        }
        current = prepared;
        return prepared;
    }

    public static MMCRMachineStructuresEvent current() {
        if (!(current instanceof MMCRMachineStructuresEvent)) {
            MMCRMachineStructuresEvent canonical = new MMCRMachineStructuresEvent(
                    current == null ? java.util.Set.of() : current.machineIds);
            if (current != null) {
                canonical.structures.putAll(current.structures);
                canonical.levelTypes.putAll(current.levelTypes);
                canonical.levels.putAll(current.levels);
                canonical.modifiers.putAll(current.modifiers);
            }
            current = canonical;
        }
        return (MMCRMachineStructuresEvent) current;
    }

    public static void resetCollector() {
        current = null;
    }
}

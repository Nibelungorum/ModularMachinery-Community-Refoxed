package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Event used to collect structures for already registered machine definitions.
 * @author howxu <dev@howxu.cn>
 */
public final class RegisterMachineStructuresEvent extends Event {
    private final Set<Identifier> machineIds;
    private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
    private boolean frozen;

    public RegisterMachineStructuresEvent(Collection<Identifier> machineIds) {
        this.machineIds = Set.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
    }

    public void registerStructure(Identifier machineId, UnaryOperator<MachineStructureBuilder> consumer) {
        if (frozen) throw new IllegalStateException("Machine structures are frozen");
        Objects.requireNonNull(machineId, "machineId");
        if (!machineIds.contains(machineId)) {
            throw new IllegalArgumentException("Unknown machine definition: " + machineId);
        }
        if (structures.containsKey(machineId)) {
            throw new IllegalStateException("Duplicate machine structure: " + machineId);
        }
        MachineStructureBuilder builder = Objects.requireNonNull(consumer, "consumer")
                .apply(MachineStructureBuilder.structure());
        structures.put(machineId, Objects.requireNonNull(builder, "consumer result").build(machineId));
    }

    public Map<Identifier, MachineStructureDefinition> structures() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(structures));
    }

    public void freeze() {
        frozen = true;
    }
}

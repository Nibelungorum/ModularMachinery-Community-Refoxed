package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
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
    private static RegisterMachineStructuresEvent current;
    private Set<Identifier> machineIds;
    private final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
    private final Map<Identifier, LevelType> levelTypes = new LinkedHashMap<>();
    private final Map<Identifier, MachineLevel> levels = new LinkedHashMap<>();
    private boolean frozen;

    public RegisterMachineStructuresEvent(Collection<Identifier> machineIds) {
        this.machineIds = Set.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
    }

    public static RegisterMachineStructuresEvent prepare(Collection<Identifier> machineIds) {
        RegisterMachineStructuresEvent prepared = new RegisterMachineStructuresEvent(machineIds);
        if (current != null) {
            prepared.levelTypes.putAll(current.levelTypes);
            prepared.levels.putAll(current.levels);
        }
        current = prepared;
        return current;
    }

    public static RegisterMachineStructuresEvent current() {
        if (current == null) current = new RegisterMachineStructuresEvent(Set.of());
        return current;
    }

    public static void resetCollector() {
        current = null;
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

    public void registerStructure(MachineStructureDefinition structure) {
        if (frozen) throw new IllegalStateException("Machine structures are frozen");
        Objects.requireNonNull(structure, "structure");
        if (!machineIds.contains(structure.machineId())) {
            throw new IllegalArgumentException("Unknown machine definition: " + structure.machineId());
        }
        if (structures.putIfAbsent(structure.machineId(), structure) != null) {
            throw new IllegalStateException("Duplicate machine structure: " + structure.machineId());
        }
    }

    public void registerLevelType(LevelType type) {
        if (frozen) throw new IllegalStateException("Machine structures are frozen");
        Objects.requireNonNull(type, "type");
        if (levelTypes.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Duplicate machine level type: " + type.id());
        }
    }

    public void registerLevel(MachineLevel level) {
        if (frozen) throw new IllegalStateException("Machine structures are frozen");
        Objects.requireNonNull(level, "level");
        if (!levelTypes.containsKey(level.typeId())) {
            throw new IllegalStateException("Unknown machine level type: " + level.typeId());
        }
        if (levels.putIfAbsent(level.id(), level) != null) {
            throw new IllegalStateException("Duplicate machine level: " + level.id());
        }
    }

    public Map<Identifier, MachineStructureDefinition> structures() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(structures));
    }

    public Map<Identifier, LevelType> levelTypes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(levelTypes));
    }

    public Map<Identifier, MachineLevel> levels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(levels));
    }

    public void freeze() {
        frozen = true;
    }
}

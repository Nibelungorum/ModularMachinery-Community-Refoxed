package cn.howxu.mmcr.api.registration;

import cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Canonical event for collecting complete machine structures and their requirements.
 * @author howxu <dev@howxu.cn>
 */
public final class StructureRegistration {
    private static StructureRegistration current;
    protected Set<ResourceLocation> machineIds;
    protected final Map<ResourceLocation, MachineStructureDefinition> structures = new LinkedHashMap<>();
    protected final Map<ResourceLocation, LevelType> levelTypes = new LinkedHashMap<>();
    protected final Map<ResourceLocation, MachineLevel> levels = new LinkedHashMap<>();
    private final Map<ResourceLocation, cn.howxu.mmcr.api.machine.definition.MachineLevel> levelDeclarations = new LinkedHashMap<>();
    private boolean frozen;
    private Snapshot snapshot;

    public StructureRegistration(Collection<ResourceLocation> machineIds) {
        this.machineIds = Set.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
    }

    public static StructureRegistration prepare(Collection<ResourceLocation> machineIds) {
        StructureRegistration prepared = new StructureRegistration(machineIds);
        if (current != null) {
            prepared.levelTypes.putAll(current.levelTypes);
            prepared.levels.putAll(current.levels);
            prepared.levelDeclarations.putAll(current.levelDeclarations);
        }
        current = prepared;
        return prepared;
    }

    public static StructureRegistration current() {
        if (current == null) current = new StructureRegistration(Set.of());
        return current;
    }

    public static void resetCollector() {
        current = null;
    }

    public void registerStructure(ResourceLocation machineId, UnaryOperator<MachineStructureBuilder> consumer) {
        requireOpen();
        require(machineId, "machine id");
        if (!machineIds.contains(machineId)) {
            throw new ApiRegistrationException("Unknown machine definition: " + machineId);
        }
        if (structures.containsKey(machineId)) {
            throw new ApiRegistrationException("Duplicate machine structure: " + machineId);
        }
        try {
            if (consumer == null) {
                throw new ApiRegistrationException("Consumer for machine " + machineId + " must not be null");
            }
            MachineStructureBuilder builder = consumer.apply(MachineStructureBuilder.structure());
            structures.put(machineId, Objects.requireNonNull(builder, "consumer result").build(machineId));
        } catch (ApiRegistrationException exception) {
            String message = exception.getMessage();
            if (message != null && message.contains(machineId.toString())) throw exception;
            throw new ApiRegistrationException("Invalid machine structure " + machineId + ": " + message, exception);
        } catch (RuntimeException exception) {
            throw new ApiRegistrationException("Invalid machine structure " + machineId + ": " + exception.getMessage(), exception);
        }
    }

    public void registerStructure(MachineStructureDefinition structure) {
        requireOpen();
        require(structure, "structure");
        if (!machineIds.contains(structure.machineId())) {
            throw new ApiRegistrationException("Unknown machine definition: " + structure.machineId());
        }
        if (structures.putIfAbsent(structure.machineId(), structure) != null) {
            throw new ApiRegistrationException("Duplicate machine structure: " + structure.machineId());
        }
    }

    public void registerLevelType(LevelType type) {
        requireOpen();
        require(type, "level type");
        if (levelTypes.putIfAbsent(type.id(), type) != null) {
            throw new ApiRegistrationException("Duplicate machine level type: " + type.id());
        }
    }

    public void registerLevel(MachineLevel level) {
        requireOpen();
        require(level, "level");
        if (levelDeclarations.containsKey(level.id()) || levels.putIfAbsent(level.id(), level) != null) {
            throw new ApiRegistrationException("Duplicate machine level: " + level.id());
        }
    }

    public void registerLevel(cn.howxu.mmcr.api.machine.definition.MachineLevel level) {
        requireOpen();
        require(level, "level");
        if (levels.containsKey(level.id()) || levelDeclarations.putIfAbsent(level.id(), level) != null) {
            throw new ApiRegistrationException("Duplicate machine level: " + level.id());
        }
    }

    public Map<ResourceLocation, MachineStructureDefinition> structures() {
        return snapshot == null ? immutable(structures) : snapshot.structures();
    }

    public Map<ResourceLocation, LevelType> levelTypes() {
        return snapshot == null ? immutable(levelTypes) : snapshot.levelTypes();
    }

    public Map<ResourceLocation, MachineLevel> levels() {
        return snapshot == null ? immutable(levels) : snapshot.levels();
    }

    /** Pending declarations remain inspectable without resolving their block states. */
    public Map<ResourceLocation, cn.howxu.mmcr.api.machine.definition.MachineLevel> levelDeclarations() {
        return immutable(levelDeclarations);
    }

    public Snapshot freeze() {
        if (frozen) return snapshot;
        levels.values().forEach(level -> {
            if (!levelTypes.containsKey(level.typeId())) throw new ApiRegistrationException(
                    "Machine level " + level.id() + " refers to unknown machine level type " + level.typeId());
        });
        levelDeclarations.values().forEach(level -> {
            if (!levelTypes.containsKey(level.typeId())) throw new ApiRegistrationException(
                    "Machine level " + level.id() + " refers to unknown machine level type " + level.typeId());
        });
        structures.values().forEach(structure -> structure.stages().forEach(stage -> {
            stage.requirements().levelSlots().values().forEach(typeId -> {
                if (!levelTypes.containsKey(typeId)) throw new ApiRegistrationException(
                        "Structure " + structure.machineId() + " refers to unknown machine level type " + typeId);
            });
        }));
        Map<ResourceLocation, MachineLevel> resolvedLevels = new LinkedHashMap<>(levels);
        levelDeclarations.forEach((id, declaration) -> {
            try {
                resolvedLevels.put(id, MachineDefinitionConverter.toMachineLevel(declaration));
            } catch (RuntimeException exception) {
                throw new ApiRegistrationException("Invalid machine level " + id + ": " + exception.getMessage(), exception);
            }
        });
        levels.putAll(resolvedLevels);
        levelDeclarations.clear();
        frozen = true;
        snapshot = new Snapshot(structures, levelTypes, levels);
        return snapshot;
    }

    private void requireOpen() {
        if (frozen) throw new ApiRegistrationException("Machine structures are frozen");
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new ApiRegistrationException(name + " must not be null");
        return value;
    }

    private static <K, V> Map<K, V> immutable(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public record Snapshot(Map<ResourceLocation, MachineStructureDefinition> structures,
            Map<ResourceLocation, LevelType> levelTypes, Map<ResourceLocation, MachineLevel> levels) {
        public Snapshot {
            structures = immutable(structures);
            levelTypes = immutable(levelTypes);
            levels = immutable(levels);
        }
    }
}

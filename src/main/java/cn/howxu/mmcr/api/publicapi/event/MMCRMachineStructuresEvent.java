package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.recipe.modifier.ModifierItemKey;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** Canonical event for collecting complete machine structures and their requirements.
 * @author howxu <dev@howxu.cn>
 */
public class MMCRMachineStructuresEvent extends Event {
    protected static MMCRMachineStructuresEvent current;
    protected Set<Identifier> machineIds;
    protected final Map<Identifier, MachineStructureDefinition> structures = new LinkedHashMap<>();
    protected final Map<Identifier, LevelType> levelTypes = new LinkedHashMap<>();
    protected final Map<Identifier, MachineLevel> levels = new LinkedHashMap<>();
    protected final Map<Identifier, ModifierDefinition> modifiers = new LinkedHashMap<>();
    protected final Map<Identifier, List<ItemStack>> modifierItems = new LinkedHashMap<>();
    private final Map<ModifierItemKey, Identifier> modifierItemKeys = new LinkedHashMap<>();
    private boolean frozen;
    private Snapshot snapshot;

    public MMCRMachineStructuresEvent(Collection<Identifier> machineIds) {
        this.machineIds = Set.copyOf(Objects.requireNonNull(machineIds, "machineIds"));
    }

    public static MMCRMachineStructuresEvent prepare(Collection<Identifier> machineIds) {
        MMCRMachineStructuresEvent prepared = new MMCRMachineStructuresEvent(machineIds);
        if (current != null) {
            prepared.levelTypes.putAll(current.levelTypes);
            prepared.levels.putAll(current.levels);
            prepared.modifiers.putAll(current.modifiers);
            current.modifierItems().forEach((modifierId, stacks) -> stacks.forEach(stack ->
                    prepared.registerModifierItem(stack, modifierId)));
        }
        current = prepared;
        return prepared;
    }

    public static MMCRMachineStructuresEvent current() {
        if (current == null) current = new MMCRMachineStructuresEvent(Set.of());
        return current;
    }

    public static void resetCollector() {
        current = null;
    }

    public void registerStructure(Identifier machineId, UnaryOperator<MachineStructureBuilder> consumer) {
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
            throw new ApiRegistrationException("Invalid machine structure " + machineId + ": " + message);
        } catch (RuntimeException exception) {
            throw new ApiRegistrationException("Invalid machine structure " + machineId + ": " + exception.getMessage());
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

    public void registerLevelType(cn.howxu.mmcr.api.publicapi.machine.LevelType type) {
        registerLevelType(MachineDefinitionConverter.toLevelType(require(type, "level type")));
    }

    public void registerLevel(MachineLevel level) {
        requireOpen();
        require(level, "level");
        if (!levelTypes.containsKey(level.typeId())) {
            throw new ApiRegistrationException("Unknown machine level type: " + level.typeId());
        }
        if (levels.putIfAbsent(level.id(), level) != null) {
            throw new ApiRegistrationException("Duplicate machine level: " + level.id());
        }
    }

    public void registerLevel(cn.howxu.mmcr.api.publicapi.machine.MachineLevel level) {
        registerLevel(MachineDefinitionConverter.toMachineLevel(require(level, "level")));
    }

    public void registerModifier(Identifier id, ModifierDefinition definition) {
        requireOpen();
        require(id, "modifier id");
        require(definition, "modifier definition");
        if (modifiers.putIfAbsent(id, definition) != null) {
            throw new ApiRegistrationException("Duplicate machine modifier: " + id);
        }
    }

    public void registerModifierItem(ItemStack stack, Identifier modifierId) {
        requireOpen();
        require(stack, "modifier item");
        require(modifierId, "modifier id");
        ItemStack normalized = stack.copyWithCount(1);
        ModifierItemKey key = ModifierItemKey.of(normalized);
        if (modifierItemKeys.putIfAbsent(key, modifierId) != null) {
            throw new ApiRegistrationException("Duplicate machine modifier item binding");
        }
        modifierItems.computeIfAbsent(modifierId, ignored -> new ArrayList<>()).add(normalized);
    }

    public Map<Identifier, MachineStructureDefinition> structures() {
        return snapshot == null ? immutable(structures) : snapshot.structures();
    }

    public Map<Identifier, LevelType> levelTypes() {
        return snapshot == null ? immutable(levelTypes) : snapshot.levelTypes();
    }

    public Map<Identifier, MachineLevel> levels() {
        return snapshot == null ? immutable(levels) : snapshot.levels();
    }

    public Map<Identifier, ModifierDefinition> modifiers() {
        return snapshot == null ? immutable(modifiers) : snapshot.modifiers();
    }

    public Map<Identifier, List<ItemStack>> modifierItems() {
        return snapshot == null ? immutableItemBindings(modifierItems) : snapshot.modifierItems();
    }

    public Snapshot freeze() {
        if (frozen) return snapshot;
        structures.values().forEach(structure -> structure.stages().forEach(stage -> {
            stage.requirements().levelSlots().values().forEach(typeId -> {
                if (!levelTypes.containsKey(typeId)) throw new ApiRegistrationException(
                        "Structure " + structure.machineId() + " refers to unknown machine level type " + typeId);
            });
            stage.requirements().modifierReplacements().values().stream().flatMap(Collection::stream)
                    .forEach(replacement -> {
                        if (!modifiers.containsKey(replacement.modifierId())) throw new ApiRegistrationException(
                                "Structure " + structure.machineId() + " refers to unknown machine modifier "
                                        + replacement.modifierId());
                });
        }));
        modifierItems.keySet().forEach(modifierId -> {
            if (!modifiers.containsKey(modifierId)) throw new ApiRegistrationException(
                    "Modifier item binding refers to unknown machine modifier " + modifierId);
        });
        frozen = true;
        snapshot = new Snapshot(structures, levelTypes, levels, modifiers, modifierItems);
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

    private static Map<Identifier, List<ItemStack>> immutableItemBindings(
            Map<Identifier, List<ItemStack>> source) {
        Map<Identifier, List<ItemStack>> copy = new LinkedHashMap<>();
        source.forEach((modifierId, stacks) -> copy.put(modifierId,
                List.copyOf(stacks.stream().map(ItemStack::copy).toList())));
        return Collections.unmodifiableMap(copy);
    }

    public record Snapshot(Map<Identifier, MachineStructureDefinition> structures,
            Map<Identifier, LevelType> levelTypes, Map<Identifier, MachineLevel> levels,
            Map<Identifier, ModifierDefinition> modifiers,
            Map<Identifier, List<ItemStack>> modifierItems) {
        public Snapshot(Map<Identifier, MachineStructureDefinition> structures,
                Map<Identifier, LevelType> levelTypes, Map<Identifier, MachineLevel> levels,
                Map<Identifier, ModifierDefinition> modifiers) {
            this(structures, levelTypes, levels, modifiers, Map.of());
        }

        public Snapshot {
            structures = immutable(structures);
            levelTypes = immutable(levelTypes);
            levels = immutable(levels);
            modifiers = immutable(modifiers);
            modifierItems = immutableItemBindings(modifierItems);
        }

        @Override
        public Map<Identifier, List<ItemStack>> modifierItems() {
            return immutableItemBindings(modifierItems);
        }
    }
}

package cn.howxu.mmcr.api.machine.level;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Startup registry for typed machine levels.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineLevelRegistry {
    private static final Map<Identifier, LevelType> TYPES = new LinkedHashMap<>();
    private static final Map<Identifier, MachineLevel> LEVELS = new LinkedHashMap<>();
    private static final Map<Identifier, List<MachineLevel>> LEVELS_BY_TYPE = new LinkedHashMap<>();
    private static boolean registrationOpen;

    private MachineLevelRegistry() {
    }

    static void beginRegistration() {
        TYPES.clear();
        LEVELS.clear();
        LEVELS_BY_TYPE.clear();
        registrationOpen = true;
    }

    static void freezeRegistration() {
        registrationOpen = false;
    }

    public static void installSnapshot(Collection<LevelType> types, Collection<MachineLevel> levels) {
        Map<Identifier, LevelType> nextTypes = new LinkedHashMap<>();
        Map<Identifier, MachineLevel> nextLevels = new LinkedHashMap<>();
        Map<Identifier, List<MachineLevel>> nextByType = new LinkedHashMap<>();
        types.forEach(type -> {
            Objects.requireNonNull(type, "type");
            if (nextTypes.putIfAbsent(type.id(), type) != null) {
                throw new IllegalStateException("Machine level type already registered: " + type.id());
            }
            nextByType.put(type.id(), new ArrayList<>());
        });
        levels.forEach(level -> validateAndAdd(level, nextTypes, nextLevels, nextByType));
        TYPES.clear();
        TYPES.putAll(nextTypes);
        LEVELS.clear();
        LEVELS.putAll(nextLevels);
        LEVELS_BY_TYPE.clear();
        nextByType.forEach((id, values) -> LEVELS_BY_TYPE.put(id, new ArrayList<>(values)));
        registrationOpen = false;
    }

    static void registerType(LevelType type) {
        requireRegistrationOpen();
        Objects.requireNonNull(type, "type");
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Machine level type already registered: " + type.id());
        }
        LEVELS_BY_TYPE.put(type.id(), new ArrayList<>());
    }

    static void registerLevel(MachineLevel level) {
        requireRegistrationOpen();
        validateAndAdd(level, TYPES, LEVELS, LEVELS_BY_TYPE);
    }

    private static void validateAndAdd(MachineLevel level, Map<Identifier, LevelType> types,
            Map<Identifier, MachineLevel> levels, Map<Identifier, List<MachineLevel>> levelsByType) {
        Objects.requireNonNull(level, "level");
        if (!types.containsKey(level.typeId())) {
            throw new IllegalStateException("Unknown machine level type: " + level.typeId());
        }
        if (levels.containsKey(level.id())) {
            throw new IllegalStateException("Machine level already registered: " + level.id());
        }
        if (!(level.statePredicate() instanceof BlockPredicate.OfBlockState statePredicate)) {
            throw new IllegalArgumentException("Machine levels require an exact block state predicate");
        }

        List<MachineLevel> levelsForType = levelsByType.get(level.typeId());
        for (MachineLevel registered : levelsForType) {
            if (registered.priority() == level.priority()) {
                throw new IllegalStateException("Machine level priority already registered for type: " + level.typeId());
            }
        }
        for (MachineLevel registered : levels.values()) {
            BlockPredicate.OfBlockState registeredPredicate = (BlockPredicate.OfBlockState) registered.statePredicate();
            if (registeredPredicate.matches(statePredicate.state())) {
                throw new IllegalStateException("Machine level state already registered: " + level.id());
            }
        }

        levels.put(level.id(), level);
        levelsForType.add(level);
    }

    public static MachineLevel getLevel(Identifier id) {
        return LEVELS.get(id);
    }

    public static LevelType getType(Identifier id) {
        return TYPES.get(id);
    }

    public static Collection<MachineLevel> getLevels(Identifier typeId) {
        return levelsForType(typeId);
    }

    public static Optional<MachineLevel> findLevel(BlockState state) {
        for (MachineLevel level : LEVELS.values()) {
            if (level.statePredicate().matches(state)) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    public static List<MachineLevel> levelsForType(Identifier typeId) {
        List<MachineLevel> levels = LEVELS_BY_TYPE.get(typeId);
        return levels == null ? List.of() : Collections.unmodifiableList(levels);
    }

    private static void requireRegistrationOpen() {
        if (!registrationOpen) {
            throw new IllegalStateException("Machine level registration rejected: registry phase is closed");
        }
    }
}

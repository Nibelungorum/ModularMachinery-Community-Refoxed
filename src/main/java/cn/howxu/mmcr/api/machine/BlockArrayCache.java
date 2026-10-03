package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class BlockArrayCache {

    private static volatile Map<Key, BlockArray> CACHE = Map.of();

    private BlockArrayCache() {
    }

    public static BlockArray get(BlockArray pattern, Direction facing) {
        return get(pattern, facing, Direction.SOUTH);
    }

    public static synchronized BlockArray get(BlockArray pattern, Direction facing, Direction rollFacing) {
        if (pattern.isEmpty()) return pattern;
        Direction normalizedRoll = BlockRotator.normalizedRoll(facing, rollFacing);
        Key key = new Key(pattern, facing, normalizedRoll);
        BlockArray cached = CACHE.get(key);
        if (cached != null) return cached;
        BlockArray rotated = rotate(key);
        Map<Key, BlockArray> replacement = new LinkedHashMap<>(CACHE);
        replacement.put(key, rotated);
        CACHE = Map.copyOf(replacement);
        return rotated;
    }

    public static void buildCache(Collection<Machine> machines) {
        CACHE = buildCacheSnapshot(machines);
    }

    static Map<Key, BlockArray> buildCacheSnapshot(Collection<Machine> machines) {
        Map<Key, BlockArray> replacement = new LinkedHashMap<>();
        for (Machine machine : machines) {
            for (MachineStructureStage stage : machine.structureStages()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    add(replacement, stage.pattern(), facing);
                    for (DynamicPatternSpec dynamicPattern : stage.dynamicPatterns()) {
                        add(replacement, dynamicPattern.startPattern(), facing);
                        if (dynamicPattern.endPattern() != null) add(replacement, dynamicPattern.endPattern(), facing);
                    }
                }
            }
        }
        return Map.copyOf(replacement);
    }

    static void installCache(Map<Key, BlockArray> cache) {
        CACHE = Map.copyOf(cache);
    }

    static BlockArray get(Map<Key, BlockArray> cache, BlockArray pattern, Direction facing) {
        Key key = new Key(pattern, facing, Direction.SOUTH);
        BlockArray cached = cache.get(key);
        return cached != null ? cached : rotate(key);
    }

    public static void clear() {
        CACHE = Map.of();
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        clear();
    }

    private static BlockArray rotate(Key key) {
        Map<BlockPos, BlockPredicate> rotated = new LinkedHashMap<>();
        Rotation rotation = rotationFor(key.facing());
        for (var entry : key.pattern().pattern().entrySet()) {
            rotated.put(BlockRotator.rotateSouthTo(entry.getKey(), key.facing(), key.rollFacing()),
                    rotatePredicate(entry.getValue(), key.facing(), key.rollFacing(), rotation));
        }
        Map<BlockPos, List<String>> rotatedTags = new LinkedHashMap<>();
        for (var entry : key.pattern().tagsByPosition().entrySet()) {
            rotatedTags.put(BlockRotator.rotateSouthTo(entry.getKey(), key.facing(), key.rollFacing()), entry.getValue());
        }
        Map<BlockPos, Character> rotatedSymbols = new LinkedHashMap<>();
        for (var entry : key.pattern().symbolsByPosition().entrySet()) {
            rotatedSymbols.put(BlockRotator.rotateSouthTo(entry.getKey(), key.facing(), key.rollFacing()), entry.getValue());
        }
        return new BlockArray(rotated, rotatedTags, rotatedSymbols);
    }

    private static Rotation rotationFor(Direction facing) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.SOUTH) == facing) return rotation;
        }
        return Rotation.NONE;
    }

    private static BlockPredicate rotatePredicate(BlockPredicate predicate, Direction facing, Direction rollFacing,
                                                  Rotation rotation) {
        return switch (predicate) {
            case BlockPredicate.OfBlockState state ->
                    new BlockPredicate.OfBlockState(rotateState(state.state(), facing, rollFacing, rotation));
            case BlockPredicate.AnyOf anyOf ->
                    new BlockPredicate.AnyOf(anyOf.children().stream()
                            .map(child -> rotatePredicate(child, facing, rollFacing, rotation))
                            .toList());
            default -> predicate;
        };
    }

    private static BlockState rotateState(BlockState state, Direction facing, Direction rollFacing, Rotation rotation) {
        if (!facing.getAxis().isVertical()) return state.rotate(rotation);

        return transformDirectionalProperties(state, direction -> BlockRotator.rotateDirection(direction, facing, rollFacing));
    }

    /** Converts a captured world state back to the SOUTH-facing template orientation. */
    public static BlockState normalizeState(BlockState state, Direction facing, Direction rollFacing) {
        if (!facing.getAxis().isVertical()) {
            for (Rotation rotation : Rotation.values()) {
                if (rotation.rotate(facing) == Direction.SOUTH) return state.rotate(rotation);
            }
        }
        UnaryOperator<Direction> transform = direction -> BlockRotator.rotateDirection(direction, facing, rollFacing);
        BlockState normalized = state;
        for (Property<?> property : state.getProperties()) {
            Comparable<?> value = state.getValue(property);
            if (!(value instanceof Direction) && !(value instanceof Direction.Axis)) continue;
            // Restricted properties can make the existing forward transform non-bijective.
            Comparable<?> source = property.getPossibleValues().stream()
                    .filter(candidate -> transformDirectionalValue(property, candidate, transform).equals(value))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Cannot normalize " + state + " for facing " + facing + " and roll " + rollFacing));
            normalized = setValue(normalized, property, source);
        }
        return normalized;
    }

    private static BlockState transformDirectionalProperties(BlockState state, UnaryOperator<Direction> transform) {
        BlockState rotated = state;
        for (Property<?> property : state.getProperties()) {
            Comparable<?> value = state.getValue(property);
            Comparable<?> transformed = transformDirectionalValue(property, value, transform);
            if (!transformed.equals(value)) rotated = setValue(rotated, property, transformed);
        }
        return rotated;
    }

    private static Comparable<?> transformDirectionalValue(Property<?> property, Comparable<?> value,
                                                           UnaryOperator<Direction> transform) {
        Comparable<?> transformed = value;
        if (value instanceof Direction direction) {
            transformed = transform.apply(direction);
        } else if (value instanceof Direction.Axis axis) {
            Direction axisDirection = switch (axis) {
                case X -> Direction.EAST;
                case Y -> Direction.UP;
                case Z -> Direction.SOUTH;
            };
            transformed = transform.apply(axisDirection).getAxis();
        }
        return property.getPossibleValues().contains(transformed) ? transformed : value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setValue(BlockState state, Property<?> property, Comparable<?> value) {
        return state.setValue((Property) property, (Comparable) value);
    }

    private static void add(Map<Key, BlockArray> cache, BlockArray pattern, Direction facing) {
        Key key = new Key(pattern, facing, Direction.SOUTH);
        cache.put(key, rotate(key));
    }

    record Key(BlockArray pattern, Direction facing, Direction rollFacing) {
    }
}

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
        installCache(buildCacheSnapshot(machines));
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
        CompiledMachinePattern.clearScanPlanCache();
    }

    static BlockArray get(Map<Key, BlockArray> cache, BlockArray pattern, Direction facing) {
        Key key = new Key(pattern, facing, Direction.SOUTH);
        BlockArray cached = cache.get(key);
        return cached != null ? cached : rotate(key);
    }

    public static void clear() {
        CACHE = Map.of();
        CompiledMachinePattern.clearScanPlanCache();
    }

    /** Test-only helper. Never call from production code. */
    public static void clearForTesting() {
        clear();
    }

    private static BlockArray rotate(Key key) {
        BlockArray pattern = key.pattern();
        Direction facing = key.facing();
        Direction roll = key.rollFacing();
        if (facing == Direction.SOUTH) {
            Rotation rotation = rotationFor(facing);
            PositionTable<BlockPredicate> predicates = pattern.positions().rotate(facing, roll,
                    predicate -> rotatePredicate(predicate, facing, roll, rotation));
            if (predicates == pattern.positions()) return pattern;
            return new BlockArray(predicates, pattern.tagsByPosition(), pattern.symbolsByPosition());
        }
        Map<BlockPos, List<String>> tags = new LinkedHashMap<>();
        pattern.tagsByPosition().forEach((pos, values) ->
                tags.put(BlockRotator.rotateSouthTo(pos, facing, roll), values));
        // Block states rotate with the structure, so those patterns still need real rotated predicates.
        // Everything else (cached blocks, tags, deferred blocks, air, couplers) is orientation-invariant
        // and is served through a view: one small object per facing instead of a copy of every entry.
        PositionTable<BlockPredicate> predicates = needsPredicateRotation(pattern.positions())
                ? pattern.positions().rotate(facing, roll,
                        predicate -> rotatePredicate(predicate, facing, roll, rotationFor(facing)))
                : new RotatedPositions<>(pattern.positions(), facing, roll);
        // The symbol table is shared the same way.
        return new BlockArray(predicates, tags, new GlyphView(pattern.glyphs(), facing, roll));
    }

    /** Mirrors {@link #rotatePredicate}: only block states (directly or inside alternatives) rotate. */
    private static boolean needsPredicateRotation(PositionTable<BlockPredicate> table) {
        return table.anyValueMatches(BlockArrayCache::rotatesWithStructure);
    }

    private static boolean rotatesWithStructure(BlockPredicate predicate) {
        return switch (predicate) {
            case BlockPredicate.OfBlockState ignored -> true;
            case BlockPredicate.AnyOf anyOf -> anyOf.children().stream().anyMatch(BlockArrayCache::rotatesWithStructure);
            default -> false;
        };
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
        // Map equality ignores encounter order; rotations must retain it for diagnostics and sentinels.
        @Override public boolean equals(Object other) {
            return other instanceof Key key && key.pattern == pattern && key.facing == facing && key.rollFacing == rollFacing;
        }
        @Override public int hashCode() {
            return (System.identityHashCode(pattern) * 31 + facing.ordinal()) * 31 + rollFacing.ordinal();
        }
    }
}

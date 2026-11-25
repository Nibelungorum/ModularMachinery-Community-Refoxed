package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Immutable ordered position map. Straight air runs encode encounter order; local masks answer coordinate lookups.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class PackedAirPositions<V> extends AbstractMap<BlockPos, V> implements PositionTable<V> {
    record Run<V>(int index, int pointsBefore, BlockPos start, Direction step, int length, V value) {
        BlockPos at(int offset) { return offset == 0 ? start : start.relative(step, offset); }
    }

    private final Map<BlockPos, V> points;
    private final List<BlockPos> keys;
    private final List<V> values;
    private final List<Run<V>> runs;
    private final Function<BlockPos, V> airLookup;
    private final int size;
    private final int shift;
    private final int[] directory;

    private PackedAirPositions(Map<BlockPos, V> points, List<Run<V>> runs, Function<BlockPos, V> airLookup, int size) {
        this.points = Collections.unmodifiableMap(points);
        keys = Collections.unmodifiableList(new ArrayList<>(points.keySet()));
        values = Collections.unmodifiableList(new ArrayList<>(points.values()));
        this.runs = List.copyOf(runs);
        this.airLookup = airLookup;
        this.size = size;
        // A coarse ordinal directory, bounded by the number of physical runs rather than logical cells.
        int bits = 5;
        while ((size >>> bits) > runs.size() && bits < 30) bits++;
        shift = bits;
        directory = runs.isEmpty() ? new int[0] : new int[((size - 1) >>> shift) + 1];
        for (int page = 0, r = 0; page < directory.length; page++) {
            int index = page << shift;
            while (r < runs.size() && runs.get(r).index + runs.get(r).length <= index) r++;
            directory[page] = r;
        }
    }

    @Override public int size() { return size; }
    @Override public V get(Object key) {
        V value = points.get(key);
        return value != null || runs.isEmpty() || !(key instanceof BlockPos pos) ? value : airLookup.apply(pos);
    }
    @Override public boolean containsKey(Object key) { return points.containsKey(key) || get(key) != null; }
    @Override public boolean containsValue(Object value) {
        return points.containsValue(value) || runs.stream().anyMatch(run -> Objects.equals(run.value, value));
    }

    private int nextRun(int i) {
        int r = directory[i >>> shift];
        while (r < runs.size() && runs.get(r).index + runs.get(r).length <= i) r++;
        return r;
    }
    private int pointIndex(int i, int r) {
        return r == runs.size() ? i - (size - keys.size()) : i - runs.get(r).index + runs.get(r).pointsBefore;
    }
    private BlockPos positionAt(int i) {
        int r = nextRun(i);
        if (r < runs.size() && i >= runs.get(r).index) return runs.get(r).at(i - runs.get(r).index);
        return keys.get(pointIndex(i, r));
    }
    private V valueAt(int i) {
        int r = nextRun(i);
        if (r < runs.size() && i >= runs.get(r).index) return runs.get(r).value;
        return values.get(pointIndex(i, r));
    }
    /** Server-thread lookup scratch. Neither this cursor nor its mutable relative position escapes into a batch. */
    final class Cursor implements PositionCursor<V> {
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        private Run<V> run;
        private int from, until, offset;
        private BlockPos position;
        private V value;

        @Override public int runEnd(int index) { return run == null ? index + 1 : until; }
        @Override public Direction step() { return run.step; }
        @Override public BlockPos position() { return position; }
        @Override public V value() { return value; }

        @Override public void seek(int index) {
            if (runs.isEmpty()) {
                position = keys.get(index);
                value = values.get(index);
                return;
            }
            if (index < from || index >= until) {
                int r = nextRun(index);
                run = r < runs.size() && runs.get(r).index <= index ? runs.get(r) : null;
                if (run == null) {
                    from = r == 0 ? 0 : runs.get(r - 1).index + runs.get(r - 1).length;
                    until = r == runs.size() ? size : runs.get(r).index;
                    offset = pointIndex(index, r) - index;
                } else {
                    from = run.index;
                    until = from + run.length;
                }
            }
            if (run == null) {
                position = keys.get(index + offset);
                value = values.get(index + offset);
            } else {
                int d = index - from;
                position = scratch.setWithOffset(run.start, run.step.getStepX() * d,
                        run.step.getStepY() * d, run.step.getStepZ() * d);
                value = run.value;
            }
        }
    }

    @Override public Cursor cursor() { return new Cursor(); }
    @Override public List<BlockPos> positions() { return runs.isEmpty() ? keys : view(this::positionAt); }
    @Override public List<V> orderedValues() { return runs.isEmpty() ? values : view(this::valueAt); }
    private <T> List<T> view(IntFunction<T> get) {
        return new AbstractList<>() {
            @Override public int size() { return size; }
            @Override public T get(int index) { return get.apply(Objects.checkIndex(index, size)); }
        };
    }
    @Override public List<Entry<BlockPos, V>> entries() {
        List<BlockPos> positions = positions();
        List<V> orderedValues = orderedValues();
        return view(i -> new SimpleImmutableEntry<>(positions.get(i), orderedValues.get(i)));
    }
    @Override public Set<Entry<BlockPos, V>> entrySet() {
        if (runs.isEmpty()) return points.entrySet();
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Entry<BlockPos, V>> iterator() { return entries().iterator(); }
        };
    }

    @Override public void visit(Visitor<V> visitor) {
        int p = 0;
        for (Run<V> run : runs) {
            while (p < run.pointsBefore) { visitor.accept(keys.get(p), Direction.EAST, 1, values.get(p)); p++; }
            visitor.accept(run.start, run.step, run.length, run.value);
        }
        while (p < keys.size()) { visitor.accept(keys.get(p), Direction.EAST, 1, values.get(p)); p++; }
    }
    @Override public void visitPoints(BiConsumer<BlockPos, V> consumer) { points.forEach(consumer); }

    @Override public boolean anyValueMatches(Predicate<V> test) {
        for (Run<V> run : runs) {
            if (test.test(run.value)) return true;
        }
        for (V value : values) {
            if (test.test(value)) return true;
        }
        return false;
    }

    @Override public void visitPointsAndExceptions(Set<BlockPos> exceptions, BiConsumer<BlockPos, V> consumer) {
        if (exceptions.isEmpty() || runs.isEmpty()) { visitPoints(consumer); return; }
        Set<SectionPos> sections = new HashSet<>();
        exceptions.forEach(pos -> sections.add(SectionPos.of(pos)));
        visit((start, step, length, value) -> {
            if (length == 1) { consumer.accept(start, value); return; }
            for (int i = 0; i < length;) {
                BlockPos pos = start.relative(step, i);
                int n = sectionSpan(pos, step, length - i);
                if (sections.contains(SectionPos.of(pos))) for (int j = 0; j < n; j++) {
                    BlockPos key = pos.relative(step, j);
                    if (exceptions.contains(key)) consumer.accept(key, value);
                }
                i += n;
            }
        });
    }
    @Override public void visitBounds(Consumer<BlockPos> consumer) {
        keys.forEach(consumer);
        for (var run : runs) { consumer.accept(run.start); consumer.accept(run.at(run.length - 1)); }
    }
    @Override public void forValues(Predicate<V> select, BiConsumer<BlockPos, V> consumer) {
        visit((start, step, length, value) -> {
            if (select.test(value)) for (int i = 0; i < length; i++) consumer.accept(start.relative(step, i), value);
        });
    }

    @Override public PackedAirPositions<V> rotate(Direction facing, Direction roll, UnaryOperator<V> transform) {
        if (facing == Direction.SOUTH) {
            Map<BlockPos, V> changes = new LinkedHashMap<>();
            points.forEach((pos, value) -> {
                V next = transform.apply(value);
                if (next != value) changes.put(pos, next);
            });
            if (changes.isEmpty()) return this;
            Map<BlockPos, V> updated = new LinkedHashMap<>(points);
            updated.putAll(changes);
            return new PackedAirPositions<>(updated, runs, airLookup, size);
        }
        Map<BlockPos, V> rotated = new LinkedHashMap<>();
        points.forEach((pos, value) -> rotated.put(BlockRotator.rotateSouthTo(pos, facing, roll), transform.apply(value)));
        List<Run<V>> order = new ArrayList<>(runs.size());
        for (var run : runs) order.add(new Run<>(run.index, run.pointsBefore, BlockRotator.rotateSouthTo(run.start, facing, roll),
                BlockRotator.rotateDirection(run.step, facing, roll), run.length, run.value));
        // Only direction-invariant pure-air predicates (or their glyphs) enter runs.
        Function<BlockPos, V> lookup = airLookup;
        return new PackedAirPositions<>(rotated, order, pos -> lookup.apply(BlockRotator.normalizeFromFace(pos, facing, roll)), size);
    }

    @SuppressWarnings("unchecked")
    static <V> PositionTable<V> copy(Map<BlockPos, V> source, BiPredicate<BlockPos, V> pack) {
        if (source != null && source instanceof PositionTable<?> table) return (PositionTable<V>) table;
        Writer<V> out = new Writer<>();
        if (source != null) source.forEach((pos, value) -> out.add(pos, Direction.EAST, 1, value, pack.test(pos, value)));
        return out.finish();
    }

    /** One owner, one publication. Input to add() is unique; put() also accepts repeated wire positions. */
    static final class Writer<V> {
        private final Map<BlockPos, V> points = new LinkedHashMap<>();
        private final List<Run<V>> runs = new ArrayList<>();
        private final Map<SectionPos, Map<V, BitSet>> masks = new HashMap<>();
        private final Map<BlockPos, V> updates = new LinkedHashMap<>();
        private BlockPos start;
        private Direction step;
        private int length, size;
        private V value;
        private boolean frozen;

        void add(BlockPos pos, Direction direction, int n, V v, boolean pack) {
            if (frozen) throw new IllegalStateException("Position builder is frozen");
            if (pack && length != 0 && Objects.equals(value, v)) {
                Direction axis = step;
                if (length == 1) axis = n == 1 ? adjacent(start, pos) : direction;
                if (axis != null && (n == 1 || direction == axis)
                        && (long) start.getX() + (long) axis.getStepX() * length == pos.getX()
                        && (long) start.getY() + (long) axis.getStepY() * length == pos.getY()
                        && (long) start.getZ() + (long) axis.getStepZ() * length == pos.getZ()) {
                    step = axis;
                    length = Math.addExact(length, n);
                    return;
                }
            }
            flush();
            if (pack) { start = pos.immutable(); step = direction; length = n; value = v; }
            else {
                for (int i = 0; i < n; i++) points.put(pos.relative(direction, i).immutable(), v);
                size = Math.addExact(size, n);
            }
        }
        private void flush() {
            if (length == 0) return;
            if (length == 1) points.put(start, value);
            else {
                runs.add(new Run<>(size, points.size(), start, step, length, value));
                for (int i = 0; i < length;) {
                    BlockPos pos = start.relative(step, i);
                    int n = sectionSpan(pos, step, length - i);
                    BitSet bits = masks.computeIfAbsent(SectionPos.of(pos), k -> new HashMap<>())
                            .computeIfAbsent(value, k -> new BitSet());
                    int local = local(pos);
                    if (step == Direction.EAST) bits.set(local, local + n);
                    else {
                        int stride = step.getStepX() + 16 * step.getStepZ() + 256 * step.getStepY();
                        for (int j = 0; j < n; j++) bits.set(local + j * stride);
                    }
                    i += n;
                }
            }
            size = Math.addExact(size, length);
            length = 0;
        }
        void put(BlockPos pos, V v, boolean pack) {
            if (frozen) throw new IllegalStateException("Position builder is frozen");
            if (points.containsKey(pos) || pendingContains(pos) || lookup(masks, pos) != null) {
                updates.put(pos.immutable(), v);
            } else add(pos, Direction.EAST, 1, v, pack);
        }
        private boolean pendingContains(BlockPos pos) {
            if (length == 0) return false;
            long dx = (long) pos.getX() - start.getX(), dy = (long) pos.getY() - start.getY(), dz = (long) pos.getZ() - start.getZ();
            long d = dx * step.getStepX() + dy * step.getStepY() + dz * step.getStepZ();
            return d >= 0 && d < length && dx == d * step.getStepX() && dy == d * step.getStepY() && dz == d * step.getStepZ();
        }
        private static Direction adjacent(BlockPos a, BlockPos b) {
            long dx = (long) b.getX() - a.getX(), dy = (long) b.getY() - a.getY(), dz = (long) b.getZ() - a.getZ();
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) return null;
            if (dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
            if (dy != 0) return dy > 0 ? Direction.UP : Direction.DOWN;
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        /** Copies another table verbatim, keeping existing run structure where it survives. */
        PositionTable<V> finishCopy(PositionTable<V> source) {
            source.visit((start, step, length, value) -> add(start, step, length, value, length > 1));
            return finish();
        }

        /** Packs the writer's input into the run store, then compacts it into the row-indexed table. */
        PositionTable<V> finish() {
            return RowRlePositions.from(finishPacked());
        }

        /** The uncompacted packing; kept for internal paths that build another writer on top of it. */
        PackedAirPositions<V> finishPacked() {
            flush();
            frozen = true;
            var index = masks;
            var result = new PackedAirPositions<>(points, runs, pos -> lookup(index, pos), size);
            return updates.isEmpty() ? result : result.replacing(updates);
        }
    }

    @Override public PositionTable<V> union(PositionTable<V> extension,
                                            Function<BlockPos, IllegalArgumentException> conflict) {
        if (extension.isEmpty() || extension == this) return this;
        if (isEmpty()) return extension instanceof PositionTable<V> table ? table
                : new Writer<V>().finishCopy(extension);
        Writer<V> out = new Writer<>();
        visit((start, step, n, value) -> out.add(start, step, n, value, n > 1));
        extension.visit((start, step, n, value) -> {
            for (int i = 0; i < n; i++) {
                BlockPos pos = start.relative(step, i);
                V previous = get(pos);
                if (previous != null) {
                    if (!previous.equals(value)) throw conflict.apply(pos);
                } else if (points.containsKey(pos)) {
                    // LinkedHashMap.putIfAbsent fills a null value without moving its key.
                    if (value != null) out.put(pos, value, n > 1);
                } else out.add(pos, step, 1, value, n > 1);
            }
        });
        return out.finish();
    }

    private PackedAirPositions<V> replacing(Map<BlockPos, V> changes) {
        if (changes.isEmpty()) return this;
        Set<SectionPos> touched = new HashSet<>();
        changes.keySet().forEach(pos -> touched.add(SectionPos.of(pos)));
        Writer<V> out = new Writer<>();
        visit((start, step, length, value) -> {
            for (int i = 0; i < length;) {
                BlockPos pos = start.relative(step, i);
                int n = sectionSpan(pos, step, length - i);
                if (length > 1 && !touched.contains(SectionPos.of(pos))) out.add(pos, step, n, value, true);
                else for (int j = 0; j < n; j++) {
                    BlockPos key = pos.relative(step, j);
                    out.add(key, step, 1, changes.getOrDefault(key, value), length > 1 && !changes.containsKey(key));
                }
                i += n;
            }
        });
        changes.forEach((pos, value) -> { if (!containsKey(pos)) out.add(pos, Direction.EAST, 1, value, false); });
        return out.finishPacked();
    }
    private static int sectionSpan(BlockPos pos, Direction step, int remaining) {
        int coord = step.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) & 15;
        return Math.min(remaining, step.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - coord : coord + 1);
    }
    // X is the low nibble so a script's horizontal range can be set in one BitSet operation.
    private static int local(BlockPos pos) { return (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | pos.getX() & 15; }
    private static <V> V lookup(Map<SectionPos, Map<V, BitSet>> masks, BlockPos pos) {
        if (!masks.isEmpty()) {
            var groups = masks.get(SectionPos.of(pos));
            if (groups != null) for (var group : groups.entrySet()) if (group.getValue().get(local(pos))) return group.getKey();
        }
        return null;
    }
}

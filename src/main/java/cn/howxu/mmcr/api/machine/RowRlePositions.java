package cn.howxu.mmcr.api.machine;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Row-major structure geometry. Both indexes contain only occupied rows/runs, never the bounding volume. */
final class RowRlePositions<V> extends AbstractMap<BlockPos, V> implements PositionTable<V> {
    private final long[] rows;
    private final int[] firstRun;
    private final int[] startX;
    private final int[] firstCell;
    private final int[] valueIndex;
    private final List<V> palette;
    private final BitSet packedAir;

    private RowRlePositions(Builder<V> builder) {
        rows = builder.rows.toLongArray();
        firstRun = builder.firstRun.toIntArray();
        startX = builder.startX.toIntArray();
        firstCell = builder.firstCell.toIntArray();
        valueIndex = builder.valueIndex.toIntArray();
        palette = List.copyOf(builder.palette);
        packedAir = builder.packedAir;
    }

    static <V> PositionTable<V> from(PackedAirPositions<V> source) {
        if (source.isEmpty()) return source;
        var builder = new Builder<V>();
        source.visit(builder::add);
        if (!builder.ordered || builder.cells != source.size()) return source;
        builder.firstRun.add(builder.startX.size());
        builder.firstCell.add(builder.cells);
        return new RowRlePositions<>(builder);
    }

    // Signed long order must match traversal by signed Y, then signed Z.
    private static long rowKey(BlockPos pos) {
        return (long) pos.getY() << 32 | Integer.toUnsignedLong(pos.getZ() ^ Integer.MIN_VALUE);
    }

    private static final class Builder<V> {
        private final LongArrayList rows = new LongArrayList();
        private final IntArrayList firstRun = new IntArrayList();
        private final IntArrayList startX = new IntArrayList();
        private final IntArrayList firstCell = new IntArrayList();
        private final IntArrayList valueIndex = new IntArrayList();
        private final List<V> palette = new ArrayList<>();
        private final Map<V, Integer> predicates = new IdentityHashMap<>();
        private final Map<V, Integer> glyphs = new HashMap<>();
        private final BitSet packedAir = new BitSet();
        private boolean ordered = true;
        private int cells;
        private long nextX;

        void add(BlockPos start, Direction step, int length, V value, boolean air) {
            if (!ordered) return;
            long row = rowKey(start);
            int x = start.getX();
            int run = startX.size();
            boolean sameRow = !rows.isEmpty() && rows.getLong(rows.size() - 1) == row;
            // Negative/non-X runs have a different encounter order. Keep the original map, don't reverse them.
            if (value == null || (length > 1 && step != Direction.EAST)
                    || (long) x + length - 1 > Integer.MAX_VALUE
                    || (!rows.isEmpty() && row < rows.getLong(rows.size() - 1)) || (sameRow && x < nextX)) {
                ordered = false;
                return;
            }
            Map<V, Integer> index = value instanceof Character ? glyphs : predicates;
            Integer slot = index.get(value);
            if (slot == null) {
                slot = palette.size();
                index.put(value, slot);
                palette.add(value);
            }
            if (!sameRow) {
                rows.add(row);
                firstRun.add(run);
            }
            if (!sameRow || x != nextX || valueIndex.getInt(run - 1) != slot || packedAir.get(run - 1) != air) {
                startX.add(x);
                firstCell.add(cells);
                valueIndex.add(slot.intValue());
                if (air) packedAir.set(run);
            }
            cells += length;
            nextX = (long) x + length;
        }
    }

    @Override public int size() { return firstCell[firstCell.length - 1]; }

    /** Index of the greatest element <= key in a strictly increasing slice, or from - 1. */
    private static int floor(int[] array, int from, int until, int key) {
        int found = Arrays.binarySearch(array, from, until, key);
        return found >= 0 ? found : -found - 2;
    }

    private int runAt(BlockPos pos) {
        int row = Arrays.binarySearch(rows, rowKey(pos));
        if (row < 0) return -1;
        int run = floor(startX, firstRun[row], firstRun[row + 1], pos.getX());
        return run >= firstRun[row] && (long) pos.getX() - startX[run] < length(run) ? run : -1;
    }

    private int length(int run) { return firstCell[run + 1] - firstCell[run]; }
    private V value(int run) { return palette.get(valueIndex[run]); }
    private static int y(long row) { return (int) (row >> 32); }
    private static int z(long row) { return (int) row ^ Integer.MIN_VALUE; }
    private int rowOf(int run) { return floor(firstRun, 0, rows.length, run); }
    private int runOf(int cell) { return floor(firstCell, 0, startX.length, cell); }

    @Override public V get(Object key) {
        int run = key instanceof BlockPos pos ? runAt(pos) : -1;
        return run < 0 ? null : value(run);
    }
    @Override public boolean containsKey(Object key) { return get(key) != null; }
    @Override public boolean containsValue(Object value) { return palette.contains(value); }
    @Override public boolean anyValueMatches(Predicate<V> test) { return palette.stream().anyMatch(test); }

    @Override public void visit(Visitor<V> visitor) {
        for (int row = 0; row < rows.length; row++) {
            int y = y(rows[row]), z = z(rows[row]);
            for (int run = firstRun[row]; run < firstRun[row + 1]; run++) {
                visitor.accept(new BlockPos(startX[run], y, z), Direction.EAST, length(run), value(run), packedAir.get(run));
            }
        }
    }

    @Override public void visitPoints(BiConsumer<BlockPos, V> consumer) {
        visitPointsAndExceptions(Set.of(), consumer);
    }

    @Override public void visitPointsAndExceptions(Set<BlockPos> exceptions, BiConsumer<BlockPos, V> consumer) {
        // Look up the exceptional air cells once, rather than expanding every long air run to find them.
        var extra = new IntArrayList(exceptions.size());
        for (BlockPos pos : exceptions) {
            int run = runAt(pos);
            if (run >= 0 && packedAir.get(run)) extra.add(firstCell[run] + (pos.getX() - startX[run]));
        }
        extra.sort(null);
        int next = 0;
        for (int row = 0; row < rows.length; row++) {
            int y = y(rows[row]), z = z(rows[row]);
            for (int run = firstRun[row]; run < firstRun[row + 1]; run++) {
                V value = value(run);
                if (packedAir.get(run)) {
                    while (next < extra.size() && extra.getInt(next) < firstCell[run + 1]) {
                        int offset = extra.getInt(next++) - firstCell[run];
                        consumer.accept(new BlockPos(startX[run] + offset, y, z), value);
                    }
                } else {
                    for (int i = 0; i < length(run); i++) consumer.accept(new BlockPos(startX[run] + i, y, z), value);
                }
            }
        }
    }

    @Override public void visitBounds(Consumer<BlockPos> consumer) {
        for (int row = 0; row < rows.length; row++) {
            int y = y(rows[row]), z = z(rows[row]), last = firstRun[row + 1] - 1;
            consumer.accept(new BlockPos(startX[firstRun[row]], y, z));
            consumer.accept(new BlockPos(startX[last] + (length(last) - 1), y, z));
        }
    }

    @Override public void forValues(Predicate<V> select, BiConsumer<BlockPos, V> consumer) {
        visit((pos, step, length, value, air) -> {
            if (select.test(value)) for (int i = 0; i < length; i++) consumer.accept(pos.east(i), value);
        });
    }

    private BlockPos position(int run, int index) {
        long row = rows[rowOf(run)];
        return new BlockPos(startX[run] + (index - firstCell[run]), y(row), z(row));
    }

    private <T> List<T> view(IntFunction<T> getter) {
        return new AbstractList<>() {
            @Override public int size() { return RowRlePositions.this.size(); }
            @Override public T get(int index) { return getter.apply(Objects.checkIndex(index, size())); }
        };
    }

    @Override public List<BlockPos> positions() { return view(i -> position(runOf(i), i)); }
    @Override public List<V> orderedValues() { return view(i -> value(runOf(i))); }
    @Override public List<Entry<BlockPos, V>> entries() {
        return view(i -> {
            int run = runOf(i);
            return Map.entry(position(run, i), value(run));
        });
    }
    @Override public Set<Entry<BlockPos, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return RowRlePositions.this.size(); }
            @Override public Iterator<Entry<BlockPos, V>> iterator() { return entries().iterator(); }
        };
    }

    @Override public PositionCursor<V> cursor() {
        return new PositionCursor<>() {
            private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
            private int run = -1;
            private int row;

            @Override public void seek(int index) {
                if (run >= 0 && index == firstCell[run + 1]) {
                    if (++run == firstRun[row + 1]) row++;
                } else if (run < 0 || index < firstCell[run] || index >= firstCell[run + 1]) {
                    run = runOf(index);
                    row = rowOf(run);
                }
                scratch.set(startX[run] + (index - firstCell[run]), y(rows[row]), z(rows[row]));
            }
            @Override public int runEnd(int index) { return firstCell[run + 1]; }
            @Override public Direction step() { return Direction.EAST; }
            @Override public BlockPos position() { return scratch; }
            @Override public V value() { return RowRlePositions.this.value(run); }
        };
    }
}

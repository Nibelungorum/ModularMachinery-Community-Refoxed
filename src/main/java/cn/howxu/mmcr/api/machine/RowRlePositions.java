package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Row-run-length encoded position table.
 *
 * <p>The measured cost of a {@link PackedAirPositions} entry at lotus scale is ~92 bytes per predicate
 * (node + slot + {@link BlockPos} + the duplicate key/value arrays), and a structure set with 2.7M solid
 * cells pays that twice (predicates and symbols). This class stores the same information as one run per
 * contiguous equal value inside one row: a {@code short} start offset, a {@code short} length, a palette
 * index and a per-run cell-index prefix — a dozen bytes per run instead of ~92 per cell.
 *
 * <p>Ordering matches the structure pattern exactly: runs are emitted row by row with rows ordered
 * z-then-y and cells ordered by x, which is the encounter order {@code BlockArray.fromRows} produces.
 * {@link #runFirstIndex} maps the logical entry index used by {@code ScanPlan} and the structure scan
 * cursor back onto a run in O(log runs), so ordering and random access both survive compaction.
 *
 * <p>Built by {@link #from(PositionTable)}: the existing packer stays the single writer so air-run
 * packing, duplicate handling and the update path keep their current behaviour; this is a compaction of
 * its output, not a second implementation of it. Anything the model cannot express (oversized extents,
 * vertical runs, out-of-order rows) keeps the packed representation unchanged.
 *
 * @author howxu {@literal <dev@howxu.cn>}
 */
final class RowRlePositions<V> extends AbstractMap<BlockPos, V> implements PositionTable<V> {

    /** Extents are bounded so a row-start offset and a run length both fit in a {@code short}. */
    private static final int MAX_EXTENT = 4096;

    /** Test hook: why the last compaction pass kept the packed representation. */
    static volatile String lastReject;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int width;
    private final int height;
    private final int depth;
    /** Prefix index: runs of row {@code r} are {@code [rowFirstRun[r], rowFirstRun[r + 1])}. */
    private final int[] rowFirstRun;
    private final short[] runStart;
    private final short[] runLength;
    private final int[] runValue;
    /** Logical cell index of each run's first cell; the last run ends at {@code size}. */
    private final int[] runFirstIndex;
    private final Object[] palette;
    private final int size;
    /**
     * Marks runs the packer produced for packed values (explicit air). Those runs are not "points" for
     * candidate collection, while every other cell is reported individually even inside a coalesced run.
     */
    private final java.util.BitSet packedRuns;

    private RowRlePositions(int minX, int minY, int minZ, int width, int height, int depth,
                            int[] rowFirstRun, short[] runStart, short[] runLength, int[] runValue,
                            int[] runFirstIndex, Object[] palette, int size, java.util.BitSet packedRuns) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.rowFirstRun = rowFirstRun;
        this.runStart = runStart;
        this.runLength = runLength;
        this.runValue = runValue;
        this.runFirstIndex = runFirstIndex;
        this.palette = palette;
        this.size = size;
        this.packedRuns = packedRuns;
    }

    /**
     * Compacts a table into row runs, or returns it unchanged when the data does not fit the
     * row-indexed model.
     *
     * <p>Runs the packer already produced (length ≥ 2, explicit air) stay distinguishable from cells
     * that were stored as points, so {@link #visitPoints} keeps exactly the meaning it had before.
     */
    static <V> PositionTable<V> from(PositionTable<V> source) {
        lastReject = null;
        if (source.isEmpty()) return source;
        // Already compact, or already a cheap rotation view; nothing to gain by copying either.
        if (source instanceof RowRlePositions<?> || source instanceof RotatedPositions<?>) return source;

        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        source.visitBounds(pos -> {
            bounds[0] = Math.min(bounds[0], pos.getX());
            bounds[3] = Math.max(bounds[3], pos.getX());
            bounds[1] = Math.min(bounds[1], pos.getY());
            bounds[4] = Math.max(bounds[4], pos.getY());
            bounds[2] = Math.min(bounds[2], pos.getZ());
            bounds[5] = Math.max(bounds[5], pos.getZ());
        });
        int minX = bounds[0];
        int minY = bounds[1];
        int minZ = bounds[2];
        int width = bounds[3] - minX + 1;
        int height = bounds[4] - minY + 1;
        int depth = bounds[5] - minZ + 1;
        if (width <= 0 || height <= 0 || depth <= 0) return source;
        if (width > MAX_EXTENT || height > MAX_EXTENT || depth > MAX_EXTENT) return source;
        long rows = (long) height * depth;
        if (rows >= Integer.MAX_VALUE) return source;

        Accumulator<V> accumulator = new Accumulator<>(minX, minY, minZ, width, height, depth, (int) rows);
        source.visit(accumulator);
        return accumulator.finish(source);
    }

    /** Mutable accumulator for one compaction pass; kept out of the lambda so the walk can mutate state. */
    private static final class Accumulator<V> implements Visitor<V> {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int width;
        private final int height;
        private final int depth;
        private final int rowCount;
        /**
         * Predicates are deduplicated by identity: the matcher memoises per instance, so two equal but
         * distinct predicates must stay distinct. Characters are deduplicated by value, because boxing
         * creates a new instance per call and a per-value palette is what keeps the table compact.
         */
        private final Map<V, Integer> paletteIndex = new java.util.IdentityHashMap<>();
        private final Map<Character, Integer> charPaletteIndex = new HashMap<>();
        private final List<Object> palette = new ArrayList<>();
        private short[] runStart = new short[256];
        private short[] runLength = new short[256];
        private int[] runRow = new int[256];
        private int[] runValue = new int[256];
        private boolean[] runPacked = new boolean[256];
        private int runCount;
        private int size;
        private int previousRow = -1;
        private int previousEnd = -1;
        private boolean bail;

        private Accumulator(int minX, int minY, int minZ, int width, int height, int depth, int rowCount) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.rowCount = rowCount;
        }

        @Override
        public void accept(BlockPos start, Direction step, int length, V value) {
            if (bail) return;
            if (step.getStepY() != 0 || step.getStepZ() != 0) {
                lastReject = "non-horizontal run";
                bail = true;
                return;
            }
            int row = (start.getY() - minY) * depth + (start.getZ() - minZ);
            if (row < previousRow || row >= rowCount) {
                // Rows must arrive in encounter order; anything else keeps the packed representation.
                lastReject = "row " + row + " after " + previousRow + " of " + rowCount;
                bail = true;
                return;
            }
            int offset = start.getX() - minX;
            if (offset < 0 || offset + length > width) {
                lastReject = "offset " + offset + " length " + length + " of width " + width;
                bail = true;
                return;
            }
            boolean sameRow = row == previousRow;
            int previousRunEnd = previousEnd;
            if (sameRow && offset < previousRunEnd) {
                // Within a row the pattern walks x ascending with no overlap; the binary search relies
                // on it. Adjacent runs start exactly at the previous end, so only true overlap bails.
                lastReject = "overlap " + offset + " < " + previousRunEnd;
                bail = true;
                return;
            }
            int paletteSlot;
            if (value instanceof Character character) {
                paletteSlot = charPaletteIndex.computeIfAbsent(character, candidate -> {
                    palette.add(candidate);
                    return palette.size() - 1;
                });
            } else {
                Integer existing = paletteIndex.get(value);
                if (existing == null) {
                    existing = palette.size();
                    paletteIndex.put(value, existing);
                    palette.add(value);
                }
                paletteSlot = existing;
            }
            if (runCount == runStart.length) grow();
            // The packer emits explicit-air runs with length >= 2 and everything else as single-cell
            // points, so a run's kind is recoverable here and must be preserved through coalescing.
            boolean packed = length > 1;
            // Coalesce a run that continues the previous one in the same row with the same value of the
            // same kind. The glyph writer emits one entry per cell, so this turns a solid row into one run.
            if (runCount > 0 && sameRow && runValue[runCount - 1] == paletteSlot
                    && runPacked[runCount - 1] == packed && offset == previousRunEnd) {
                runLength[runCount - 1] = (short) (runLength[runCount - 1] + length);
                previousRow = row;
                previousEnd = offset + length;
                size += length;
                return;
            }
            runStart[runCount] = (short) offset;
            runLength[runCount] = (short) length;
            runRow[runCount] = row;
            runValue[runCount] = paletteSlot;
            runPacked[runCount] = packed;
            runCount++;
            size += length;
            previousRow = row;
            previousEnd = offset + length;
        }

        private void grow() {
            int capacity = runStart.length * 2;
            short[] starts = new short[capacity];
            short[] lengths = new short[capacity];
            int[] rows = new int[capacity];
            int[] values = new int[capacity];
            boolean[] packedFlags = new boolean[capacity];
            System.arraycopy(runStart, 0, starts, 0, runCount);
            System.arraycopy(runLength, 0, lengths, 0, runCount);
            System.arraycopy(runRow, 0, rows, 0, runCount);
            System.arraycopy(runValue, 0, values, 0, runCount);
            System.arraycopy(runPacked, 0, packedFlags, 0, runCount);
            runStart = starts;
            runLength = lengths;
            runRow = rows;
            runValue = values;
            runPacked = packedFlags;
        }

        private PositionTable<V> finish(PositionTable<V> source) {
            if (bail || runCount == 0 || size != source.size()) return source;

            short[] starts = new short[runCount];
            short[] lengths = new short[runCount];
            int[] values = new int[runCount];
            int[] firstIndex = new int[runCount];
            System.arraycopy(runStart, 0, starts, 0, runCount);
            System.arraycopy(runLength, 0, lengths, 0, runCount);
            System.arraycopy(runValue, 0, values, 0, runCount);
            int cell = 0;
            for (int i = 0; i < runCount; i++) {
                firstIndex[i] = cell;
                cell += lengths[i];
            }

            int[] rowFirstRun = new int[rowCount + 1];
            int run = 0;
            for (int r = 0; r < rowCount; r++) {
                rowFirstRun[r] = run;
                while (run < runCount && runRow[run] == r) run++;
            }
            rowFirstRun[rowCount] = runCount;
            if (run != runCount) return source;

            java.util.BitSet packed = new java.util.BitSet(runCount);
            for (int i = 0; i < runCount; i++) {
                if (runPacked[i]) packed.set(i);
            }

            return new RowRlePositions<>(minX, minY, minZ, width, height, depth, rowFirstRun,
                    starts, lengths, values, firstIndex, palette.toArray(), size, packed);
        }
    }

    private V valueOf(int runIndex) {
        return unchecked(palette[runValue[runIndex]]);
    }

    @SuppressWarnings("unchecked")
    private V unchecked(Object value) {
        return (V) value;
    }

    /** Run covering the logical entry index. */
    private int runAt(int index) {
        int low = 0;
        int high = runValue.length - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (runFirstIndex[mid] <= index) low = mid + 1;
            else high = mid - 1;
        }
        return Math.max(0, high);
    }

    /** Row owning the given run; empty rows share their prefix value, so skip forward. */
    private int rowOfRun(int run) {
        int low = 0;
        int high = rowFirstRun.length - 2;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (rowFirstRun[mid] <= run) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        while (result < rowFirstRun.length - 2 && rowFirstRun[result + 1] <= run) result++;
        return result;
    }

    /** Run covering {@code (row, x)}, or {@code -1} when the row has no run covering that cell. */
    private int runFor(int row, int x) {
        int from = rowFirstRun[row];
        int to = rowFirstRun[row + 1] - 1;
        while (from <= to) {
            int mid = (from + to) >>> 1;
            int start = minX + runStart[mid];
            if (x < start) to = mid - 1;
            else if (x >= start + runLength[mid]) from = mid + 1;
            else return mid;
        }
        return -1;
    }

    private BlockPos positionAt(int index) {
        int run = runAt(index);
        int row = rowOfRun(run);
        return new BlockPos(minX + runStart[run] + (index - runFirstIndex[run]),
                minY + row / depth, minZ + row % depth);
    }

    private V valueAt(int index) {
        return valueOf(runAt(index));
    }

    @Override
    public V get(Object key) {
        if (!(key instanceof BlockPos pos)) return null;
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (x < minX || y < minY || z < minZ) return null;
        int dy = y - minY;
        int dz = z - minZ;
        if (x - minX >= width || dy >= height || dz >= depth) return null;
        int run = runFor(dy * depth + dz, x);
        return run < 0 ? null : valueOf(run);
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Object candidate : palette) {
            if (Objects.equals(candidate, value)) return true;
        }
        return false;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Set<Entry<BlockPos, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public Iterator<Entry<BlockPos, V>> iterator() {
                return new Iterator<>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index < size;
                    }

                    @Override
                    public Entry<BlockPos, V> next() {
                        int current = index++;
                        return Map.entry(positionAt(current), valueAt(current));
                    }
                };
            }
        };
    }

    @Override
    public void visit(Visitor<V> visitor) {
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int y = minY + row / depth;
            int z = minZ + row % depth;
            for (int run = rowFirstRun[row]; run < rowFirstRun[row + 1]; run++) {
                visitor.accept(new BlockPos(minX + runStart[run], y, z), Direction.EAST,
                        runLength[run], valueOf(run));
            }
        }
    }

    @Override
    public void visitPoints(BiConsumer<BlockPos, V> consumer) {
        // Packed semantics: a multi-cell run of packed values (explicit air) is not a point, but every
        // other cell is reported individually even though it may share a coalesced run.
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int y = minY + row / depth;
            int z = minZ + row % depth;
            for (int run = rowFirstRun[row]; run < rowFirstRun[row + 1]; run++) {
                V value = valueOf(run);
                int length = runLength[run];
                if (length != 1 && packedRuns.get(run)) continue;
                int startX = minX + runStart[run];
                for (int i = 0; i < length; i++) {
                    consumer.accept(new BlockPos(startX + i, y, z), value);
                }
            }
        }
    }

    @Override
    public void visitPointsAndExceptions(Set<BlockPos> exceptions, BiConsumer<BlockPos, V> consumer) {
        if (exceptions.isEmpty()) {
            visitPoints(consumer);
            return;
        }
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int y = minY + row / depth;
            int z = minZ + row % depth;
            for (int run = rowFirstRun[row]; run < rowFirstRun[row + 1]; run++) {
                V value = valueOf(run);
                int length = runLength[run];
                int startX = minX + runStart[run];
                // Only packed runs hide their non-exception cells, matching the packed representation.
                boolean hideNonExceptions = length != 1 && packedRuns.get(run);
                for (int i = 0; i < length; i++) {
                    BlockPos pos = new BlockPos(startX + i, y, z);
                    if (hideNonExceptions && !exceptions.contains(pos)) continue;
                    consumer.accept(pos, value);
                }
            }
        }
    }

    @Override
    public void visitBounds(Consumer<BlockPos> consumer) {
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int first = rowFirstRun[row];
            int last = rowFirstRun[row + 1] - 1;
            if (last < first) continue;
            int y = minY + row / depth;
            int z = minZ + row % depth;
            consumer.accept(new BlockPos(minX + runStart[first], y, z));
            consumer.accept(new BlockPos(minX + runStart[last] + runLength[last] - 1, y, z));
        }
    }

    @Override
    public void forValues(Predicate<V> select, BiConsumer<BlockPos, V> consumer) {
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int y = minY + row / depth;
            int z = minZ + row % depth;
            for (int run = rowFirstRun[row]; run < rowFirstRun[row + 1]; run++) {
                V value = valueOf(run);
                if (!select.test(value)) continue;
                int startX = minX + runStart[run];
                for (int i = 0; i < runLength[run]; i++) {
                    consumer.accept(new BlockPos(startX + i, y, z), value);
                }
            }
        }
    }

    @Override
    public List<BlockPos> positions() {
        return view(this::positionAt);
    }

    @Override
    public List<V> orderedValues() {
        return view(this::valueAt);
    }

    @Override
    public List<Entry<BlockPos, V>> entries() {
        return view(index -> Map.entry(positionAt(index), valueAt(index)));
    }

    private <T> List<T> view(IntFunction<T> get) {
        return new AbstractList<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public T get(int index) {
                Objects.checkIndex(index, size);
                return get.apply(index);
            }
        };
    }

    @Override
    public PositionCursor<V> cursor() {
        return new RleCursor();
    }

    @Override
    public boolean anyValueMatches(Predicate<V> test) {
        for (Object value : palette) {
            if (test.test(unchecked(value))) return true;
        }
        return false;
    }

    /** Visit hook that also reports whether the run came from the packer as a packed (air) run. */
    private interface FlaggedVisitor<V> {
        void accept(BlockPos start, Direction step, int length, V value, boolean packed);
    }

    private void visitFlagged(FlaggedVisitor<V> visitor) {
        for (int row = 0; row < rowFirstRun.length - 1; row++) {
            int y = minY + row / depth;
            int z = minZ + row % depth;
            for (int run = rowFirstRun[row]; run < rowFirstRun[row + 1]; run++) {
                visitor.accept(new BlockPos(minX + runStart[run], y, z), Direction.EAST,
                        runLength[run], valueOf(run), packedRuns.get(run));
            }
        }
    }

    @Override
    public PositionTable<V> union(PositionTable<V> extension,
                                  Function<BlockPos, IllegalArgumentException> conflict) {
        if (extension.isEmpty() || extension == this) return this;
        PackedAirPositions.Writer<V> writer = new PackedAirPositions.Writer<>();
        // Preserve each run's packed flag: a coalesced non-air run must not become a packed air run,
        // otherwise it disappears from visitPoints and candidate collection goes blind.
        visitFlagged((start, step, length, value, packed) -> writer.add(start, step, length, value, packed));
        extension.visit((start, step, length, value) -> {
            for (int i = 0; i < length; i++) {
                BlockPos pos = start.relative(step, i);
                V previous = get(pos);
                if (previous != null) {
                    if (!previous.equals(value)) throw conflict.apply(pos);
                } else {
                    writer.add(pos, Direction.EAST, 1, value, false);
                }
            }
        });
        return writer.finish();
    }

    @Override
    public PositionTable<V> rotate(Direction facing, Direction roll, UnaryOperator<V> transform) {
        PackedAirPositions.Writer<V> writer = new PackedAirPositions.Writer<>();
        visitFlagged((start, step, length, value, packed) -> {
            V next = transform.apply(value);
            if (facing == Direction.SOUTH) {
                writer.add(start, step, length, next, packed);
                return;
            }
            writer.add(BlockRotator.rotateSouthTo(start, facing, roll),
                    BlockRotator.rotateDirection(step, facing, roll), length, next, false);
        });
        return writer.finish();
    }

    /** Cursor over the run table; the scratch position is reused so scanning does not allocate. */
    private final class RleCursor implements PositionCursor<V> {

        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        private int run = -1;
        private int runEndIndex;
        private V value;

        @Override
        public void seek(int index) {
            run = runAt(index);
            int row = rowOfRun(run);
            value = valueOf(run);
            runEndIndex = runFirstIndex[run] + runLength[run];
            scratch.set(minX + runStart[run] + (index - runFirstIndex[run]),
                    minY + row / depth, minZ + row % depth);
        }

        @Override
        public int runEnd(int index) {
            return run < 0 ? index + 1 : runEndIndex;
        }

        @Override
        public Direction step() {
            return Direction.EAST;
        }

        @Override
        public BlockPos position() {
            return scratch;
        }

        @Override
        public V value() {
            return value;
        }
    }
}

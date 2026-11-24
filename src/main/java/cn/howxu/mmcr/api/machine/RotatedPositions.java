package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
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
 * Rotation-aware view over a south-facing {@link PositionTable}.
 *
 * <p>A non-south facing of a structure reads the shared south-facing table through this view instead
 * of materialising a rotated copy of every predicate (measured at roughly 92 bytes per entry before
 * this change). Lookups convert the query position into south space in integer arithmetic.
 *
 * <p>{@link #cursor()} is the allocation-free path used by the structure scan: it rotates the source
 * cursor's scratch position into its own mutable position. Everything else materialises positions on
 * demand and is used only by diagnostics, wire encoding and compile-time candidate collection.
 *
 * @author howxu {@literal <dev@howxu.cn>}
 */
final class RotatedPositions<V> extends AbstractMap<BlockPos, V> implements PositionTable<V> {

    private final PositionTable<V> source;
    private final Direction facing;
    private final Direction roll;

    RotatedPositions(PositionTable<V> source, Direction facing, Direction roll) {
        this.source = Objects.requireNonNull(source, "source");
        this.facing = Objects.requireNonNull(facing, "facing");
        this.roll = BlockRotator.normalizedRoll(facing, roll == null ? Direction.SOUTH : roll);
    }

    private BlockPos rotate(BlockPos pos) {
        return BlockRotator.rotateSouthTo(pos, facing, roll);
    }

    private BlockPos normalize(BlockPos pos) {
        return BlockRotator.normalizeFromFace(pos, facing, roll);
    }

    @Override
    public V get(Object key) {
        return key instanceof BlockPos pos ? source.get(normalize(pos)) : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof BlockPos pos && source.containsKey(normalize(pos));
    }

    @Override
    public int size() {
        return source.size();
    }

    @Override
    public boolean isEmpty() {
        return source.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return source.containsValue(value);
    }

    @Override
    public Set<Entry<BlockPos, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return source.size();
            }

            @Override
            public Iterator<Entry<BlockPos, V>> iterator() {
                Iterator<Entry<BlockPos, V>> delegate = source.entrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegate.hasNext();
                    }

                    @Override
                    public Entry<BlockPos, V> next() {
                        Entry<BlockPos, V> entry = delegate.next();
                        return Map.entry(rotate(entry.getKey()), entry.getValue());
                    }
                };
            }
        };
    }

    @Override
    public void visit(Visitor<V> visitor) {
        source.visit((start, step, length, value) -> visitor.accept(rotate(start),
                BlockRotator.rotateDirection(step, facing, roll), length, value));
    }

    @Override
    public void visitPoints(BiConsumer<BlockPos, V> consumer) {
        source.visitPoints((pos, value) -> consumer.accept(rotate(pos), value));
    }

    @Override
    public void visitPointsAndExceptions(Set<BlockPos> exceptions, BiConsumer<BlockPos, V> consumer) {
        if (exceptions.isEmpty()) {
            visitPoints(consumer);
            return;
        }
        Set<BlockPos> normalized = new HashSet<>(Math.max(4, exceptions.size() * 2));
        for (BlockPos pos : exceptions) {
            normalized.add(normalize(pos));
        }
        source.visitPointsAndExceptions(normalized, (pos, value) -> consumer.accept(rotate(pos), value));
    }

    @Override
    public void visitBounds(Consumer<BlockPos> consumer) {
        source.visitBounds(pos -> consumer.accept(rotate(pos)));
    }

    @Override
    public void forValues(Predicate<V> select, BiConsumer<BlockPos, V> consumer) {
        source.forValues(select, (pos, value) -> consumer.accept(rotate(pos), value));
    }

    @Override
    public List<BlockPos> positions() {
        List<BlockPos> sourcePositions = source.positions();
        return view(index -> rotate(sourcePositions.get(index)));
    }

    @Override
    public List<V> orderedValues() {
        return source.orderedValues();
    }

    @Override
    public List<Entry<BlockPos, V>> entries() {
        List<BlockPos> sourcePositions = source.positions();
        List<V> sourceValues = source.orderedValues();
        return view(index -> Map.entry(rotate(sourcePositions.get(index)), sourceValues.get(index)));
    }

    private <T> List<T> view(IntFunction<T> get) {
        return new AbstractList<>() {
            @Override
            public int size() {
                return source.size();
            }

            @Override
            public T get(int index) {
                Objects.checkIndex(index, source.size());
                return get.apply(index);
            }
        };
    }

    @Override
    public PositionCursor<V> cursor() {
        return new RotatedCursor(source.cursor());
    }

    @Override
    public boolean anyValueMatches(Predicate<V> test) {
        return source.anyValueMatches(test);
    }

    /**
     * Union is a south-facing operation: structure families merge their own templates, never a view.
     * Failing loudly beats silently merging an un-rotated table.
     */
    @Override
    public PositionTable<V> union(PositionTable<V> extension,
                                  Function<BlockPos, IllegalArgumentException> conflict) {
        throw new UnsupportedOperationException("union requires the south-facing pattern, not a rotated view");
    }

    @Override
    public PositionTable<V> rotate(Direction target, Direction targetRoll, UnaryOperator<V> transform) {
        PackedAirPositions.Writer<V> writer = new PackedAirPositions.Writer<>();
        visit((start, step, length, value) -> writer.add(start, step, length, transform.apply(value), false));
        return writer.finish();
    }

    /** Rotates the source cursor's scratch position into this view's orientation without allocating. */
    private final class RotatedCursor implements PositionCursor<V> {

        private final PositionCursor<V> delegate;
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

        private RotatedCursor(PositionCursor<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void seek(int index) {
            delegate.seek(index);
        }

        @Override
        public int runEnd(int index) {
            return delegate.runEnd(index);
        }

        @Override
        public Direction step() {
            return BlockRotator.rotateDirection(delegate.step(), facing, roll);
        }

        @Override
        public BlockPos position() {
            return BlockRotator.rotateSouthTo(delegate.position(), facing, roll, scratch);
        }

        @Override
        public V value() {
            return delegate.value();
        }
    }

    /** Materialises a south-facing copy; used only when a rotated table must become a source table. */
    static <V> List<BlockPos> materialize(PositionTable<V> table) {
        List<BlockPos> positions = new ArrayList<>(table.size());
        table.visit((start, step, length, value) -> {
            if (length == 1) {
                positions.add(start);
                return;
            }
            for (int i = 0; i < length; i++) {
                positions.add(start.relative(step, i));
            }
        });
        return positions;
    }
}

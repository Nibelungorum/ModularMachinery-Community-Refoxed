package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Immutable, encounter-ordered position → value table.
 *
 * <p>Two implementations exist: {@link PackedAirPositions}, the south-facing run-packed store, and
 * {@link RotatedPositions}, a thin rotation-aware view over another table. Structure walking goes
 * through this interface so a non-south facing costs one small object instead of a full copy of every
 * predicate and its coordinates.
 *
 * <p>The per-entry scan loop must use {@link #cursor()}: it is the only access path guaranteed not to
 * allocate. {@link #positions()}, {@link #orderedValues()} and {@link #entries()} are for diagnostics
 * and wire encoding, which materialise positions on demand.
 */
interface PositionTable<V> extends Map<BlockPos, V> {

    /** Emits straight runs of equal values; single points arrive as {@code length == 1}. */
    interface Visitor<V> {
        void accept(BlockPos start, Direction step, int length, V value);
    }

    void visit(Visitor<V> visitor);

    void visitPoints(BiConsumer<BlockPos, V> consumer);

    void visitPointsAndExceptions(Set<BlockPos> exceptions, BiConsumer<BlockPos, V> consumer);

    void visitBounds(Consumer<BlockPos> consumer);

    void forValues(Predicate<V> select, BiConsumer<BlockPos, V> consumer);

    List<BlockPos> positions();

    List<V> orderedValues();

    List<Entry<BlockPos, V>> entries();

    PositionCursor<V> cursor();

    /** True when any stored value satisfies {@code test}; walks runs before points. */
    boolean anyValueMatches(Predicate<V> test);

    PositionTable<V> union(PositionTable<V> extension, Function<BlockPos, IllegalArgumentException> conflict);

    PositionTable<V> rotate(Direction facing, Direction roll, UnaryOperator<V> transform);
}

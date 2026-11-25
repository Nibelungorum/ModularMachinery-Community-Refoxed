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

/** Immutable position map in structure scan order. */
interface PositionTable<V> extends Map<BlockPos, V> {
    interface Visitor<V> {
        /** Only packed air may be omitted from component candidates; a solid run is still all points. */
        void accept(BlockPos start, Direction step, int length, V value, boolean packedAir);
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
    boolean anyValueMatches(Predicate<V> test);

    default PositionTable<V> union(PositionTable<V> extension, Function<BlockPos, IllegalArgumentException> conflict) {
        if (extension.isEmpty() || extension == this) return this;
        if (isEmpty()) return extension;
        var writer = new PackedAirPositions.Writer<V>();
        visit(writer::add);
        extension.visit((start, step, length, value, packedAir) -> {
            for (int i = 0; i < length; i++) {
                BlockPos pos = start.relative(step, i);
                V previous = get(pos);
                if (previous != null) {
                    if (!previous.equals(value)) throw conflict.apply(pos);
                } else if (containsKey(pos)) {
                    if (value != null) writer.put(pos, value, packedAir);
                } else {
                    writer.add(pos, step, 1, value, packedAir);
                }
            }
        });
        return writer.finish();
    }

    default PositionTable<V> rotate(Direction facing, Direction roll, UnaryOperator<V> transform) {
        var writer = new PackedAirPositions.Writer<V>();
        visit((start, step, length, value, packedAir) -> writer.add(BlockRotator.rotateSouthTo(start, facing, roll),
                BlockRotator.rotateDirection(step, facing, roll), length, transform.apply(value), packedAir));
        return writer.finish();
    }
}

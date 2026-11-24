package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rotation-aware view over a south-facing symbol table.
 *
 * <p>A rotated {@link BlockArray} keeps the single south-facing glyph table and exposes this view, so
 * the three non-south facings cost one small object each instead of a full copy of every position
 * (measured at roughly 90 bytes per entry before this change). Coordinates are converted in integer
 * space on lookup; only iteration allocates {@link BlockPos} instances, and iteration happens on the
 * wire-encode and build paths rather than in the per-tick match loop.
 *
 * <p>{@link #normalized()} exposes the south-facing table for the internal packing paths
 * ({@link BlockArray#glyphs()} union), which must keep seeing untransformed coordinates.
 *
 * @author howxu {@literal <dev@howxu.cn>}
 */
final class GlyphView extends AbstractMap<BlockPos, Character> {

    private final PositionTable<Character> source;
    private final Direction facing;
    private final Direction roll;

    GlyphView(PositionTable<Character> source, Direction facing, Direction roll) {
        this.source = Objects.requireNonNull(source, "source");
        this.facing = Objects.requireNonNull(facing, "facing");
        this.roll = BlockRotator.normalizedRoll(facing, roll == null ? Direction.SOUTH : roll);
    }

    /** The south-facing table this view presents from another orientation. */
    PositionTable<Character> normalized() {
        return source;
    }

    @Override
    public Character get(Object key) {
        return key instanceof BlockPos pos
                ? source.get(BlockRotator.normalizeFromFace(pos, facing, roll))
                : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof BlockPos pos
                && source.containsKey(BlockRotator.normalizeFromFace(pos, facing, roll));
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
    public Set<Entry<BlockPos, Character>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return source.size();
            }

            @Override
            public Iterator<Entry<BlockPos, Character>> iterator() {
                Iterator<Entry<BlockPos, Character>> delegate = source.entrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegate.hasNext();
                    }

                    @Override
                    public Entry<BlockPos, Character> next() {
                        Entry<BlockPos, Character> entry = delegate.next();
                        return Map.entry(BlockRotator.rotateSouthTo(entry.getKey(), facing, roll), entry.getValue());
                    }
                };
            }
        };
    }
}

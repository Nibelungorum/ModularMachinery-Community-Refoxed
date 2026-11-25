package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Allocation-free sequential access to a {@link PositionTable}.
 *
 * <p>One cursor instance owns one reusable scratch position, so implementations that decode runs or
 * rotate coordinates never allocate inside the per-entry scan loop, which walks up to hundreds of
 * thousands of entries per structure check.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
interface PositionCursor<V> {

    /** Positions the cursor at the logical entry index. */
    void seek(int index);

    /** Index (exclusive) at which the run containing {@code index} ends. */
    int runEnd(int index);

    /** Step of the run the cursor currently sits in. */
    Direction step();

    /** Current position; valid until the next {@link #seek(int)}. */
    BlockPos position();

    /** Value at the current position. */
    V value();
}

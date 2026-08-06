package cn.howxu.mmcr.api.machine;

import net.minecraft.core.Direction;

/**
 * Runtime match state for a dynamic-length structure segment.
 *
 * @author howxu <dev@howxu.cn>
 */
public record DynamicPatternMatch(String name, int size, Direction matchFacing) {

    public DynamicPatternMatch {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        if (size < 0) throw new IllegalArgumentException("size negative");
        if (matchFacing == null) throw new IllegalArgumentException("matchFacing null");
    }
}

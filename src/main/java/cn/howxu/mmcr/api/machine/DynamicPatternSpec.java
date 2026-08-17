package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Static definition for a dynamic-length structure segment.
 *
 * @author howxu <dev@howxu.cn>
 */
public record DynamicPatternSpec(
        String name,
        BlockArray startPattern,
        @Nullable BlockArray endPattern,
        int minSize,
        int maxSize,
        BlockPos offsetStart,
        BlockPos structureSizeOffset,
        Set<Direction> allowedFaces
) {

    public DynamicPatternSpec {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name blank");
        if (startPattern == null) throw new IllegalArgumentException("startPattern null");
        if (minSize < 0) throw new IllegalArgumentException("minSize negative");
        if (maxSize < minSize) throw new IllegalArgumentException("maxSize smaller than minSize");
        if (offsetStart == null) throw new IllegalArgumentException("offsetStart null");
        if (structureSizeOffset == null) throw new IllegalArgumentException("structureSizeOffset null");
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        if (allowedFaces != null) faces.addAll(allowedFaces);
        if (faces.isEmpty()) faces.add(Direction.SOUTH);
        allowedFaces = Set.copyOf(faces);
    }
}

package cn.howxu.mmcr.api.machine;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.ToIntFunction;

public record BlockArray(Map<BlockPos, BlockPredicate> pattern) {

    public @Nullable BlockPredicate get(BlockPos pos) {
        return pattern.get(pos);
    }

    public boolean isEmpty() {
        return pattern.isEmpty();
    }

    public int width() {
        return extent(BlockPos::getX);
    }

    public int height() {
        return extent(BlockPos::getY);
    }

    public int length() {
        return extent(BlockPos::getZ);
    }

    private int extent(ToIntFunction<BlockPos> axis) {
        if (pattern.isEmpty()) return 0;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (BlockPos pos : pattern.keySet()) {
            int value = axis.applyAsInt(pos);
            if (value < min) min = value;
            if (value > max) max = value;
        }
        return max - min + 1;
    }
}

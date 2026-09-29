package cn.howxu.mmcr.internal.autoio;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Result of one direct automatic I/O attempt.
 *
 * @author howxu <dev@howxu.cn>
 */
public record AutoIoResult(boolean successful, long amount, @Nullable ExecutionStatus failure) {
    public AutoIoResult {
        if (amount < 0L || successful != (amount > 0L)) throw new IllegalArgumentException("invalid transfer result");
    }

    public static AutoIoResult moved(long amount) { return new AutoIoResult(amount > 0L, amount, null); }

    public static AutoIoResult blocked(ExecutionStatus failure) { return new AutoIoResult(false, 0L, failure); }
}

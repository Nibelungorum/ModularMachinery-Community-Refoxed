package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Result of one capability transfer attempt.
 *
 * @param successful whether a positive amount was transferred
 * @param amount the transferred amount, preserved as a long
 * @param failure the optional reason when the attempt could not transfer
 * @author howxu <dev@howxu.cn>
 */
public record TransferResult(boolean successful, long amount, @Nullable ExecutionStatus failure) {
    public TransferResult {
        if (amount < 0L) throw new IllegalArgumentException("amount must not be negative");
        if (successful != (amount > 0L)) throw new IllegalArgumentException("successful must match amount");
    }

    public static TransferResult moved(long amount) {
        return new TransferResult(amount > 0L, amount, null);
    }

    public static TransferResult blocked(ExecutionStatus failure) {
        return new TransferResult(false, 0L, failure);
    }
}

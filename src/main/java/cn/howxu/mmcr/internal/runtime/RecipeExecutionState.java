package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import java.util.Objects;

/**
 * Worker-owned confirmed facts. Never retains a live runtime or resource binding.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeExecutionState {
    private final ExecutionIdentity identity;
    private long installedRevision = -1L;
    private long consumedSequence = -1L;
    private int confirmedTick;
    private long confirmedPaidAttempts;
    private boolean terminal;
    private ExecutionStatus failure;

    public RecipeExecutionState(ExecutionIdentity identity) {
        this.identity = Objects.requireNonNull(identity);
    }

    public boolean accept(Receipt receipt) {
        if (!identity.equals(receipt.identity()) || receipt.installedRevision() < installedRevision
                || receipt.sequence() <= consumedSequence || terminal && !receipt.terminal()) return false;
        installedRevision = receipt.installedRevision();
        consumedSequence = receipt.sequence();
        confirmedTick = receipt.tick();
        confirmedPaidAttempts = receipt.paidAttempts();
        terminal = receipt.terminal();
        failure = receipt.failure();
        return true;
    }

    public long consumedSequence() { return consumedSequence; }
    public int confirmedTick() { return confirmedTick; }
    public long confirmedPaidAttempts() { return confirmedPaidAttempts; }
    public boolean terminal() { return terminal; }
    public ExecutionStatus failure() { return failure; }

    /**
     * An immutable main-thread fact, including failures and actual backward progress.
     * @author howxu <dev@howxu.cn>
     */
    public record Receipt(ExecutionIdentity identity, long installedRevision, long sequence,
                          int tick, long paidAttempts, boolean terminal, ExecutionStatus failure) {
        public Receipt {
            Objects.requireNonNull(identity);
            if (installedRevision < 0L || sequence < 0L || tick < 0 || paidAttempts < 0L) {
                throw new IllegalArgumentException("Invalid execution receipt");
            }
        }
    }

    /**
     * Only the matching terminal fact may release its original execution slot.
     * @author howxu <dev@howxu.cn>
     */
    public record Acknowledgement(ExecutionIdentity identity, long installedRevision, long sequence) { }
}

package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import java.util.Objects;

/**
 * Owns one admitted pattern start until it is committed or released.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartReservation implements AutoCloseable {
    public enum Status {
        UNAVAILABLE,
        RESERVED,
        /** Native ownership accepted; payment may be partial/uncertain after a callback failure. */
        COMMITTED
    }

    private final MachineRecipe recipe;
    private final String laneId;
    private final CraftingRuntime runtime;
    private final CraftingRuntime.PreparedStart preparedStart;
    private final Runnable release;
    private RecipeCommitLedger payment;
    private Runnable afterCommit = () -> { };
    private boolean rolledBack;
    private boolean committing;
    private Status status;

    private PatternStartReservation(Status status, MachineRecipe recipe, String laneId, CraftingRuntime runtime,
                                    CraftingRuntime.PreparedStart preparedStart, Runnable release) {
        this.status = status;
        this.recipe = recipe;
        this.laneId = laneId == null ? "" : laneId;
        this.runtime = runtime;
        this.preparedStart = preparedStart;
        this.release = release;
    }

    public static PatternStartReservation unavailable() {
        return new PatternStartReservation(Status.UNAVAILABLE, null, "", null, null, () -> { });
    }

    public static PatternStartReservation reserved(MachineRecipe recipe, String laneId, CraftingRuntime runtime,
                                                   CraftingRuntime.PreparedStart preparedStart, Runnable release) {
        return new PatternStartReservation(Status.RESERVED, Objects.requireNonNull(recipe, "recipe"), laneId,
                Objects.requireNonNull(runtime, "runtime"), Objects.requireNonNull(preparedStart, "preparedStart"),
                Objects.requireNonNull(release, "release"));
    }

    public Status status() {
        return status;
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public String laneId() {
        return laneId;
    }

    long preparedParallelism() {
        return preparedStart == null ? 0L : preparedStart.plan().parallelism();
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    public boolean commit() { return commit(() -> { }); }

    /** Returns true once resources are paid, even if a root notification cancels activation. */
    /** Accepts holder ownership after any native payment; uncertain batches cannot be pushed again. */
    public boolean commit(Runnable ownershipWrites) {
        Objects.requireNonNull(ownershipWrites, "ownershipWrites");
        if (!beginCommitAttempt()) return false;
        try {
            boolean complete = commitPlan();
            if (!complete && !paid()) { rollback(); return false; }
            try { ownershipWrites.run(); }
            catch (RuntimeException exception) { pauseAfterNotificationFailure(); }
            activate();
            return true;
        } finally { endCommitAttempt(); }
    }

    boolean beginCommitAttempt() {
        if (committing || status != Status.RESERVED || rolledBack) return false;
        committing = true;
        return true;
    }

    void endCommitAttempt() {
        committing = false;
    }

    boolean commitPlan() {
        if (status != Status.RESERVED || rolledBack) return false;
        payment = runtime.ledger();
        try {
            return runtime.commitPatternPlan(preparedStart);
        } catch (RuntimeException exception) {
            if (!paid()) return false;
            pauseAfterNotificationFailure();
            return false;
        }
    }

    boolean preflight() {
        return status == Status.RESERVED && !rolledBack && runtime.preparedStartCurrent(preparedStart);
    }

    boolean paid() {
        return payment != null && (payment.state().paidAttempts() > 0L
                || payment.state().phase() == RecipeCommitLedger.Phase.UNCERTAIN
                || payment.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED);
    }

    void pauseAfterNotificationFailure() {
        if (paid()) payment.pauseAfterCommitFailure();
    }

    void activate() {
        // COMMITTED describes payment, including a paid start cancelled by a root notification.
        status = Status.COMMITTED;
        rolledBack = false;
        try {
            boolean activated;
            try {
                if (payment.state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED) {
                    runtime.releasePatternStart(preparedStart);
                    activated = false;
                } else activated = runtime.activatePatternStart(preparedStart);
            } finally {
                release.run();
            }
            if (activated && payment.state().phase() != RecipeCommitLedger.Phase.ERROR_PAUSED) afterCommit.run();
        } catch (RuntimeException exception) {
            pauseAfterNotificationFailure();
            MMCR.LOG.warn("Pattern start paid before activation callback failure: lane={}", laneId, exception);
        }
    }

    public PatternStartReservation afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (status != Status.RESERVED || rolledBack) throw new IllegalStateException("Reservation is no longer pending");
        Runnable previous = afterCommit;
        afterCommit = () -> {
            previous.run();
            action.run();
        };
        return this;
    }

    public void rollback() {
        if (status != Status.RESERVED || rolledBack) return;
        if (paid()) return;
        rolledBack = true;
        release.run();
    }

    @Override
    public void close() {
        rollback();
    }
}

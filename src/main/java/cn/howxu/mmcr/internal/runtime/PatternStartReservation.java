package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Objects;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Owns one admitted pattern start until it is committed or released.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartReservation implements AutoCloseable {
    public enum RecipeSelection { CATALOG, LAST_RECIPE }
    public enum Status {
        UNAVAILABLE,
        RESERVED,
        /** Resources are paid; a root notification may have cancelled activation. */
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
    private RecipeSelection recipeSelection = RecipeSelection.CATALOG;
    private BooleanSupplier admissionCurrent = () -> true;

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

    public RecipeSelection recipeSelection() { return recipeSelection; }

    public PatternStartReservation withRecipeSelection(RecipeSelection selection) {
        if (status != Status.RESERVED || rolledBack) throw new IllegalStateException("Reservation is no longer pending");
        recipeSelection = Objects.requireNonNull(selection, "selection");
        return this;
    }

    /** Keeps thread/core admission in the same root-commit fence as runtime identity and routing. */
    public PatternStartReservation guard(BooleanSupplier current) {
        if (status != Status.RESERVED || rolledBack) throw new IllegalStateException("Reservation is no longer pending");
        admissionCurrent = Objects.requireNonNull(current, "current");
        return this;
    }

    public MachineRecipe recipe() {
        return recipe;
    }

    public String laneId() {
        return laneId;
    }

    long preparedParallelism() {
        return parallelism();
    }

    /** Actual copies admitted by the prepared plan, or zero when unavailable. */
    public long parallelism() {
        return preparedStart == null ? 0L : preparedStart.plan().parallelism();
    }

    /** The actual post-callback requirements used by this reservation. */
    public List<MachineRequirement> effectiveRequirements() {
        return preparedStart == null ? List.of() : MachineRequirement.copyList(preparedStart.effective().requirements());
    }

    public List<MachineOutput> effectiveOutputs() {
        return preparedStart == null ? List.of() : MachineOutput.copyList(preparedStart.effective().outputs());
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    public boolean commit() {
        return commit(ignored -> { });
    }

    /** Returns true once resources are paid, even if a root notification cancels activation. */
    public boolean commit(Consumer<TransactionContext> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (!beginCommitAttempt()) return false;
        try {
            boolean committed = false;
            try (Transaction transaction = Transaction.openRoot()) {
                if (!commitPlan(transaction)) return false;
                transactionWrites.accept(transaction);
                if (!currentBeforeRootCommit()) return false;
                transaction.commit();
                committed = true;
            } catch (RuntimeException exception) {
                if (!paid()) return false;
                pauseAfterNotificationFailure();
                committed = true;
            } finally {
                if (!committed) rollback();
            }
            activate();
            return true;
        } finally {
            endCommitAttempt();
        }
    }

    boolean beginCommitAttempt() {
        if (committing || status != Status.RESERVED || rolledBack) return false;
        committing = true;
        return true;
    }

    void endCommitAttempt() {
        committing = false;
    }

    boolean commitPlan(TransactionContext transaction) {
        if (status != Status.RESERVED || rolledBack || !admissionCurrent.getAsBoolean()
                || !runtime.commitPatternPlan(preparedStart, transaction)) return false;
        payment = runtime.patternStartPayment(preparedStart);
        return true;
    }

    boolean paid() {
        return payment != null && payment.state().paidAttempts() > 0L;
    }

    boolean currentBeforeRootCommit() {
        return status == Status.RESERVED && !rolledBack
                && admissionCurrent.getAsBoolean()
                && runtime.patternStartCurrentBeforeRootCommit(preparedStart, payment);
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
                activated = runtime.activatePatternStart(preparedStart);
            } finally {
                release.run();
            }
            if (activated) afterCommit.run();
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

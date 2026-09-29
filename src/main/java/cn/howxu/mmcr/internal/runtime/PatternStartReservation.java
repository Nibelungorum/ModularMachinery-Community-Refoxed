package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Owns one admitted pattern start until it is committed or released.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartReservation implements AutoCloseable {
    public enum Status {
        UNAVAILABLE,
        RESERVED,
        COMMITTED
    }

    private final MachineRecipe recipe;
    private final String laneId;
    private final CraftingRuntime runtime;
    private final CraftingRuntime.PreparedStart preparedStart;
    private final Runnable release;
    private Runnable afterCommit = () -> { };
    private boolean rolledBack;
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

    public boolean commit() {
        return commit(ignored -> { });
    }

    /** Commits the reserved start with additional storage mutations in the input transaction. */
    public boolean commit(Consumer<TransactionContext> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (status != Status.RESERVED || rolledBack) return false;
        try (Transaction transaction = Transaction.openRoot()) {
            if (!runtime.commitPatternPlan(preparedStart)) {
                rollback();
                return false;
            }
            transactionWrites.accept(transaction);
            transaction.commit();
        } catch (RuntimeException exception) {
            rollback();
            return false;
        }
        activate();
        return true;
    }

    boolean commitPlan() {
        return status == Status.RESERVED && !rolledBack && runtime.commitPatternPlan(preparedStart);
    }

    void activate() {
        runtime.activatePatternStart(preparedStart);
        release.run();
        status = Status.COMMITTED;
        afterCommit.run();
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
        rolledBack = true;
        release.run();
    }

    @Override
    public void close() {
        rollback();
    }
}

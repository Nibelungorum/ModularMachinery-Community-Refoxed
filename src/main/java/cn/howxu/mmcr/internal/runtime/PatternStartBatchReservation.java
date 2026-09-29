package cn.howxu.mmcr.internal.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Owns every lane reservation admitted for one external pattern batch.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartBatchReservation implements AutoCloseable {
    public enum Status {
        UNAVAILABLE,
        RESERVED,
        COMMITTED
    }

    private final List<PatternStartReservation> reservations;
    private Status status;

    private PatternStartBatchReservation(Status status, List<PatternStartReservation> reservations) {
        this.status = status;
        this.reservations = List.copyOf(reservations);
    }

    public static PatternStartBatchReservation reserved(List<PatternStartReservation> reservations) {
        Objects.requireNonNull(reservations, "reservations");
        if (reservations.isEmpty()) throw new IllegalArgumentException("reservations must not be empty");
        return new PatternStartBatchReservation(Status.RESERVED, reservations);
    }

    public static PatternStartBatchReservation unavailable() {
        return new PatternStartBatchReservation(Status.UNAVAILABLE, List.of());
    }

    public Status status() {
        return status;
    }

    public long parallelism() {
        return reservations.stream().mapToLong(reservation -> reservation.preparedParallelism()).sum();
    }

    public List<PatternStartReservation> reservations() {
        return reservations;
    }

    public boolean commit() {
        if (status != Status.RESERVED) return false;
        for (PatternStartReservation reservation : reservations) {
            if (reservation.commitPlan()) continue;
            rollback();
            return false;
        }
        reservations.forEach(PatternStartReservation::activate);
        status = Status.COMMITTED;
        return true;
    }

    /**
     * Legacy transaction callback boundary. Native pattern starts cannot share a Transfer transaction.
     */
    public boolean commit(Consumer<TransactionContext> transactionWrites) {
        Objects.requireNonNull(transactionWrites, "transactionWrites");
        if (status != Status.RESERVED) return false;
        rollback();
        return false;
    }

    public void rollback() {
        if (status != Status.RESERVED) return;
        reservations.forEach(PatternStartReservation::rollback);
        status = Status.UNAVAILABLE;
    }

    @Override
    public void close() {
        rollback();
    }
}

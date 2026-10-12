package cn.howxu.mmcr.internal.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Owns every lane reservation admitted for one external pattern batch.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartBatchReservation implements AutoCloseable {
    public enum Status {
        UNAVAILABLE,
        RESERVED,
        /** Holder ownership accepted; native partial payments are quarantined instead of replayed. */
        COMMITTED
    }

    private final List<PatternStartReservation> reservations;
    private Status status;
    private boolean committing;

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

    /** Native execution already owns this proposal, including an in-flight notification/save window. */
    public boolean ownershipAccepted() {
        return status == Status.COMMITTED || reservations.stream().anyMatch(PatternStartReservation::paid);
    }

    public boolean commit() { return commit(() -> { }); }

    public boolean commit(Runnable ownershipWrites) {
        Objects.requireNonNull(ownershipWrites, "ownershipWrites");
        if (committing || status != Status.RESERVED) return false;
        committing = true;
        int acquired = 0;
        boolean accepted = false;
        try {
            for (PatternStartReservation reservation : reservations) {
                if (!reservation.beginCommitAttempt()) return false;
                acquired++;
            }
            if (!reservations.stream().allMatch(PatternStartReservation::preflight)) return false;
            for (PatternStartReservation reservation : reservations) {
                if (!reservation.commitPlan()) {
                    accepted |= reservation.paid();
                    if (!accepted) { rollback(); return false; }
                    reservations.forEach(PatternStartReservation::pauseAfterNotificationFailure);
                    break;
                }
                accepted = true;
            }
            status = Status.COMMITTED;
            try { ownershipWrites.run(); }
            catch (RuntimeException exception) { reservations.forEach(PatternStartReservation::pauseAfterNotificationFailure); }
            for (PatternStartReservation reservation : reservations) {
                if (reservation.paid()) reservation.activate();
                else reservation.rollback();
            }
            return true;
        } finally {
            for (int index = 0; index < acquired; index++) reservations.get(index).endCommitAttempt();
            committing = false;
        }
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

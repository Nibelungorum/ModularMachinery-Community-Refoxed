package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;

/**
 * Physical storage identity shared by native resource projections.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface NativeReservationAccess {
    Object reservationIdentity();

    Object reservationSlot(int slot);

    Object resourceKey(Object resource);

    Object resource(Object key);

    Object storedKey(int slot);

    long storedAmount(int slot);

    /** Remaining inventory-wide budget for a complete resource key, or -1 when only slots are bounded. */
    default long insertionBudget(Object key) { return -1L; }

    /** Worker slot snapshots cannot represent shared insertion budgets. */
    default boolean requiresServerOutputPlanning() { return false; }

    /** Whole-destination output capacity, or -1 to use ordinary slot planning. */
    default long outputAvailable(Object resource, PlanningReservations reservations) { return -1L; }

    /** Reserves whole-destination output; nonnegative results are committed through insertOutput. */
    default long reserveOutput(Object resource, long amount, PlanningReservations reservations) { return -1L; }

    /** Exact whole-destination insertion for output actions with slot -1, or -1 when unsupported. */
    default long insertOutput(Object resource, long amount, boolean simulate) { return -1L; }

    /** Optional exact long transfer; -1 leaves transfer to the platform's ordinary handler. */
    default long transfer(int slot, Object resource, long amount, boolean insert, boolean simulate) { return -1L; }
}

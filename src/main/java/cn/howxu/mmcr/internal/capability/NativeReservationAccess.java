package cn.howxu.mmcr.internal.capability;

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

    /** Optional exact long transfer; -1 leaves transfer to the platform's ordinary handler. */
    default long transfer(int slot, Object resource, long amount, boolean insert, boolean simulate) { return -1L; }
}

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
}

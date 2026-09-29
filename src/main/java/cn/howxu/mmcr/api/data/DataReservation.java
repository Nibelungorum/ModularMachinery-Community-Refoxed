package cn.howxu.mmcr.api.data;

/** Reservation boundary for lazy data repositories.
 *
 * <p>Implementations decide how a reservation is committed or cancelled;
 * this module intentionally provides no concrete repository implementation.</p>
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DataReservation {
    boolean commit();

    void cancel();
}

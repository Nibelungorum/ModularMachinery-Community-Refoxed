package cn.howxu.mmcr.api.data.view;

/**
 * Public reservation for a data repository request.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface DataReservation {
    boolean commit();

    void cancel();
}

package cn.howxu.mmcr.publicapi.data;

/** User-implementable reservation; false does not implicitly abort an IO commit.
 * @author howxu <dev@howxu.cn>
 */
public interface Reservation {
    boolean commit();
    void cancel();
}

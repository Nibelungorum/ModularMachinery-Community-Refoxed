package cn.howxu.mmcr.publicapi.ui;

/** Addon-provided latest-value state source. Revision must increase monotonically
 * within a session; snapshot is queried initially and when revision changes.
 * @author howxu <dev@howxu.cn>
 */
public interface UiStateProvider<T> {
    long revision(UiServerContext context);
    T snapshot(UiServerContext context);
}

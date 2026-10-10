package cn.howxu.mmcr.publicapi.ui;

/** Addon-provided synchronous request handler, executed on the server thread.
 * Storage writes are immediate; callback failure does not roll back arbitrary side effects.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface UiRequestHandler<Q, R> {
    UiResult<R> handle(UiServerContext context, Q request);
}

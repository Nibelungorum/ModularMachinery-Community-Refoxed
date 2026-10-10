package cn.howxu.mmcr.publicapi.client.ui;

import org.jetbrains.annotations.ApiStatus;

/** Cancels future delivery, including callbacks queued before cancellation.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface UiSubscription extends AutoCloseable {
    @Override void close();
}

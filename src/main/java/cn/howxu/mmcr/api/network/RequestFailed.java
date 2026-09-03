package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.data.DataStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Handles a machine network request that could not be delivered.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface RequestFailed {
    void fail(RequestBody body, RequestInfo request, @Nullable DataStorage senderStorage, RequestFailureReason reason);
}

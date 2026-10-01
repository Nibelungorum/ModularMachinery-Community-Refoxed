package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.publicapi.data.DataStore;
import org.jetbrains.annotations.Nullable;

/** User-implementable failed-delivery handler. @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface FailureHandler {
    void fail(RequestPayload body, RequestDetails request, @Nullable DataStore senderStorage, RequestFailure reason);
}

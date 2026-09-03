package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.data.DataStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Handles a delivered machine network request.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface RequestProcess {
    void process(RequestBody body, RequestInfo request, @Nullable DataStorage senderStorage,
                 @Nullable DataStorage receiverStorage);
}

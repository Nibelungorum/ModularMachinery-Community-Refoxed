package cn.howxu.mmcr.publicapi.network;

import cn.howxu.mmcr.publicapi.data.DataStore;
import org.jetbrains.annotations.Nullable;

/** User-implementable queued request handler; writes are not implicitly transactional.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface RequestHandler {
    void process(RequestPayload body, RequestDetails request, @Nullable DataStore senderStorage,
                 @Nullable DataStore receiverStorage);
}

package cn.howxu.mmcr.api.network.view;

import cn.howxu.mmcr.api.data.view.DataStorage;

/** Handles a public machine network request on the server thread.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface RequestProcess {
    void process(RequestBody body, RequestInfo request, DataStorage senderStorage, DataStorage receiverStorage);
}

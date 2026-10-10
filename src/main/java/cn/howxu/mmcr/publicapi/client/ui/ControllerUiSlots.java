package cn.howxu.mmcr.publicapi.client.ui;

import org.jetbrains.annotations.ApiStatus;

/** Controls existing inventory slots, without changing their indices. Reads and writes
 * require the client main thread; writes also require a live, matching menu session.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ControllerUiSlots {
    boolean isPlayerInventoryVisible();
    void setPlayerInventoryVisible(boolean visible);
}

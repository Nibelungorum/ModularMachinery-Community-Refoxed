package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.BlockPos;

/** Opt-in to a baseline and committed long-storage updates while the menu is open.
 * @author howxu <dev@howxu.cn>
 */
public interface StorageSnapshotMenu extends PortMenu {
    boolean matches(BlockPos pos, String kind);
    void applySnapshot(PktPortStorageSyncPayload payload, IOPortBlockEntity port);
}

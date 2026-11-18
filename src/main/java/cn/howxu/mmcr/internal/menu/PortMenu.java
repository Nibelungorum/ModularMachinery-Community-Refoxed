package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

/** Server-selected port menu. Client menus may have no live owner.
 * @author howxu <dev@howxu.cn>
 */
public interface PortMenu {
    BlockPos pos();
    @Nullable IOPortBlockEntity owner();
    default @Nullable Identifier displayedCapability() { return null; }
    default int transferContainer(ServerPlayer player, int slot, boolean extract, TransactionContext tx) { return 0; }
}

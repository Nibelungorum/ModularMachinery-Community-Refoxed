package cn.howxu.mmcr.internal.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Server-side menu ownership and optional carried-container transfer. Never selected by a client type id.
 * @author howxu <dev@howxu.cn>
 */
public interface PortMenuBinding {
    IOPortBlockEntity owner();
    BlockPos pos();
    default void writeOpenData(RegistryFriendlyByteBuf buffer) { buffer.writeBlockPos(pos()); }
    default int transferContainer(ServerPlayer player, int tank, boolean extract) { return 0; }
}

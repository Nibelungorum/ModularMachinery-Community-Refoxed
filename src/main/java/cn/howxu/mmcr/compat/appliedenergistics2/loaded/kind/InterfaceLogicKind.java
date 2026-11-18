package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.world.item.Item;

/**
 * A port kind that supplies its AE2 interface logic profile.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface InterfaceLogicKind extends IOPortKind {
    @Override default InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) {
        return player instanceof ServerPlayer server && AE2Bridge.get().openMenu(server, level, pos)
                ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    default InterfaceLogic createInterfaceLogic(IManagedGridNode node, InterfaceLogicHost host, Item icon) {
        return new InterfaceLogic(node, host, icon);
    }
}

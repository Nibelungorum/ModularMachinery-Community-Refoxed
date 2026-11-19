package cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind;

import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.internal.port.IOPortKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.MenuType;

/** Opens AE2's locator-backed menus outside the ordinary block menu provider.
 * @author howxu <dev@howxu.cn>
 */
public interface AE2PortKind extends IOPortKind {
    MenuType<?> menuType();

    @Override
    default InteractionResult openSpecialMenu(Player player, Level level, BlockPos pos) {
        return player instanceof ServerPlayer serverPlayer && AE2Bridge.get().openMenu(serverPlayer, level, pos)
                ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}

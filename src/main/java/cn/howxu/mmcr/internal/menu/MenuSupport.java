package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 通用工具:玩家物品栏复制 / 距离检查 / 跨菜单复用的常量。 */
public final class MenuSupport {

    private MenuSupport() {}

    public static ItemStack noopQuickMove() {
        return ItemStack.EMPTY;
    }

    public static boolean stillValidWithin(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter()) < 64;
    }

    public static boolean controllerStillPresentAndFormed(MachineControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null || !controller.isFormed()) return false;
        BlockEntity blockEntity = controller.getLevel().getBlockEntity(controller.getBlockPos());
        return blockEntity == controller;
    }
}

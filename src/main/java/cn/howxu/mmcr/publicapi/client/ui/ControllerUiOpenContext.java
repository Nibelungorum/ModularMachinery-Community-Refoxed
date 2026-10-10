package cn.howxu.mmcr.publicapi.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.ApiStatus;

/** Client main-thread factory arguments, independent of the chosen UI framework.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ControllerUiOpenContext {
    AbstractContainerMenu menu();
    Inventory inventory();
    Component title();
    ControllerUiSession session();
}

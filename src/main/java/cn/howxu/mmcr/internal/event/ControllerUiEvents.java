package cn.howxu.mmcr.internal.event;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/** Activate only installed menus; server close and removed are deliberately idempotent.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID)
public final class ControllerUiEvents {
    private ControllerUiEvents() { }

    @SubscribeEvent
    public static void opened(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof ControllerUiMenu menu
                && menu.uiServerSession() != null) {
            menu.uiServerSession().activate(player);
            menu.uiServerSession().broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void closed(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer && event.getContainer() instanceof ControllerUiMenu menu
                && menu.uiServerSession() != null) {
            menu.uiServerSession().close();
        }
    }
}

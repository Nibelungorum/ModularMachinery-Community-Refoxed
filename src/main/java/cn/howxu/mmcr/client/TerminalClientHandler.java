package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.TerminalScreen;
import cn.howxu.mmcr.internal.item.TerminalAction;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.internal.network.PktTerminalActionPayload;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/** Client input bridge and server-state receiver for the terminal screen.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class TerminalClientHandler {
    private TerminalClientHandler() {}

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (!shouldOpenScreen(event.isUseItem(), event.getHand(), minecraft.hasShiftDown(), minecraft.screen != null,
                minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.MISS,
                minecraft.player.getMainHandItem().is(ModItems.TERMINAL.get()))) return;
        TerminalData data = TerminalData.from(minecraft.player.getMainHandItem());
        minecraft.setScreen(new TerminalScreen(data, false, false, List.of(), ""));
        ClientPacketDistributor.sendToServer(initialStateRequest());
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    public static void applyState(TerminalData data, boolean controllerAvailable, boolean storageAvailable,
            List<Integer> stages, Component machineName, List<Integer> previewLayers, String statusKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TerminalScreen screen) {
            screen.applyState(data, controllerAvailable, storageAvailable, stages, machineName, previewLayers, statusKey);
        }
    }

    static PktTerminalActionPayload initialStateRequest() {
        return new PktTerminalActionPayload(TerminalAction.REQUEST_STATE, 0, null, null);
    }

    static boolean shouldOpenScreen(boolean useItem, InteractionHand hand, boolean shiftDown,
            boolean hasScreen, boolean miss, boolean terminalHeld) {
        return useItem && hand == InteractionHand.MAIN_HAND && !shiftDown && !hasScreen && miss && terminalHeld;
    }
}

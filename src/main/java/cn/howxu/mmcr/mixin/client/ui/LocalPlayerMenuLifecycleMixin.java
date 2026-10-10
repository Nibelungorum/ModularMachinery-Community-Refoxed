package cn.howxu.mmcr.mixin.client.ui;

import cn.howxu.mmcr.client.controller.ui.ControllerUiClientEvents;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes native close after inventoryMenu is restored, including null-screen closes.
 * @author howxu <dev@howxu.cn> */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMenuLifecycleMixin {
    @Inject(method = "clientSideCloseContainer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void mmcr$menuClosed(CallbackInfo callback) {
        ControllerUiClientEvents.onMenuChanged();
    }
}

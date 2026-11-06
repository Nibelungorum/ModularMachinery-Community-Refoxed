package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.ChannelCardHostSupport;
import com.extendedae_plus.util.wireless.ChannelCardConnectionController;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @author howxu <dev@howxu.cn> */
@Mixin(value = InterfaceLogic.class, priority = 500, remap = false)
public abstract class StockingChannelCardLogicMixin {
    @Shadow @Final protected InterfaceLogicHost host;
    @Unique private ChannelCardConnectionController mmcr$channelController;

    @Dynamic("Added by EAEP InterfaceLogicChannelCardMixin before this lower-priority mixin")
    @Inject(method = "eap$getChannelCardController", at = @At("HEAD"), cancellable = true)
    private void mmcr$useStockingWorkNode(CallbackInfoReturnable<ChannelCardConnectionController> callback) {
        if (!(host instanceof StockingInterfaceBlockEntity stocking)) return;
        if (mmcr$channelController == null) {
            mmcr$channelController = ChannelCardHostSupport.createController(stocking, stocking.getMainNode());
            ChannelCardConnectionController.register(stocking, mmcr$channelController);
        }
        callback.setReturnValue(mmcr$channelController);
    }
}

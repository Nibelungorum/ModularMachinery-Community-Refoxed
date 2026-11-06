package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBaseBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae_plus.loaded.ChannelCardHostSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @author howxu <dev@howxu.cn> */
@Mixin(value = {InputInterfaceBlockEntity.class, OutputInterfaceBaseBlockEntity.class,
        StockingInterfaceBlockEntity.class, PatternInterfaceBlockEntity.class}, remap = false)
public abstract class ChannelCardHostLifecycleMixin {
    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void mmcr$loadChannelCard(CallbackInfo callback) {
        ChannelCardHostSupport.loaded((IOPortBlockEntity) (Object) this);
    }

    @Inject(method = {"onChunkUnloaded", "setRemoved"}, at = @At("HEAD"))
    private void mmcr$unloadChannelCard(CallbackInfo callback) {
        ChannelCardHostSupport.unloaded((IOPortBlockEntity) (Object) this);
    }
}

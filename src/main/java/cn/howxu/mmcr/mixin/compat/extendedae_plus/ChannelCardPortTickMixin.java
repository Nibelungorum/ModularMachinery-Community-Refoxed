package cn.howxu.mmcr.mixin.compat.extendedae_plus;

import cn.howxu.mmcr.compat.extendedae_plus.loaded.ChannelCardHostSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** @author howxu <dev@howxu.cn> */
@Mixin(value = IOPortBlockEntity.class, remap = false)
public abstract class ChannelCardPortTickMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private void mmcr$maintainChannelCard(CallbackInfo callback) {
        IOPortBlockEntity host = (IOPortBlockEntity) (Object) this;
        if (host.getLevel() == null || host.getLevel().isClientSide() || host.isRemoved()) return;
        // Stagger loaded hosts and keep discovery running even when the AE node is asleep or unpowered.
        if (Math.floorMod(host.getLevel().getGameTime() + host.getBlockPos().asLong(), 20L) != 0L) return;
        ChannelCardHostSupport.tick(ChannelCardHostSupport.wirelessLogic(host));
    }
}

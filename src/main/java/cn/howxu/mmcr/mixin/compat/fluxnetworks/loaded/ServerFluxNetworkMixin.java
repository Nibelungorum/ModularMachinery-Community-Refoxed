package cn.howxu.mmcr.mixin.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceBlockEntity;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkRefunds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.PhantomFluxDevice;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.util.HashMap;
import java.util.LinkedList;

/** Cancels MMCR admissions that native removal ignores until the next cycle.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = ServerFluxNetwork.class, remap = false)
public abstract class ServerFluxNetworkMixin {
    @Shadow @Final private LinkedList<TileFluxDevice> mToAdd;

    @Inject(method = "onEndServerTick()V", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/ServerFluxNetwork;getLogicalDevices(I)Ljava/util/ArrayList;",
            ordinal = 1))
    private void mmcr$returnIdleEnergy(CallbackInfo ci) {
        FluxNetworkRefunds.refund((ServerFluxNetwork) (Object) this);
    }

    @Inject(method = "enqueueConnectionRemoval(Lsonar/fluxnetworks/common/device/TileFluxDevice;Z)V", at = @At("HEAD"), cancellable = true)
    private void mmcr$cancelPendingAddition(TileFluxDevice device, boolean unload, CallbackInfo ci) {
        if (!(device instanceof FluxNetworkInterfaceBlockEntity)
                || ((ServerFluxNetwork) (Object) this).getLogicalDevices(FluxNetwork.ANY).contains(device)
                || !mToAdd.removeIf(pending -> pending == device)) return;

        var connections = ((FluxNetworkAccessor) this).mmcr$getConnectionMap();
        var pos = device.getGlobalPos();
        if (connections.get(pos) == device) {
            if (unload) connections.put(pos, PhantomFluxDevice.makeUnloaded(device));
            else connections.remove(pos);
        }
        // Never queue removal for a device which has not entered the logical lists.
        ci.cancel();
    }

    @Redirect(method = "enqueueConnectionRemoval(Lsonar/fluxnetworks/common/device/TileFluxDevice;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/util/HashMap;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object mmcr$removeMatchingConnection(HashMap<Object, Object> connections, Object pos,
            TileFluxDevice device, boolean unload) {
        if (device instanceof FluxNetworkInterfaceBlockEntity && connections.get(pos) != device) return connections.get(pos);
        return connections.remove(pos);
    }

    @Redirect(method = "enqueueConnectionRemoval(Lsonar/fluxnetworks/common/device/TileFluxDevice;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object mmcr$unloadMatchingConnection(HashMap<Object, Object> connections, Object pos, Object phantom,
            TileFluxDevice device, boolean unload) {
        if (device instanceof FluxNetworkInterfaceBlockEntity && connections.get(pos) != device) return connections.get(pos);
        return connections.put(pos, phantom);
    }
}

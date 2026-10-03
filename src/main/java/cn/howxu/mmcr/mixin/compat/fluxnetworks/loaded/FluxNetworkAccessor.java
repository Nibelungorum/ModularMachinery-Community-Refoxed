package cn.howxu.mmcr.mixin.compat.fluxnetworks.loaded;

import net.minecraft.core.GlobalPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import sonar.fluxnetworks.api.device.IFluxDevice;
import sonar.fluxnetworks.common.connection.FluxNetwork;

import java.util.HashMap;

/** Access to the native identity map for pending connection cancellation.
 * @author howxu <dev@howxu.cn>
 */
@Mixin(value = FluxNetwork.class, remap = false)
public interface FluxNetworkAccessor {
    @Accessor("mConnectionMap")
    HashMap<GlobalPos, IFluxDevice> mmcr$getConnectionMap();
}

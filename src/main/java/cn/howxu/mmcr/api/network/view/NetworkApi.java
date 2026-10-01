package cn.howxu.mmcr.api.network.view;

import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Public facade for queued machine network requests.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkApi {
    private NetworkApi() {
    }

    public static List<NetworkInterfaceReference> interfaces(MachineBehaviorContext context) {
        return cn.howxu.mmcr.api.network.NetworkApi.interfaces(Objects.requireNonNull(context, "context")).stream()
                .map(NetworkInterfaceReference::new).toList();
    }

    public static void sendRequest(NetworkInterfaceReference source, MachineReference target, ResourceLocation requestId,
                                   RequestBody body) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        cn.howxu.mmcr.api.network.NetworkApi.sendRequest(
                (cn.howxu.mmcr.api.network.NetworkInterfaceReference) source.bridgeValue(),
                (cn.howxu.mmcr.api.network.MachineReference) target.bridgeValue(), requestId,
                (cn.howxu.mmcr.api.network.RequestBody) body.bridgeValue());
    }
}

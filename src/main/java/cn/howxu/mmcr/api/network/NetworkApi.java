package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.internal.network.NetworkServerState;
import cn.howxu.mmcr.internal.network.PendingRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Java facade for queued machine network requests.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkApi {
    private NetworkApi() {
    }

    public static List<NetworkInterfaceReference> interfaces(MachineBehaviorContext context) {
        Objects.requireNonNull(context, "context");
        if (!(context.level() instanceof ServerLevel level) || !context.controller().currentStructureSnapshot().formed()) {
            return List.of();
        }
        return context.controller().activeNetworkInterfacePositions().stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ)).filter(level::hasChunkAt)
                .map(position -> new NetworkInterfaceReference(level.getServer(), GlobalPos.of(level.dimension(), position)))
                .toList();
    }

    public static void sendRequest(NetworkInterfaceReference source, MachineReference target, Identifier requestId,
                                   RequestBody body) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(body, "body");
        if (requestId.getNamespace().isBlank() || requestId.getPath().isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        GlobalPos targetEndpoint = source.endpointFor(target);
        if (targetEndpoint == null) throw new IllegalArgumentException("Target is not connected to the source interface");
        var level = source.server().getLevel(source.source().dimension());
        long tick = level == null ? 0L : level.getGameTime();
        NetworkServerState.get(source.server()).enqueue(new PendingRequest(source.source(), targetEndpoint, target,
                requestId, body, tick));
    }
}

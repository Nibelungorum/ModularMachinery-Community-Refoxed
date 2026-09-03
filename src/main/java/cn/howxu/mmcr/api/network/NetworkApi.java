package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.internal.network.NetworkServerState;
import cn.howxu.mmcr.internal.network.PendingRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
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
        if (!(context.level() instanceof ServerLevel level)) {
            return List.of();
        }
        List<NetworkInterfaceReference> references = new ArrayList<>();
        level.getServer().executeBlocking(() -> {
            var controller = context.controller();
            if (!controller.currentStructureSnapshot().formed()) return;
            var sourceMachine = controller.currentStructureSnapshot().machine();
            GlobalPos sourceController = GlobalPos.of(level.dimension(), controller.getBlockPos());
            references.addAll(controller.activeNetworkInterfacePositions().stream()
                    .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ))
                    .filter(level::hasChunkAt)
                    .map(position -> new NetworkInterfaceReference(level.getServer(),
                            GlobalPos.of(level.dimension(), position), sourceController, sourceMachine))
                    .toList());
        });
        return List.copyOf(references);
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
        source.server().executeBlocking(() -> {
            GlobalPos targetEndpoint = source.endpointFor(target);
            if (targetEndpoint == null) throw new IllegalArgumentException("Target is not connected to the source interface");
            NetworkServerState.get(source.server()).enqueue(new PendingRequest(source.source(), targetEndpoint,
                    source.sourceController(), target, requestId, body, source.server().getTickCount(),
                    source.sourceFailure(requestId)));
        });
    }
}

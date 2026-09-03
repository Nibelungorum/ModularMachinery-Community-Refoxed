package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.api.network.RequestFailureReason;
import cn.howxu.mmcr.api.network.RequestFailed;
import cn.howxu.mmcr.api.network.RequestInfo;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and dispatches queued machine network requests on the server thread.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkRequestDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NetworkRequestDispatcher.class);
    private final MinecraftServer server;

    NetworkRequestDispatcher(MinecraftServer server) {
        this.server = server;
    }

    void dispatch(PendingRequest request) {
        Resolved source = resolve(request.sourceEndpoint());
        if (source == null || source.network == null) {
            fail(sourceFailure(request, source), request, RequestFailureReason.SOURCE_INTERFACE_MISSING);
            return;
        }
        if (source.controller == null || !valid(source, request.sourceEndpoint())
                || request.sourceController() != null && !request.sourceController().equals(source.controller.getLevel() == null
                ? null : GlobalPos.of(source.controller.getLevel().dimension(), source.controller.getBlockPos()))) {
            fail(sourceFailure(request, source), request, RequestFailureReason.SOURCE_STRUCTURE_INVALID);
            return;
        }
        Resolved target = resolve(request.targetEndpoint());
        if (target == null) {
            fail(source, request, RequestFailureReason.TARGET_CHUNK_UNLOADED);
            return;
        }
        if (target.network == null) {
            fail(source, request, RequestFailureReason.TARGET_INTERFACE_MISSING);
            return;
        }
        if (target.controller == null || !valid(target, request.targetEndpoint())) {
            fail(source, request, RequestFailureReason.TARGET_STRUCTURE_INVALID);
            return;
        }
        MachineReference sourceReference = source.controller.machineReference();
        MachineReference targetReference = target.controller.machineReference();
        if (!request.target().equals(targetReference)) {
            fail(source, request, RequestFailureReason.HASH_MISMATCH);
            return;
        }
        if (!connected(source.network, request.targetEndpoint(), request.target())
                || !connected(target.network, request.sourceEndpoint(), sourceReference)) {
            fail(source, request, RequestFailureReason.CONNECTION_MISSING);
            return;
        }
        if (!allows(source.machine, targetReference) || !allows(target.machine, sourceReference)) {
            fail(source, request, RequestFailureReason.ALLOWLIST_REJECTED);
            return;
        }
        var process = target.machine.requestProcessors().get(request.requestId());
        if (process == null) {
            fail(source, request, RequestFailureReason.TARGET_HANDLER_MISSING);
            return;
        }
        try {
            process.process(request.body(), new RequestInfo(request.requestId(), sourceReference),
                    source.controller.behaviorContext().dataStorage(), target.controller.behaviorContext().dataStorage());
        } catch (RuntimeException exception) {
            LOG.warn("Machine network request handler failed for {}", request.requestId(), exception);
        }
    }

    private void fail(Resolved source, PendingRequest request, RequestFailureReason reason) {
        RequestFailed failure = source == null || source.machine == null
                ? null : source.machine.requestFailures().get(request.requestId());
        if (failure == null) failure = request.sourceFailure();
        if (failure == null) return;
        var senderStorage = source == null || source.controller == null
                ? null : source.controller.behaviorContext().dataStorage();
        try {
            failure.fail(request.body(), new RequestInfo(request.requestId(), request.target()),
                    senderStorage, reason);
        } catch (RuntimeException exception) {
            LOG.warn("Machine network request failure handler failed for {}", request.requestId(), exception);
        }
    }

    private Resolved sourceFailure(PendingRequest request, Resolved source) {
        if (source != null && source.controller != null && source.machine != null) {
            GlobalPos owner = source.controller.getLevel() == null ? null
                    : GlobalPos.of(source.controller.getLevel().dimension(), source.controller.getBlockPos());
            if (request.sourceController() == null || request.sourceController().equals(owner)) return source;
        }
        return request.sourceController() == null ? null : resolveController(request.sourceController());
    }

    private Resolved resolve(GlobalPos endpoint) {
        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null || !level.hasChunkAt(endpoint.pos())) return null;
        if (!(level.getBlockEntity(endpoint.pos()) instanceof NetworkInterfaceBlockEntity network)) {
            return new Resolved(null, null, null);
        }
        GlobalPos owner = network.owner().orElse(null);
        if (owner == null || !owner.dimension().equals(level.dimension()) || !level.hasChunkAt(owner.pos())
                || !(level.getBlockEntity(owner.pos()) instanceof MachineControllerBlockEntity controller)) {
            return new Resolved(network, null, null);
        }
        Machine machine = controller.currentStructureSnapshot().machine();
        return new Resolved(network, controller, machine);
    }

    private Resolved resolveController(GlobalPos position) {
        if (position == null) return null;
        ServerLevel level = server.getLevel(position.dimension());
        if (level == null || !level.hasChunkAt(position.pos())
                || !(level.getBlockEntity(position.pos()) instanceof MachineControllerBlockEntity controller)) return null;
        return new Resolved(null, controller, controller.currentStructureSnapshot().machine());
    }

    private static boolean valid(Resolved endpoint, GlobalPos position) {
        return endpoint.machine != null && endpoint.controller.currentStructureSnapshot().formed()
                && endpoint.controller.machineReference() != null && endpoint.controller.hasActiveNetworkInterface(position.pos());
    }

    private static boolean connected(NetworkInterfaceBlockEntity endpoint, GlobalPos peer, MachineReference machine) {
        return endpoint.connections().stream().anyMatch(connection -> connection.endpoint().equals(peer)
                && connection.machine().equals(machine));
    }

    private static boolean allows(Machine source, MachineReference target) {
        return source.networkInterface().allowedMachineIds().contains(target.type());
    }

    private record Resolved(NetworkInterfaceBlockEntity network, MachineControllerBlockEntity controller, Machine machine) {
    }
}

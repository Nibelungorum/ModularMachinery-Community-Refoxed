package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.internal.network.NetworkServerState;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Comparator;
import java.util.List;

/** Coordinates reciprocal machine-network bindings without loading remote chunks.
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkInterfaceBindingCoordinator {
    private NetworkInterfaceBindingCoordinator() {
    }

    public static ConnectionResult connect(MinecraftServer server, GlobalPos sourceEndpoint, MachineReference sourceMachine,
                                           GlobalPos targetEndpoint, MachineReference targetMachine) {
        ResolvedEndpoint source = resolve(server, sourceEndpoint);
        if (source == null) return ConnectionResult.SOURCE_UNLOADED;
        ResolvedEndpoint target = resolve(server, targetEndpoint);
        if (target == null) return ConnectionResult.TARGET_UNLOADED;
        if (source.controller == null) return ConnectionResult.INVALID_SOURCE;
        if (target.controller == null) return ConnectionResult.INVALID_TARGET;
        if (!formed(source.controller)) return ConnectionResult.SOURCE_NOT_FORMED;
        if (!formed(target.controller)) return ConnectionResult.TARGET_NOT_FORMED;
        if (!sourceMachine.equals(source.controller.machineReference())) return ConnectionResult.SOURCE_IDENTITY_MISMATCH;
        if (!targetMachine.equals(target.controller.machineReference())) return ConnectionResult.TARGET_IDENTITY_MISMATCH;
        if (!source.controller.hasActiveNetworkInterface(sourceEndpoint.pos())) return ConnectionResult.INVALID_SOURCE;
        if (!target.controller.hasActiveNetworkInterface(targetEndpoint.pos())) return ConnectionResult.INVALID_TARGET;
        if (!allows(source.controller, targetMachine) || !allows(target.controller, sourceMachine)) {
            return ConnectionResult.ALLOWLIST_REJECTED;
        }
        if (source.network.connections().stream().anyMatch(connection -> connection.endpoint().equals(targetEndpoint)
                && connection.machine().equals(targetMachine))) return ConnectionResult.DUPLICATE;
        if (connectionCount(server, source.controller) >= machine(source.controller).networkInterface().maxConnections()) {
            return ConnectionResult.SOURCE_CAPACITY;
        }
        if (connectionCount(server, target.controller) >= machine(target.controller).networkInterface().maxConnections()) {
            return ConnectionResult.TARGET_CAPACITY;
        }
        long sequence = NetworkServerState.get(server).nextConnectionSequence();
        NetworkInterfaceBlockEntity.Connection forward = new NetworkInterfaceBlockEntity.Connection(targetEndpoint, targetMachine, sequence);
        NetworkInterfaceBlockEntity.Connection reverse = new NetworkInterfaceBlockEntity.Connection(sourceEndpoint, sourceMachine, sequence);
        if (!source.network.addConnection(forward) || !target.network.addConnection(reverse)) {
            source.network.removeConnection(forward);
            target.network.removeConnection(reverse);
            return ConnectionResult.INVALID_TARGET;
        }
        return ConnectionResult.CONNECTED;
    }

    public static void disconnect(MinecraftServer server, GlobalPos sourceEndpoint, MachineReference sourceMachine,
                                  GlobalPos targetEndpoint, MachineReference targetMachine) {
        ResolvedEndpoint source = resolve(server, sourceEndpoint);
        ResolvedEndpoint target = resolve(server, targetEndpoint);
        if (source != null && source.network != null) {
            source.network.removeConnection(new NetworkInterfaceBlockEntity.Connection(targetEndpoint, targetMachine, 0L));
        }
        if (target != null && target.network != null) {
            target.network.removeConnection(new NetworkInterfaceBlockEntity.Connection(sourceEndpoint, sourceMachine, 0L));
        }
    }

    public static void reconcile(MinecraftServer server, MachineControllerBlockEntity controller) {
        if (server == null || controller == null || !formed(controller)) return;
        List<StoredConnection> connections = activeInterfaces(server, controller).stream()
                .flatMap(network -> network.connections().stream().map(connection -> new StoredConnection(network, connection)))
                .sorted(Comparator.comparingLong(stored -> stored.connection.sequence()))
                .toList();
        int maxConnections = machine(controller).networkInterface().maxConnections();
        for (int index = maxConnections; index < connections.size(); index++) {
            StoredConnection stored = connections.get(index);
            NetworkInterfaceBlockEntity.Connection connection = stored.connection;
            GlobalPos sourceEndpoint = GlobalPos.of(((ServerLevel) stored.network.getLevel()).dimension(), stored.network.getBlockPos());
            disconnect(server, sourceEndpoint, controller.machineReference(), connection.endpoint(), connection.machine());
        }
    }

    public static void clearConnectionsFor(MinecraftServer server, MachineControllerBlockEntity controller) {
        if (server == null || controller == null) return;
        MachineReference reference = controller.machineReference();
        for (NetworkInterfaceBlockEntity network : activeInterfaces(server, controller)) {
            GlobalPos endpoint = GlobalPos.of(((ServerLevel) network.getLevel()).dimension(), network.getBlockPos());
            for (NetworkInterfaceBlockEntity.Connection connection : network.connections()) {
                disconnect(server, endpoint, reference, connection.endpoint(), connection.machine());
            }
        }
    }

    public static void heartbeat(ServerLevel level) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        for (BlockPos controllerPos : StructureClaimRegistry.get(level).claimedControllers()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller)) continue;
            reconcile(server, controller);
            MachineReference sourceMachine = controller.machineReference();
            if (sourceMachine == null) continue;
            for (NetworkInterfaceBlockEntity network : activeInterfaces(server, controller)) {
                GlobalPos sourceEndpoint = GlobalPos.of(level.dimension(), network.getBlockPos());
                for (NetworkInterfaceBlockEntity.Connection connection : network.connections()) {
                    ResolvedEndpoint target = resolve(server, connection.endpoint());
                    if (target == null) continue;
                    if (target.controller == null || !formed(target.controller)
                            || !connection.machine().equals(target.controller.machineReference())
                            || !target.controller.hasActiveNetworkInterface(connection.endpoint().pos())
                            || !allows(controller, connection.machine()) || !allows(target.controller, sourceMachine)
                            || target.network.connections().stream().noneMatch(reverse -> reverse.endpoint().equals(sourceEndpoint)
                            && reverse.machine().equals(sourceMachine) && reverse.sequence() == connection.sequence())) {
                        disconnect(server, sourceEndpoint, sourceMachine, connection.endpoint(), connection.machine());
                    }
                }
            }
        }
    }

    private static List<NetworkInterfaceBlockEntity> activeInterfaces(MinecraftServer server,
                                                                        MachineControllerBlockEntity controller) {
        ServerLevel level = server.getLevel(controller.getLevel().dimension());
        if (level == null) return List.of();
        return controller.activeNetworkInterfacePositions().stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ))
                .filter(level::hasChunkAt).map(level::getBlockEntity).filter(NetworkInterfaceBlockEntity.class::isInstance)
                .map(NetworkInterfaceBlockEntity.class::cast).toList();
    }

    private static int connectionCount(MinecraftServer server, MachineControllerBlockEntity controller) {
        return activeInterfaces(server, controller).stream().mapToInt(network -> network.connections().size()).sum();
    }

    private static boolean formed(MachineControllerBlockEntity controller) {
        return machine(controller) != null && controller.currentStructureSnapshot().formed();
    }

    private static Machine machine(MachineControllerBlockEntity controller) {
        return controller.currentStructureSnapshot().machine();
    }

    private static boolean allows(MachineControllerBlockEntity controller, MachineReference target) {
        return machine(controller).networkInterface().allowedMachineIds().contains(target.type());
    }

    private static ResolvedEndpoint resolve(MinecraftServer server, GlobalPos endpoint) {
        if (server == null || endpoint == null) return null;
        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null || !level.hasChunkAt(endpoint.pos())) return null;
        BlockEntity blockEntity = level.getBlockEntity(endpoint.pos());
        if (!(blockEntity instanceof NetworkInterfaceBlockEntity network)) return new ResolvedEndpoint(null, null);
        GlobalPos owner = network.owner().orElse(null);
        if (owner == null || !owner.dimension().equals(level.dimension()) || !level.hasChunkAt(owner.pos())
                || !(level.getBlockEntity(owner.pos()) instanceof MachineControllerBlockEntity controller)) {
            return new ResolvedEndpoint(network, null);
        }
        return new ResolvedEndpoint(network, controller);
    }

    public enum ConnectionResult {
        CONNECTED, DUPLICATE, SOURCE_UNLOADED, TARGET_UNLOADED, INVALID_SOURCE, INVALID_TARGET,
        SOURCE_NOT_FORMED, TARGET_NOT_FORMED, SOURCE_IDENTITY_MISMATCH, TARGET_IDENTITY_MISMATCH,
        ALLOWLIST_REJECTED, SOURCE_CAPACITY, TARGET_CAPACITY
    }

    private record ResolvedEndpoint(NetworkInterfaceBlockEntity network, MachineControllerBlockEntity controller) {
    }

    private record StoredConnection(NetworkInterfaceBlockEntity network, NetworkInterfaceBlockEntity.Connection connection) {
    }
}

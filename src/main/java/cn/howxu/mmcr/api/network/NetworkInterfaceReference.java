package cn.howxu.mmcr.api.network;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.internal.tile.NetworkInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-created reference to an active machine network interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class NetworkInterfaceReference {
    private final MinecraftServer server;
    private final GlobalPos source;
    private final GlobalPos sourceController;
    private final Map<Identifier, RequestFailed> sourceFailures;

    NetworkInterfaceReference(MinecraftServer server, GlobalPos source, GlobalPos sourceController, Machine sourceMachine) {
        this.server = Objects.requireNonNull(server, "server");
        this.source = Objects.requireNonNull(source, "source");
        this.sourceController = Objects.requireNonNull(sourceController, "sourceController");
        this.sourceFailures = sourceMachine == null ? Map.of() : Map.copyOf(sourceMachine.requestFailures());
    }

    public BlockPos position() {
        return source.pos();
    }

    public List<MachineReference> connections() {
        NetworkInterfaceBlockEntity endpoint = endpoint();
        return endpoint == null ? List.of() : endpoint.connections().stream()
                .map(NetworkInterfaceBlockEntity.Connection::machine).toList();
    }

    GlobalPos endpointFor(MachineReference target) {
        NetworkInterfaceBlockEntity endpoint = endpoint();
        if (endpoint == null) return null;
        return endpoint.connections().stream().filter(connection -> connection.machine().equals(target))
                .map(NetworkInterfaceBlockEntity.Connection::endpoint).findFirst().orElse(null);
    }

    GlobalPos owner() {
        NetworkInterfaceBlockEntity endpoint = endpoint();
        return endpoint == null ? null : endpoint.owner().orElse(null);
    }

    GlobalPos sourceController() {
        return sourceController;
    }

    RequestFailed sourceFailure(Identifier requestId) {
        return sourceFailures.get(requestId);
    }

    MinecraftServer server() {
        return server;
    }

    GlobalPos source() {
        return source;
    }

    private NetworkInterfaceBlockEntity endpoint() {
        var level = server.getLevel(source.dimension());
        if (level == null || !level.hasChunkAt(source.pos())) return null;
        return level.getBlockEntity(source.pos()) instanceof NetworkInterfaceBlockEntity endpoint ? endpoint : null;
    }
}

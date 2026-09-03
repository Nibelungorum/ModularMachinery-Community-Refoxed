package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.network.MachineReference;
import cn.howxu.mmcr.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persists one machine network endpoint and its formed connections.
 * @author howxu <dev@howxu.cn>
 */
public class NetworkInterfaceBlockEntity extends LinkedAppearanceBlockEntity {
    private static final String OWNER_KEY = "owner";
    private static final String CONNECTIONS_KEY = "connections";
    private static final String ENDPOINT_KEY = "endpoint";
    private static final String DIMENSION_KEY = "dimension";
    private static final String X_KEY = "x";
    private static final String Y_KEY = "y";
    private static final String Z_KEY = "z";
    private static final String MACHINE_KEY = "machine";
    private static final String HASH_KEY = "hash";
    private static final String SEQUENCE_KEY = "sequence";
    private static final int HEARTBEAT_INTERVAL_TICKS = 40;

    private @Nullable GlobalPos owner;
    private final Map<ConnectionKey, Connection> connections = new LinkedHashMap<>();
    private int heartbeatCounter;

    public NetworkInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_INTERFACE.get(), pos, state);
    }

    public Optional<GlobalPos> owner() {
        return Optional.ofNullable(owner);
    }

    public List<Connection> connections() {
        return List.copyOf(connections.values());
    }

    public boolean claimOwner(GlobalPos owner) {
        if (owner == null) return false;
        if (this.owner != null && !this.owner.equals(owner)) return false;
        if (this.owner != null) return true;
        this.owner = owner;
        setChanged();
        return true;
    }

    public boolean releaseOwner(GlobalPos owner) {
        if (owner == null || !owner.equals(this.owner)) return false;
        unlinkControllerAppearance(owner.pos());
        this.owner = null;
        connections.clear();
        setChanged();
        return true;
    }

    public boolean addConnection(Connection connection) {
        if (!valid(connection)) return false;
        Connection previous = connections.put(new ConnectionKey(connection.endpoint(), connection.machine()), connection);
        if (!Objects.equals(previous, connection)) setChanged();
        return true;
    }

    public boolean removeConnection(GlobalPos endpoint) {
        if (endpoint == null || !connections.keySet().removeIf(key -> endpoint.equals(key.endpoint()))) return false;
        setChanged();
        return true;
    }

    public boolean removeConnection(Connection connection) {
        if (connection == null || connections.remove(new ConnectionKey(connection.endpoint(), connection.machine())) == null) return false;
        setChanged();
        return true;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()
                || Math.floorMod(heartbeatCounter++ + worldPosition.asLong(), HEARTBEAT_INTERVAL_TICKS) != 0) return;
        GlobalPos currentOwner = owner;
        if (currentOwner != null && currentOwner.dimension().equals(level.dimension())
                && level.hasChunkAt(currentOwner.pos())
                && (!(level.getBlockEntity(currentOwner.pos()) instanceof MachineControllerBlockEntity controller)
                || !controller.hasActiveNetworkInterface(worldPosition))) {
            releaseOwner(currentOwner);
        }
        maintainControllerLink();
    }

    /** Removes this endpoint from connected peers whose chunks are already loaded.
     *
     * The network coordinator can extend this seam for cross-level resolution without forcing chunks here.
     */
    public void removeFromLoadedPeers() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        GlobalPos endpoint = GlobalPos.of(serverLevel.dimension(), worldPosition);
        for (Connection connection : connections()) {
            GlobalPos peer = connection.endpoint();
            if (!serverLevel.dimension().equals(peer.dimension()) || !serverLevel.hasChunkAt(peer.pos())) continue;
            if (serverLevel.getBlockEntity(peer.pos()) instanceof NetworkInterfaceBlockEntity peerInterface) {
                peerInterface.removeConnection(endpoint);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        removeFromLoadedPeers();
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeGlobalPos(output.child(OWNER_KEY), owner);
        var serializedConnections = output.childrenList(CONNECTIONS_KEY);
        for (Connection connection : connections.values()) {
            ValueOutput serialized = serializedConnections.addChild();
            writeGlobalPos(serialized.child(ENDPOINT_KEY), connection.endpoint());
            serialized.putString(MACHINE_KEY, connection.machine().type().toString());
            serialized.putLong(HASH_KEY, connection.machine().hash());
            serialized.putLong(SEQUENCE_KEY, connection.sequence());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = readGlobalPos(input.childOrEmpty(OWNER_KEY));
        connections.clear();
        for (ValueInput serialized : input.childrenListOrEmpty(CONNECTIONS_KEY)) {
            try {
                GlobalPos endpoint = readGlobalPos(serialized.childOrEmpty(ENDPOINT_KEY));
                String machine = serialized.getStringOr(MACHINE_KEY, "");
                if (endpoint == null || machine.isBlank()
                        || serialized.getLong(HASH_KEY).isEmpty()
                        || serialized.getLong(SEQUENCE_KEY).isEmpty()) continue;
                long sequence = serialized.getLong(SEQUENCE_KEY).orElseThrow();
                if (sequence < 0L) continue;
                addConnection(new Connection(endpoint,
                        new MachineReference(Identifier.parse(machine), serialized.getLong(HASH_KEY).orElseThrow()),
                        sequence));
            } catch (RuntimeException ignored) {
                // Malformed connection records must not prevent the endpoint from loading.
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ValueInput input) {
        super.onDataPacket(net, input);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean valid(Connection connection) {
        return connection != null && connection.endpoint() != null && connection.machine() != null
                && connection.machine().type() != null && connection.sequence() >= 0L;
    }

    private static void writeGlobalPos(ValueOutput output, @Nullable GlobalPos pos) {
        if (pos == null) return;
        output.putString(DIMENSION_KEY, pos.dimension().identifier().toString());
        output.putInt(X_KEY, pos.pos().getX());
        output.putInt(Y_KEY, pos.pos().getY());
        output.putInt(Z_KEY, pos.pos().getZ());
    }

    private static @Nullable GlobalPos readGlobalPos(ValueInput input) {
        String dimension = input.getStringOr(DIMENSION_KEY, "");
        if (dimension.isBlank()) return null;
        Identifier dimensionId = Identifier.parse(dimension);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return GlobalPos.of(key, new BlockPos(
                input.getIntOr(X_KEY, 0), input.getIntOr(Y_KEY, 0), input.getIntOr(Z_KEY, 0)));
    }

    public record Connection(GlobalPos endpoint, MachineReference machine, long sequence) {
    }

    private record ConnectionKey(GlobalPos endpoint, MachineReference machine) {
    }
}

package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiServerSession;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owned capability handshake written from the actual newly created menu.
 * @author howxu <dev@howxu.cn>
 */
public record ControllerMenuOpenData(UUID sessionId, ResourceKey<Level> dimension, BlockPos pos,
                                     ResourceLocation machineId, Kind kind, Role role, boolean formed,
                                     int installedModuleCount, Optional<ResourceLocation> connectedHostId,
                                     List<Capability> capabilities) {
    private static final int MAX_CAPABILITIES = 64;

    public ControllerMenuOpenData {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(dimension, "dimension");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(connectedHostId, "connectedHostId");
        capabilities = List.copyOf(capabilities);
        if (installedModuleCount < 0 || capabilities.size() > MAX_CAPABILITIES) {
            throw new IllegalArgumentException("Invalid controller opening metadata");
        }
    }

    /** Capability IDs and versions, without author callbacks or codecs.
     * @author howxu <dev@howxu.cn>
     */
    public record Capability(ResourceLocation id, int version, boolean state) {
        public Capability {
            Objects.requireNonNull(id, "id");
            if (version <= 0) throw new IllegalArgumentException("version must be positive");
        }
    }

    public static ControllerMenuOpenData forOwner(@Nullable MachineControllerBlockEntity owner,
                                                  @Nullable Level level, Kind fallbackKind) {
        if (owner == null) return legacy(level, BlockPos.ZERO, null, null, 0, false, 0, fallbackKind);
        ControllerRuntimeSnapshot runtime = owner.runtimeSnapshot();
        ResourceLocation id = machineId(owner, runtime);
        Machine machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        if (machine == null) machine = MachineRegistry.getMachine(id);
        Kind kind = machine != null && machine.behavior() instanceof TickBehavior ? Kind.TICK
                : runtime.factoryControllerPresent() ? Kind.FACTORY : Kind.NORMAL;
        Role role = runtime.structure().machine() == null && runtime.structure().configuredMachine() == null
                && machine != null ? machine.isHost() ? Role.HOST : machine.isModule() ? Role.MODULE : Role.NORMAL
                : role(runtime.controllerRole());
        Level ownerLevel = owner.getLevel() == null ? level : owner.getLevel();
        return new ControllerMenuOpenData(UUID.randomUUID(), ownerLevel == null ? Level.OVERWORLD : ownerLevel.dimension(),
                owner.getBlockPos(), id, kind, role, runtime.structure().formed(),
                runtime.installedModuleCount(), runtime.moduleConnectionStatus().connected()
                ? Optional.ofNullable(runtime.moduleConnectionStatus().connectedHostId()) : Optional.empty(),
                ControllerUiServerSession.currentProtocols().capabilities(id).stream()
                        .map(value -> new Capability(value.id(), value.version(), value.state())).toList());
    }

    /** Resolve formed identity first; unformed controllers retain their configured or physical identity. */
    public static ResourceLocation machineId(MachineControllerBlockEntity owner, ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().formed() ? runtime.structure().machine() : null;
        if (machine != null) return machine.registryName();
        machine = runtime.structure().configuredMachine();
        if (machine != null) return machine.registryName();
        return ((MachineControllerBlock) owner.getBlockState().getBlock()).machineId();
    }

    static ControllerMenuOpenData legacy(@Nullable Level level, BlockPos pos, @Nullable ResourceLocation machineId,
                                          @Nullable ResourceLocation connectedHostId, int role, boolean formed,
                                          int modules, Kind kind) {
        return new ControllerMenuOpenData(UUID.randomUUID(), level == null ? Level.OVERWORLD : level.dimension(),
                pos, machineId == null ? MMCR.id("unknown") : machineId, kind, role(role), formed,
                Math.max(0, modules), Optional.ofNullable(connectedHostId), List.of());
    }

    private static Role role(int value) {
        return switch (value) {
            case 0 -> Role.NORMAL; case 1 -> Role.HOST; case 2 -> Role.MODULE;
            default -> throw new IllegalArgumentException("Invalid controller role");
        };
    }

    public static void write(FriendlyByteBuf buffer, ControllerMenuOpenData value) {
        buffer.writeUUID(value.sessionId());
        ResourceLocation.STREAM_CODEC.encode(buffer, value.dimension().location());
        buffer.writeBlockPos(value.pos());
        ResourceLocation.STREAM_CODEC.encode(buffer, value.machineId());
        buffer.writeEnum(value.kind());
        buffer.writeEnum(value.role());
        buffer.writeBoolean(value.formed());
        buffer.writeVarInt(value.installedModuleCount());
        buffer.writeBoolean(value.connectedHostId().isPresent());
        value.connectedHostId().ifPresent(id -> ResourceLocation.STREAM_CODEC.encode(buffer, id));
        buffer.writeVarInt(value.capabilities().size());
        for (Capability capability : value.capabilities()) {
            ResourceLocation.STREAM_CODEC.encode(buffer, capability.id());
            buffer.writeVarInt(capability.version());
            buffer.writeBoolean(capability.state());
        }
    }

    public static ControllerMenuOpenData read(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.STREAM_CODEC.decode(buffer));
        BlockPos pos = buffer.readBlockPos();
        ResourceLocation machineId = ResourceLocation.STREAM_CODEC.decode(buffer);
        Kind kind = buffer.readEnum(Kind.class);
        Role role = buffer.readEnum(Role.class);
        boolean formed = buffer.readBoolean();
        int modules = buffer.readVarInt();
        Optional<ResourceLocation> host = buffer.readBoolean()
                ? Optional.of(ResourceLocation.STREAM_CODEC.decode(buffer)) : Optional.empty();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_CAPABILITIES) throw new IllegalArgumentException("Invalid capability count");
        var capabilities = new ArrayList<Capability>(count);
        for (int i = 0; i < count; i++) {
            capabilities.add(new Capability(ResourceLocation.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new ControllerMenuOpenData(sessionId, dimension, pos, machineId, kind, role, formed, modules, host, capabilities);
    }
}

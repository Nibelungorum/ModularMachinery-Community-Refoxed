package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-bound machine presentation state projected from one published runtime snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                     List<String> foundLevelIds, boolean recipeLocked, String lockedRecipeId,
                                     String machineId, int controllerRole, int installedModuleCount,
                                     boolean moduleConnected, String connectedHostId,
                                      CraftingStatus.Status craftingStatus, String craftingMessage,
                                      ExecutionStatus failure, boolean structureAreaLoaded, boolean redstonePaused,
                                      int tick, int totalTick, int parallelism, int maxParallelism,
                                      boolean factoryControllerPresent, int factoryThreadCount,
                                      int activeFactoryThreadCount, int parallelControllerCount,
                                      int maxParallelControllerCount, long totalStoredEnergy,
                                      long totalCapacityEnergy, FluidStack primaryFluid,
                                      FluidStack primaryOutputFluid)
        implements CustomPacketPayload {
    public static final int MAX_LEVEL_SNAPSHOTS = 1024;
    public static final int MAX_FAILURE_DETAIL_ENTRIES = 1024;
    public static final int MAX_INSTALLED_MODULES = 1024;
    private static final int MAX_STRING_LENGTH = 256;
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    public PktMachineStatePayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        lockedRecipeId = lockedRecipeId == null ? "" : lockedRecipeId;
        machineId = machineId == null ? "" : machineId;
        connectedHostId = connectedHostId == null ? "" : connectedHostId;
        craftingStatus = craftingStatus == null ? CraftingStatus.Status.IDLE : craftingStatus;
        craftingMessage = craftingMessage == null ? "" : craftingMessage;
        primaryFluid = primaryFluid == null ? FluidStack.EMPTY : primaryFluid.copy();
        primaryOutputFluid = primaryOutputFluid == null ? FluidStack.EMPTY : primaryOutputFluid.copy();
        if (installedModuleCount < 0 || installedModuleCount > MAX_INSTALLED_MODULES
                || tick < 0 || totalTick < 0 || tick > totalTick || parallelism < 0 || maxParallelism < 1
                || factoryThreadCount < 0 || activeFactoryThreadCount < 0 || parallelControllerCount < 0
                || maxParallelControllerCount < 0 || totalStoredEnergy < 0L || totalCapacityEnergy < 0L) {
            throw new IllegalArgumentException("Invalid machine presentation progress");
        }
    }

    public FluidStack primaryFluid() {
        return primaryFluid.copy();
    }

    public FluidStack primaryOutputFluid() {
        return primaryOutputFluid.copy();
    }

    public static PktMachineStatePayload from(BlockPos pos, ControllerRuntimeSnapshot runtime) {
        MachineStateSnapshot machineState = SYNC_RUNTIME.machineState(runtime);
        return new PktMachineStatePayload(pos, machineState.activeRecipe(), machineState.formed(), machineState.active(),
                machineState.foundLevelIds(), machineState.recipeLocked(), machineState.lockedRecipeId(),
                machineState.machineId(), machineState.controllerRole(), machineState.installedModuleCount(),
                machineState.moduleConnected(), machineState.connectedHostId(), machineState.craftingStatus(),
                machineState.craftingMessage(), machineState.failure(), machineState.structureAreaLoaded(),
                machineState.redstonePaused(),
                machineState.tick(), machineState.totalTick(), machineState.parallelism(), machineState.maxParallelism(),
                machineState.factoryControllerPresent(), machineState.factoryThreadCount(),
                machineState.activeFactoryThreadCount(), machineState.parallelControllerCount(),
                machineState.maxParallelControllerCount(), machineState.totalStoredEnergy(),
                machineState.totalCapacityEnergy(), machineState.primaryFluid(), machineState.primaryOutputFluid());
    }

    public static boolean stateChanged(PktMachineStatePayload current, PktMachineStatePayload previous) {
        return !current.pos.equals(previous.pos)
                || current.formed != previous.formed
                || current.active != previous.active
                || !Objects.equals(current.recipeName, previous.recipeName)
                || !current.foundLevelIds.equals(previous.foundLevelIds)
                || current.recipeLocked != previous.recipeLocked
                || !current.lockedRecipeId.equals(previous.lockedRecipeId)
                || !current.machineId.equals(previous.machineId)
                || current.controllerRole != previous.controllerRole
                || current.installedModuleCount != previous.installedModuleCount
                || current.moduleConnected != previous.moduleConnected
                || !current.connectedHostId.equals(previous.connectedHostId)
                || current.craftingStatus != previous.craftingStatus
                || !current.craftingMessage.equals(previous.craftingMessage)
                || !Objects.equals(current.failure, previous.failure)
                || current.structureAreaLoaded != previous.structureAreaLoaded
                || current.redstonePaused != previous.redstonePaused
                || current.tick != previous.tick
                || current.totalTick != previous.totalTick
                || current.parallelism != previous.parallelism
                || current.maxParallelism != previous.maxParallelism
                || current.factoryControllerPresent != previous.factoryControllerPresent
                || current.factoryThreadCount != previous.factoryThreadCount
                || current.activeFactoryThreadCount != previous.activeFactoryThreadCount
                || current.parallelControllerCount != previous.parallelControllerCount
                || current.maxParallelControllerCount != previous.maxParallelControllerCount
                || current.totalStoredEnergy != previous.totalStoredEnergy
                || current.totalCapacityEnergy != previous.totalCapacityEnergy
                || !FluidStack.matches(current.primaryFluid, previous.primaryFluid)
                || !FluidStack.matches(current.primaryOutputFluid, previous.primaryOutputFluid);
    }

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.of(PktMachineStatePayload::write, PktMachineStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktMachineStatePayload payload) {
        if (payload.foundLevelIds.size() > MAX_LEVEL_SNAPSHOTS) {
            throw new IllegalArgumentException("Invalid machine level count: " + payload.foundLevelIds.size());
        }
        if (payload.failure != null && payload.failure.details().size() > MAX_FAILURE_DETAIL_ENTRIES) {
            throw new IllegalArgumentException("Invalid failure detail count: " + payload.failure.details().size());
        }
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.recipeName, MAX_STRING_LENGTH);
        buf.writeBoolean(payload.formed);
        buf.writeBoolean(payload.active);
        buf.writeVarInt(payload.foundLevelIds.size());
        for (String id : payload.foundLevelIds) buf.writeUtf(id, MAX_STRING_LENGTH);
        buf.writeBoolean(payload.recipeLocked);
        buf.writeUtf(payload.lockedRecipeId, MAX_STRING_LENGTH);
        buf.writeUtf(payload.machineId, MAX_STRING_LENGTH);
        buf.writeVarInt(payload.controllerRole);
        buf.writeVarInt(payload.installedModuleCount);
        buf.writeBoolean(payload.moduleConnected);
        buf.writeUtf(payload.connectedHostId, MAX_STRING_LENGTH);
        buf.writeVarInt(payload.craftingStatus.ordinal());
        buf.writeUtf(payload.craftingMessage, MAX_STRING_LENGTH);
        writeFailure(buf, payload.failure);
        buf.writeBoolean(payload.structureAreaLoaded);
        buf.writeBoolean(payload.redstonePaused);
        buf.writeVarInt(payload.tick);
        buf.writeVarInt(payload.totalTick);
        buf.writeVarInt(payload.parallelism);
        buf.writeVarInt(payload.maxParallelism);
        buf.writeBoolean(payload.factoryControllerPresent);
        buf.writeVarInt(payload.factoryThreadCount);
        buf.writeVarInt(payload.activeFactoryThreadCount);
        buf.writeVarInt(payload.parallelControllerCount);
        buf.writeVarInt(payload.maxParallelControllerCount);
        buf.writeLong(payload.totalStoredEnergy);
        buf.writeLong(payload.totalCapacityEnergy);
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.primaryFluid);
        FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.primaryOutputFluid);
    }

    private static PktMachineStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String recipeName = buf.readUtf(MAX_STRING_LENGTH);
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int levelCount = buf.readVarInt();
        if (levelCount < 0 || levelCount > MAX_LEVEL_SNAPSHOTS) throw new IllegalArgumentException("Invalid machine level count");
        List<String> foundLevelIds = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) foundLevelIds.add(buf.readUtf(MAX_STRING_LENGTH));
        boolean recipeLocked = buf.readBoolean();
        String lockedRecipeId = buf.readUtf(MAX_STRING_LENGTH);
        String machineId = buf.readUtf(MAX_STRING_LENGTH);
        int controllerRole = buf.readVarInt();
        int installedModuleCount = buf.readVarInt();
        if (installedModuleCount < 0 || installedModuleCount > MAX_INSTALLED_MODULES) {
            throw new IllegalArgumentException("Invalid installed module count");
        }
        boolean moduleConnected = buf.readBoolean();
        String connectedHostId = buf.readUtf(MAX_STRING_LENGTH);
        CraftingStatus.Status status = readEnum(CraftingStatus.Status.values(), buf.readVarInt(), "crafting status");
        String craftingMessage = buf.readUtf(MAX_STRING_LENGTH);
        ExecutionStatus failure = readFailure(buf);
        boolean structureAreaLoaded = buf.readBoolean();
        boolean redstonePaused = buf.readBoolean();
        return new PktMachineStatePayload(pos, recipeName, formed, active, foundLevelIds,
                recipeLocked, lockedRecipeId, machineId, controllerRole, installedModuleCount,
                moduleConnected, connectedHostId, status, craftingMessage, failure, structureAreaLoaded,
                redstonePaused,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readLong(), buf.readLong(), FluidStack.OPTIONAL_STREAM_CODEC.decode(buf),
                FluidStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    private static void writeFailure(RegistryFriendlyByteBuf buf, ExecutionStatus failure) {
        buf.writeBoolean(failure != null);
        if (failure == null) return;
        buf.writeUtf(failure.id().toString(), MAX_STRING_LENGTH);
        buf.writeVarInt(failure.severity().ordinal());
        buf.writeUtf(failure.source().toString(), MAX_STRING_LENGTH);
        buf.writeVarInt(failure.details().size());
        for (Map.Entry<String, String> detail : failure.details().entrySet()) {
            buf.writeUtf(detail.getKey(), MAX_STRING_LENGTH);
            buf.writeUtf(detail.getValue(), MAX_STRING_LENGTH);
        }
    }

    private static ExecutionStatus readFailure(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        Identifier id = Identifier.parse(buf.readUtf(MAX_STRING_LENGTH));
        StatusSeverity severity = readEnum(StatusSeverity.values(), buf.readVarInt(), "failure severity");
        Identifier source = Identifier.parse(buf.readUtf(MAX_STRING_LENGTH));
        int detailCount = buf.readVarInt();
        if (detailCount < 0 || detailCount > MAX_FAILURE_DETAIL_ENTRIES) throw new IllegalArgumentException("Invalid failure detail count");
        Map<String, String> details = new LinkedHashMap<>();
        for (int i = 0; i < detailCount; i++) details.put(buf.readUtf(MAX_STRING_LENGTH), buf.readUtf(MAX_STRING_LENGTH));
        return new ExecutionStatus(id, severity, source, details);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            if (player.level().getBlockEntity(pos) instanceof MachineControllerBlockEntity controller) {
                controller.applyClientState(recipeName, formed, active, foundLevelIds, recipeLocked, lockedRecipeId,
                        machineId.isEmpty() ? null : Identifier.parse(machineId), controllerRole, installedModuleCount,
                        moduleConnected, connectedHostId.isEmpty() ? null : Identifier.parse(connectedHostId),
                        new CraftingStatus(craftingStatus, craftingMessage), failure, structureAreaLoaded,
                        tick, totalTick, parallelism, maxParallelism);
            }
            if (player.containerMenu instanceof MachineControllerMenu menu && menu.controllerPos().equals(pos)) {
                menu.applyClientSnapshot(this);
            }
        });
    }

    private static <T> T readEnum(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid " + name + ": " + ordinal);
        return values[ordinal];
    }
}

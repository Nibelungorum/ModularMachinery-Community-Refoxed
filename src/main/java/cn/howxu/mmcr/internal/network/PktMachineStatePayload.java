package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.machine.Machine;
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
                                     ExecutionStatus failure, boolean structureAreaLoaded,
                                     int tick, int totalTick, int parallelism, int maxParallelism)
        implements CustomPacketPayload {
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
        if (tick < 0 || totalTick < 0 || tick > totalTick || parallelism < 0 || maxParallelism < 1) {
            throw new IllegalArgumentException("Invalid machine presentation progress");
        }
    }

    public static PktMachineStatePayload from(BlockPos pos, ControllerRuntimeSnapshot runtime) {
        MachineStateSnapshot machineState = SYNC_RUNTIME.machineState(runtime);
        String machineId = "";
        Machine configuredMachine = runtime.structure().configuredMachine();
        if (configuredMachine != null) machineId = configuredMachine.registryName().toString();
        String lockedRecipe = SYNC_RUNTIME.lockedRecipeId(runtime);
        return new PktMachineStatePayload(pos, SYNC_RUNTIME.activeRecipe(runtime),
                machineState.structure().formed(), SYNC_RUNTIME.active(runtime), SYNC_RUNTIME.foundLevelIds(runtime),
                SYNC_RUNTIME.recipeLocked(runtime), lockedRecipe, machineId, controllerRole(runtime),
                machineState.installedModuleCount(), machineState.moduleConnected(),
                machineState.moduleConnected() ? runtime.moduleConnectionStatus().connectedHostId().toString() : "",
                machineState.crafting().status().getStatus(), machineState.crafting().status().getUnlocMessage(),
                machineState.crafting().failure(), machineState.structure().structureAreaLoaded(),
                SYNC_RUNTIME.tick(runtime), SYNC_RUNTIME.totalTick(runtime), SYNC_RUNTIME.currentParallelism(runtime),
                SYNC_RUNTIME.maxParallelism(runtime));
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
                || current.tick != previous.tick
                || current.totalTick != previous.totalTick
                || current.parallelism != previous.parallelism
                || current.maxParallelism != previous.maxParallelism;
    }

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.of(PktMachineStatePayload::write, PktMachineStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktMachineStatePayload payload) {
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
        buf.writeVarInt(Math.max(0, payload.installedModuleCount));
        buf.writeBoolean(payload.moduleConnected);
        buf.writeUtf(payload.connectedHostId, MAX_STRING_LENGTH);
        buf.writeVarInt(payload.craftingStatus.ordinal());
        buf.writeUtf(payload.craftingMessage, MAX_STRING_LENGTH);
        writeFailure(buf, payload.failure);
        buf.writeBoolean(payload.structureAreaLoaded);
        buf.writeVarInt(payload.tick);
        buf.writeVarInt(payload.totalTick);
        buf.writeVarInt(payload.parallelism);
        buf.writeVarInt(payload.maxParallelism);
    }

    private static PktMachineStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String recipeName = buf.readUtf(MAX_STRING_LENGTH);
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int levelCount = buf.readVarInt();
        if (levelCount < 0 || levelCount > 1024) throw new IllegalArgumentException("Invalid machine level count");
        List<String> foundLevelIds = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) foundLevelIds.add(buf.readUtf(MAX_STRING_LENGTH));
        boolean recipeLocked = buf.readBoolean();
        String lockedRecipeId = buf.readUtf(MAX_STRING_LENGTH);
        String machineId = buf.readUtf(MAX_STRING_LENGTH);
        int controllerRole = buf.readVarInt();
        int installedModuleCount = buf.readVarInt();
        boolean moduleConnected = buf.readBoolean();
        String connectedHostId = buf.readUtf(MAX_STRING_LENGTH);
        CraftingStatus.Status status = CraftingStatus.Status.values()[buf.readVarInt()];
        String craftingMessage = buf.readUtf(MAX_STRING_LENGTH);
        ExecutionStatus failure = readFailure(buf);
        boolean structureAreaLoaded = buf.readBoolean();
        return new PktMachineStatePayload(pos, recipeName, formed, active, foundLevelIds,
                recipeLocked, lockedRecipeId, machineId, controllerRole, installedModuleCount,
                moduleConnected, connectedHostId, status, craftingMessage, failure, structureAreaLoaded,
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static int controllerRole(ControllerRuntimeSnapshot runtime) {
        Machine machine = runtime.structure().configuredMachine();
        if (machine == null) machine = runtime.structure().machine();
        if (machine == null) return 0;
        if (machine.isHost()) return 1;
        if (machine.isModule()) return 2;
        return 0;
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
        StatusSeverity severity = StatusSeverity.values()[buf.readVarInt()];
        Identifier source = Identifier.parse(buf.readUtf(MAX_STRING_LENGTH));
        int detailCount = buf.readVarInt();
        if (detailCount < 0 || detailCount > 1024) throw new IllegalArgumentException("Invalid failure detail count");
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
                menu.applyClientControllerState(machineId.isEmpty() ? null : Identifier.parse(machineId), controllerRole,
                        installedModuleCount, moduleConnected,
                        connectedHostId.isEmpty() ? null : Identifier.parse(connectedHostId));
            }
        });
    }
}

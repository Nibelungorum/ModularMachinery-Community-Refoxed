package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;

import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
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

public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                     List<String> foundLevelIds, boolean recipeLocked, String lockedRecipeId,
                                     String machineId, int controllerRole, int installedModuleCount,
                                     boolean moduleConnected, String connectedHostId,
                                     CraftingStatus.Status craftingStatus, String craftingMessage,
                                     ExecutionStatus failure, boolean structureAreaLoaded) implements CustomPacketPayload {

    public PktMachineStatePayload {
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        lockedRecipeId = lockedRecipeId == null ? "" : lockedRecipeId;
        machineId = machineId == null ? "" : machineId;
        connectedHostId = connectedHostId == null ? "" : connectedHostId;
        craftingStatus = craftingStatus == null ? CraftingStatus.Status.IDLE : craftingStatus;
        craftingMessage = craftingMessage == null ? "" : craftingMessage;
    }

    public static boolean stateChanged(PktMachineStatePayload current, PktMachineStatePayload previous) {
        return current.formed != previous.formed
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
                || current.structureAreaLoaded != previous.structureAreaLoaded;
    }

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.of(PktMachineStatePayload::write, PktMachineStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktMachineStatePayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.recipeName);
        buf.writeBoolean(payload.formed);
        buf.writeBoolean(payload.active);
        buf.writeVarInt(payload.foundLevelIds.size());
        for (String id : payload.foundLevelIds) buf.writeUtf(id);
        buf.writeBoolean(payload.recipeLocked);
        buf.writeUtf(payload.lockedRecipeId);
        buf.writeUtf(payload.machineId);
        buf.writeVarInt(payload.controllerRole);
        buf.writeVarInt(Math.max(0, payload.installedModuleCount));
        buf.writeBoolean(payload.moduleConnected);
        buf.writeUtf(payload.connectedHostId);
        buf.writeVarInt(payload.craftingStatus.ordinal());
        buf.writeUtf(payload.craftingMessage);
        writeFailure(buf, payload.failure);
        buf.writeBoolean(payload.structureAreaLoaded);
    }

    private static PktMachineStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String recipeName = buf.readUtf();
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int levelCount = buf.readVarInt();
        List<String> foundLevelIds = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) foundLevelIds.add(buf.readUtf());
        return new PktMachineStatePayload(pos, recipeName, formed, active, foundLevelIds,
                buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean(), buf.readUtf(), CraftingStatus.Status.values()[buf.readVarInt()], buf.readUtf(),
                readFailure(buf), buf.readBoolean());
    }

    private static void writeFailure(RegistryFriendlyByteBuf buf, ExecutionStatus failure) {
        buf.writeBoolean(failure != null);
        if (failure == null) return;
        buf.writeUtf(failure.id().toString());
        buf.writeVarInt(failure.severity().ordinal());
        buf.writeUtf(failure.source().toString());
        buf.writeVarInt(failure.details().size());
        for (Map.Entry<String, String> detail : failure.details().entrySet()) {
            buf.writeUtf(detail.getKey());
            buf.writeUtf(detail.getValue());
        }
    }

    private static ExecutionStatus readFailure(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        var id = Identifier.parse(buf.readUtf());
        var severity = StatusSeverity.values()[buf.readVarInt()];
        var source = Identifier.parse(buf.readUtf());
        int detailCount = buf.readVarInt();
        Map<String, String> details = new LinkedHashMap<>();
        for (int i = 0; i < detailCount; i++) details.put(buf.readUtf(), buf.readUtf());
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
                          new CraftingStatus(craftingStatus, craftingMessage), failure, structureAreaLoaded);
            }
            if (player.containerMenu instanceof MachineControllerMenu menu
                    && menu.controllerPos().equals(pos)) {
                menu.applyClientControllerState(machineId.isEmpty() ? null : Identifier.parse(machineId), controllerRole,
                        installedModuleCount, moduleConnected,
                        connectedHostId.isEmpty() ? null : Identifier.parse(connectedHostId));
            }
        });
    }
}

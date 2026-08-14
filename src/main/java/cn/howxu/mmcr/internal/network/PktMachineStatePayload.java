package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                     List<String> foundLevelIds, boolean recipeLocked, String lockedRecipeId,
                                     String machineId, int controllerRole, int installedModuleCount,
                                     boolean moduleConnected, String connectedHostId) implements CustomPacketPayload {

    public static boolean stateChanged(boolean formed, boolean active, boolean recipeLocked, String lockedRecipeId,
                                       boolean lastFormed, boolean lastActive, boolean lastRecipeLocked,
                                       String lastLockedRecipeId) {
        return formed != lastFormed || active != lastActive || recipeLocked != lastRecipeLocked
                || !lockedRecipeId.equals(lastLockedRecipeId);
    }

    public static boolean stateChanged(boolean formed, boolean active, boolean recipeLocked, String lockedRecipeId,
                                       String machineId, int controllerRole, int installedModuleCount,
                                       boolean moduleConnected, String connectedHostId,
                                       boolean lastFormed, boolean lastActive, boolean lastRecipeLocked,
                                       String lastLockedRecipeId, String lastMachineId, int lastControllerRole,
                                       int lastInstalledModuleCount, boolean lastModuleConnected,
                                       String lastConnectedHostId) {
        return stateChanged(formed, active, recipeLocked, lockedRecipeId,
                lastFormed, lastActive, lastRecipeLocked, lastLockedRecipeId)
                || !machineId.equals(lastMachineId)
                || controllerRole != lastControllerRole
                || installedModuleCount != lastInstalledModuleCount
                || moduleConnected != lastModuleConnected
                || !connectedHostId.equals(lastConnectedHostId);
    }

    public PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                   List<String> foundLevelIds) {
        this(pos, recipeName, formed, active, foundLevelIds, false, "", "", 0, 0, false, "");
    }

    public PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                  List<String> foundLevelIds, boolean recipeLocked, String lockedRecipeId) {
        this(pos, recipeName, formed, active, foundLevelIds, recipeLocked, lockedRecipeId, "", 0, 0, false, "");
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
                buf.readBoolean(), buf.readUtf());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            if (player.level().getBlockEntity(pos) instanceof MachineControllerBlockEntity controller) {
                  controller.applyClientState(recipeName, formed, active, foundLevelIds, recipeLocked, lockedRecipeId);
            }
            if (player.containerMenu instanceof cn.howxu.mmcr.internal.menu.MachineControllerMenu menu
                    && menu.controllerPos().equals(pos)) {
                menu.applyClientControllerState(machineId.isEmpty() ? null : Identifier.parse(machineId), controllerRole,
                        installedModuleCount, moduleConnected,
                        connectedHostId.isEmpty() ? null : Identifier.parse(connectedHostId));
            }
        });
    }
}

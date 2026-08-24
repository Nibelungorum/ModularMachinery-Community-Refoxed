package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to toggle one server-owned factory recipe lock.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktRecipeLockPayload(BlockPos controllerPos, int threadIndex) implements CustomPacketPayload {
    public static final Type<PktRecipeLockPayload> TYPE = new Type<>(MMCR.id("recipe_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktRecipeLockPayload> STREAM_CODEC =
            StreamCodec.of(PktRecipeLockPayload::write, PktRecipeLockPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktRecipeLockPayload payload) {
        buf.writeBlockPos(payload.controllerPos);
        buf.writeVarInt(payload.threadIndex);
    }

    private static PktRecipeLockPayload read(RegistryFriendlyByteBuf buf) {
        return new PktRecipeLockPayload(buf.readBlockPos(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) toggleOnServer(player, this);
        });
    }

    static boolean toggleOnServer(ServerPlayer player, PktRecipeLockPayload payload) {
        if (player == null || payload == null || player.level().isClientSide()) return false;
        if (!(player.level().getBlockEntity(payload.controllerPos()) instanceof MachineControllerBlockEntity controller)) {
            return false;
        }
        if (!controller.runtimeSnapshot().structure().formed()
                || !MenuSupport.stillValidWithin(player, payload.controllerPos())) return false;
        if (!hasAccessToMenu(player, controller, payload.controllerPos())) return false;
        if (!controller.toggleFactoryRecipeLock(payload.threadIndex())) return false;
        controller.setChanged();
        controller.sendRecipeLockState(player);
        controller.sendFactoryControllerSnapshot(player);
        return true;
    }

    private static boolean hasAccessToMenu(ServerPlayer player, MachineControllerBlockEntity controller, BlockPos pos) {
        if (player.containerMenu instanceof FactoryControllerMenu menu) {
            return menu.controllerPos().equals(pos) && menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        if (player.containerMenu instanceof MachineControllerMenu menu) {
            return menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        return false;
    }
}

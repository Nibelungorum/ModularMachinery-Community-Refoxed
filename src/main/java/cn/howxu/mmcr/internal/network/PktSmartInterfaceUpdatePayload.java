package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.SmartInterfaceMenu;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client request to edit one binding of the currently open smart-interface menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktSmartInterfaceUpdatePayload(BlockPos pos, int bindingIndex, float value) implements CustomPacketPayload {
    public static final Type<PktSmartInterfaceUpdatePayload> TYPE = new Type<>(MMCR.id("smart_interface_update"));
    public static final StreamCodec<ByteBuf, PktSmartInterfaceUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktSmartInterfaceUpdatePayload::pos,
            ByteBufCodecs.VAR_INT, PktSmartInterfaceUpdatePayload::bindingIndex,
            ByteBufCodecs.FLOAT, PktSmartInterfaceUpdatePayload::value,
            PktSmartInterfaceUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!canUpdate(player.containerMenu, pos, bindingIndex, value)) return;
            if (!(player.level().getBlockEntity(pos) instanceof SmartInterfaceBlockEntity smartInterface)) return;
            if (smartInterface.binding(bindingIndex).isEmpty()) return;
            smartInterface.setValue(bindingIndex, value);
        });
    }

    static boolean canUpdate(AbstractContainerMenu menu, BlockPos pos, int bindingIndex, float value) {
        return menu instanceof SmartInterfaceMenu smartInterface
                && smartInterface.pos().equals(pos)
                && bindingIndex >= 0
                && Float.isFinite(value);
    }
}

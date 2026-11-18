package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.menu.PortMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client request to eject the contents of the currently open input port.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktEjectPortContentsPayload(BlockPos pos, Identifier capabilityId,
                                          boolean allResources) implements CustomPacketPayload {
    public static final Type<PktEjectPortContentsPayload> TYPE = new Type<>(MMCR.id("eject_port_contents"));
    public static final StreamCodec<ByteBuf, PktEjectPortContentsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktEjectPortContentsPayload::pos,
            Identifier.STREAM_CODEC, PktEjectPortContentsPayload::capabilityId,
            ByteBufCodecs.BOOL, PktEjectPortContentsPayload::allResources,
            PktEjectPortContentsPayload::new);

    public PktEjectPortContentsPayload(BlockPos pos, Identifier capabilityId) {
        this(pos, capabilityId, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ejectOnServer(player, this);
        });
    }

    public static boolean ejectOnServer(ServerPlayer player, PktEjectPortContentsPayload payload) {
        if (player == null || payload == null || payload.pos == null) return false;
        if (!(player.level().getBlockEntity(payload.pos) instanceof IOPortBlockEntity port)
                || port.ioType() != IOType.INPUT || payload.capabilityId == null) return false;
        if (!hasPortMenuAt(player.containerMenu, payload.pos, port)
                || !player.containerMenu.stillValid(player)
                || !MenuSupport.stillValidWithin(player, payload.pos)) return false;
        CapabilityType type = new CapabilityType(payload.capabilityId);
        var capability = port.capability(type);
        return capability != null && capability.directions().supports(IOType.INPUT)
                && (payload.allResources ? port.ejectContents(type, true) : port.ejectContents(type));
    }

    private static boolean hasPortMenuAt(AbstractContainerMenu menu, BlockPos pos, IOPortBlockEntity port) {
        return menu instanceof PortMenu opened && pos.equals(opened.pos()) && opened.owner() == port;
    }
}

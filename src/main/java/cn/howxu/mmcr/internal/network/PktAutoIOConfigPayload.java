package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Client request to mutate Auto IO state on the currently open port menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktAutoIOConfigPayload(BlockPos pos, AutoIOAction action, @Nullable Direction side, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<PktAutoIOConfigPayload> TYPE = new Type<>(MMCR.id("auto_io_config"));
    public static final StreamCodec<ByteBuf, PktAutoIOConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktAutoIOConfigPayload::pos,
            ByteBufCodecs.idMapper(index -> AutoIOAction.values()[index], AutoIOAction::ordinal), PktAutoIOConfigPayload::action,
            ByteBufCodecs.optional(ByteBufCodecs.idMapper(index -> Direction.values()[index], Direction::ordinal)),
            payload -> Optional.ofNullable(payload.side),
            ByteBufCodecs.BOOL, PktAutoIOConfigPayload::enabled,
            (pos, action, side, enabled) -> new PktAutoIOConfigPayload(pos, action, side.orElse(null), enabled));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!canUpdate(player.containerMenu, pos, action, side)) return;
            if (!(player.level().getBlockEntity(pos) instanceof IOPortBlockEntity port)) return;
            if (action == AutoIOAction.SET_ENABLED) port.setAutoIOEnabled(enabled);
            else if (action == AutoIOAction.SET_SIDE) port.setAutoIOSide(side, enabled);
            else if (action == AutoIOAction.SET_ALL_SIDES) port.setAllAutoIOSides(enabled);
        });
    }

    public static boolean canUpdate(AbstractContainerMenu menu, BlockPos pos, AutoIOAction action, @Nullable Direction side) {
        if (pos == null || action == null) return false;
        boolean portMenu = menu instanceof ItemBusMenu itemBus && itemBus.pos().equals(pos)
                || menu instanceof FluidHatchMenu fluidHatch && fluidHatch.pos().equals(pos)
                || menu instanceof EnergyHatchMenu energyHatch && energyHatch.pos().equals(pos);
        if (!portMenu) return false;
        return action != AutoIOAction.SET_SIDE || side != null;
    }
}

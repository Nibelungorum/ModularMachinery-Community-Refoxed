package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.menu.EnergyHatchMenu;
import cn.howxu.mmcr.internal.menu.ExtendedCombinedMenu;
import cn.howxu.mmcr.internal.menu.ExtendedFluidMenu;
import cn.howxu.mmcr.internal.menu.ExtendedItemMenu;
import cn.howxu.mmcr.internal.menu.FluidHatchMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
public record PktAutoIOConfigPayload(BlockPos pos, Identifier capabilityId, AutoIOAction action,
                                    @Nullable Direction side, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<PktAutoIOConfigPayload> TYPE = new Type<>(MMCR.id("auto_io_config"));
    public static final StreamCodec<ByteBuf, PktAutoIOConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktAutoIOConfigPayload::pos,
            Identifier.STREAM_CODEC, PktAutoIOConfigPayload::capabilityId,
            ByteBufCodecs.idMapper(index -> readEnum(AutoIOAction.values(), index), AutoIOAction::ordinal), PktAutoIOConfigPayload::action,
            ByteBufCodecs.optional(ByteBufCodecs.idMapper(index -> readEnum(Direction.values(), index), Direction::ordinal)),
            payload -> Optional.ofNullable(payload.side),
            ByteBufCodecs.BOOL, PktAutoIOConfigPayload::enabled,
            (pos, capabilityId, action, side, enabled) -> new PktAutoIOConfigPayload(pos, capabilityId, action,
                    side.orElse(null), enabled));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!canUpdate(player, pos, capabilityId, action, side)) return;
            if (!(player.level().getBlockEntity(pos) instanceof IOPortBlockEntity port)) return;
            if (!ownsMenu(player.containerMenu, port)) return;
            CapabilityType type = new CapabilityType(capabilityId);
            if (action == AutoIOAction.SET_ENABLED) port.setAutoIOEnabled(type, enabled);
            else if (action == AutoIOAction.SET_SIDE) port.setAutoIOSide(type, side, enabled);
            else if (action == AutoIOAction.SET_ALL_SIDES) port.setAllAutoIOSides(type, enabled);
        });
    }

    public static boolean canUpdate(ServerPlayer player, BlockPos pos, AutoIOAction action, @Nullable Direction side) {
        if (player == null || pos == null || action == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        boolean portMenu = menu instanceof ItemBusMenu itemBus && itemBus.pos().equals(pos)
                || menu instanceof FluidHatchMenu fluidHatch && fluidHatch.pos().equals(pos)
                || menu instanceof EnergyHatchMenu energyHatch && energyHatch.pos().equals(pos)
                || menu instanceof ExtendedItemMenu extendedItem && extendedItem.pos().equals(pos)
                || menu instanceof ExtendedFluidMenu extendedFluid && extendedFluid.pos().equals(pos)
                || menu instanceof CombinedPortMenu combined && combined.pos().equals(pos)
                || menu instanceof ExtendedCombinedMenu extendedCombined && extendedCombined.pos().equals(pos);
        if (!portMenu) return false;
        return menu.stillValid(player) && (action != AutoIOAction.SET_SIDE || side != null);
    }

    public static boolean canUpdate(ServerPlayer player, BlockPos pos, Identifier capabilityId,
                                    AutoIOAction action, @Nullable Direction side) {
        if (!canUpdate(player, pos, action, side) || capabilityId == null || player.level() == null) return false;
        if (!(player.level().getBlockEntity(pos) instanceof IOPortBlockEntity port)) return false;
        CapabilityType type = new CapabilityType(capabilityId);
        var capability = port.capability(type);
        return capability != null && capability.ioType() == port.ioType();
    }

    static boolean ownsMenu(AbstractContainerMenu menu, IOPortBlockEntity port) {
        return menu instanceof ItemBusMenu itemBus && itemBus.owner() == port
                || menu instanceof FluidHatchMenu fluidHatch && fluidHatch.owner() == port
                || menu instanceof EnergyHatchMenu energyHatch && energyHatch.owner() == port
                || menu instanceof ExtendedItemMenu extendedItem && extendedItem.owner() == port
                || menu instanceof ExtendedFluidMenu extendedFluid && extendedFluid.owner() == port
                || menu instanceof CombinedPortMenu combined && combined.owner() == port
                || menu instanceof ExtendedCombinedMenu extendedCombined && extendedCombined.owner() == port;
    }

    private static <T> T readEnum(T[] values, int index) {
        if (index < 0 || index >= values.length) throw new IllegalArgumentException("Invalid enum ordinal: " + index);
        return values[index];
    }
}

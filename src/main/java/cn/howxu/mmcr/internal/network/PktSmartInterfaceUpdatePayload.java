package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
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

import java.util.Optional;

/**
 * Client request to edit one parameter of the currently open smart-interface menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktSmartInterfaceUpdatePayload(BlockPos pos, String interfaceType, float value) implements CustomPacketPayload {
    public static final Type<PktSmartInterfaceUpdatePayload> TYPE = new Type<>(MMCR.id("smart_interface_update"));
    public static final StreamCodec<ByteBuf, PktSmartInterfaceUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktSmartInterfaceUpdatePayload::pos,
            ByteBufCodecs.STRING_UTF8, PktSmartInterfaceUpdatePayload::interfaceType,
            ByteBufCodecs.FLOAT, PktSmartInterfaceUpdatePayload::value,
            PktSmartInterfaceUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!canUpdate(player.containerMenu, pos, interfaceType, value)) return;
            if (!(player.level().getBlockEntity(pos) instanceof SmartInterfaceBlockEntity smartInterface)) return;
            var machineId = smartInterface.machineId().orElse(null);
            if (machineId == null) return;
            var registration = MachineDefinitions.getRegistration(machineId);
            if (registration == null) return;
            SmartInterfaceType type = registration.smartInterfaceTypes().get(interfaceType);
            validatedValue(type, value).ifPresent(validated -> smartInterface.setValue(interfaceType, validated));
        });
    }

    static boolean canUpdate(AbstractContainerMenu menu, BlockPos pos, String interfaceType, float value) {
        return menu instanceof SmartInterfaceMenu smartInterface
                && smartInterface.pos().equals(pos)
                && interfaceType != null
                && !interfaceType.isBlank()
                && Float.isFinite(value);
    }

    static boolean typeAccepts(SmartInterfaceType type, float value) {
        return type != null && type.accepts(value);
    }

    static Optional<Float> validatedValue(SmartInterfaceType type, float value) {
        return type == null ? Optional.empty() : Optional.of(type.validatedValue(value));
    }
}

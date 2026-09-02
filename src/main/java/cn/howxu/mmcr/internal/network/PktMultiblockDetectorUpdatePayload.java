package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.MultiblockDetectorSelection;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to update the persistent multiblock detector screen state.
 * @author howxu <dev@howxu.cn>
 */
public record PktMultiblockDetectorUpdatePayload(MultiblockDetectorSelection selection, boolean maskEnabled)
        implements CustomPacketPayload {
    public static final Type<PktMultiblockDetectorUpdatePayload> TYPE =
            new Type<>(MMCR.id("multiblock_detector_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMultiblockDetectorUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    MultiblockDetectorSelection.STREAM_CODEC, PktMultiblockDetectorUpdatePayload::selection,
                    ByteBufCodecs.BOOL, PktMultiblockDetectorUpdatePayload::maskEnabled,
                    PktMultiblockDetectorUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !canUpdate(player)) return;
            ItemStack stack = player.getMainHandItem();
            stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(), selection);
            if (maskEnabled) stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get(), true);
            else stack.remove(ModDataComponents.MULTIBLOCK_DETECTOR_MASK.get());
        });
    }

    static boolean canUpdate(ServerPlayer player) {
        return player != null && player.getMainHandItem().is(ModItems.MULTIBLOCK_DETECTOR.get());
    }
}

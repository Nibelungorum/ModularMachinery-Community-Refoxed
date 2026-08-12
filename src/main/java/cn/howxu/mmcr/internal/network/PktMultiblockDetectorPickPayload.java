package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-to-server packet for detector middle-click controller selection.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMultiblockDetectorPickPayload(BlockPos pos, Direction face) implements CustomPacketPayload {

    public static final Type<PktMultiblockDetectorPickPayload> TYPE = new Type<>(MMCR.id("multiblock_detector_pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PktMultiblockDetectorPickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PktMultiblockDetectorPickPayload::pos,
                    Direction.STREAM_CODEC, PktMultiblockDetectorPickPayload::face,
                    PktMultiblockDetectorPickPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack stack = detectorInHand(player);
            if (stack.isEmpty()) return;

            var selection = MultiblockDetectorItem.selection(stack).withController(pos, face);
            stack.set(ModDataComponents.MULTIBLOCK_DETECTOR_SELECTION.get(), selection);
            player.sendSystemMessage(Component.translatable("message.mmcr.multiblock_detector.controller_set",
                    pos.toShortString(), face.getSerializedName()));
        });
    }

    private static ItemStack detectorInHand(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.is(ModItems.MULTIBLOCK_DETECTOR.get())) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        return off.is(ModItems.MULTIBLOCK_DETECTOR.get()) ? off : ItemStack.EMPTY;
    }
}

package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.menu.MenuSupport;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client request to select one server-owned controller recipe pool.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktRecipePoolSelectPayload(BlockPos controllerPos, ResourceLocation recipePoolId)
        implements CustomPacketPayload {
    public static final Type<PktRecipePoolSelectPayload> TYPE = new Type<>(MMCR.id("recipe_pool_select"));
    public static final StreamCodec<ByteBuf, PktRecipePoolSelectPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PktRecipePoolSelectPayload::controllerPos,
            ResourceLocation.STREAM_CODEC, PktRecipePoolSelectPayload::recipePoolId,
            PktRecipePoolSelectPayload::new);

    public PktRecipePoolSelectPayload {
        if (controllerPos == null || recipePoolId == null) {
            throw new IllegalArgumentException("controllerPos and recipePoolId are required");
        }
        controllerPos = controllerPos.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) selectOnServer(player, this);
        });
    }

    static boolean selectOnServer(ServerPlayer player, PktRecipePoolSelectPayload payload) {
        if (player == null || payload == null || player.level().isClientSide()) return false;
        if (!(player.level().getBlockEntity(payload.controllerPos()) instanceof MachineControllerBlockEntity controller)
                || !MenuSupport.stillValidWithin(player, payload.controllerPos())
                || !hasAccessToMenu(player, controller, payload.controllerPos())) return false;
        return controller.selectRecipePool(payload.recipePoolId());
    }

    private static boolean hasAccessToMenu(ServerPlayer player, MachineControllerBlockEntity controller, BlockPos pos) {
        if (player.containerMenu instanceof FactoryControllerMenu menu) {
            return menu.controllerPos().equals(pos) && menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        if (player.containerMenu instanceof MachineControllerMenu menu) {
            return menu.controllerPos().equals(pos) && menu.resolvedOwner() == controller && menu.stillValid(player);
        }
        return false;
    }
}

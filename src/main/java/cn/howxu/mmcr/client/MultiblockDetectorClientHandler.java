package cn.howxu.mmcr.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.MultiblockDetectorScreen;
import cn.howxu.mmcr.internal.network.PktMultiblockDetectorPickPayload;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.util.ItemSpecialOperationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client input bridge for detector pick-block selection.
 *
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(modid = MMCR.MODID, value = Dist.CLIENT)
public final class MultiblockDetectorClientHandler {

    private MultiblockDetectorClientHandler() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        if (shouldOpenScreen(event.isUseItem(), event.getHand(), ItemSpecialOperationUtil.isSpecialOperated(minecraft.player), minecraft.screen != null,
                minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.MISS,
                minecraft.player.getMainHandItem().is(ModItems.MULTIBLOCK_DETECTOR.get()))) {
            minecraft.setScreen(new MultiblockDetectorScreen(
                    Component.translatable("gui.mmcr.multiblock_detector.title")));
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }

        if (!event.isPickBlock()) return;

        if (minecraft.hitResult == null) return;

        ItemStack held = minecraft.player.getItemInHand(event.getHand());
        if (!held.is(ModItems.MULTIBLOCK_DETECTOR.get())) {
            held = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);
            if (!held.is(ModItems.MULTIBLOCK_DETECTOR.get())) return;
        }

        if (minecraft.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) minecraft.hitResult;
        PacketDistributor.sendToServer(new PktMultiblockDetectorPickPayload(hit.getBlockPos(), hit.getDirection()));
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    static boolean shouldOpenScreen(boolean useItem, InteractionHand hand, boolean crouching,
                                    boolean hasScreen, boolean miss, boolean mainHandDetector) {
        return useItem && hand == InteractionHand.MAIN_HAND && !crouching && !hasScreen && miss && mainHandDetector;
    }
}

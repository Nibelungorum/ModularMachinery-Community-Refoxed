package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.common.helper.VecHelper;

/** Draws only real stored mana, independently of the native receiver projection.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaPortRenderer implements BlockEntityRenderer<ManaPortBlockEntity> {
    public ManaPortRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ManaPortBlockEntity pool, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var storage = pool.storage();
        if (storage.amount() == 0) return;
        float ratio = (float) storage.amount() / storage.capacity();
        float bottom = 2 / 16F + 0.001F;
        float height = bottom + ratio * (7 / 16F - bottom);
        // Resolve each frame so the atlas sprite always follows resource reloads.
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ManaPortAppearance.manaTexture());
        pose.pushPose();
        try {
            pose.translate(0, height, 0);
            pose.mulPose(VecHelper.rotateX(90F));
            RenderHelper.renderIconCropped(pose, buffers.getBuffer(RenderHelper.MANA_POOL_WATER),
                    2, 2, 14, 14, sprite, 0xFFFFFF, 1F, light);
        } finally {
            pose.popPose();
        }
    }
}

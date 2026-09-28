package org.nibelungorum.client;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRendersEvent;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderContext;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.nibelungorum.builtin.ARTIFICIAL_STAR;

/** Renders the GT LCore artificial-star model for the test controller.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ArtificialStarRenderer implements ControllerRenderer {
    public static final ArtificialStarRenderer INSTANCE = new ArtificialStarRenderer();
    private static final ResourceLocation STAR_MODEL_ID = ResourceLocation.fromNamespaceAndPath("mmcr_test", "obj/star");
    private static final ModelResourceLocation STAR_MODEL = ModelResourceLocation.standalone(STAR_MODEL_ID);

    private ArtificialStarRenderer() {
    }

    @SubscribeEvent
    public static void registerRenderer(MMCRMachineRendersEvent event) {
        event.register(ARTIFICIAL_STAR.ARTIFICIAL_STAR, INSTANCE);
    }

    @SubscribeEvent
    public static void registerModel(ModelEvent.RegisterAdditional event) {
        event.register(STAR_MODEL);
    }

    @Override
    public void render(ControllerRenderContext context, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!context.structure().formed() || Minecraft.getInstance().level == null) return;

        double x = 0.5;
        double y = 42.5;
        double z = 0.5;
        if (context.facing() != null) {
            switch (context.facing()) {
                case NORTH -> z = 39.5;
                case SOUTH -> z = -38.5;
                case WEST -> x = 39.5;
                case EAST -> x = -38.5;
                default -> {
                }
            }
        }

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(STAR_MODEL);

        float tick = Minecraft.getInstance().level.getGameTime() + context.partialTick();
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            poseStack.scale(0.45F, 0.45F, 0.45F);
            poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(0F, 1F, 1F, tick % 360F));
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    poseStack.last(), bufferSource.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)),
                    null, model, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, null);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}

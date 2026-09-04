package org.nibelungorum.client;

import cn.howxu.mmcr.api.publicapi.event.MMCRMachineRendersEvent;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderContext;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.joml.Quaternionf;
import org.nibelungorum.builtin.ARTIFICIAL_STAR;

import java.util.List;

/** Renders the GT LCore artificial-star model for the test controller.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class ArtificialStarRenderer implements ControllerRenderer {
    public static final ArtificialStarRenderer INSTANCE = new ArtificialStarRenderer();
    private static final Identifier STAR_MODEL_ID = Identifier.fromNamespaceAndPath("mmcr_test", "obj/star");
    private static final StandaloneModelKey<BlockStateModelPart> STAR_MODEL = new StandaloneModelKey<>(
            () -> STAR_MODEL_ID.toString());

    private ArtificialStarRenderer() {
    }

    @SubscribeEvent
    public static void registerRenderer(MMCRMachineRendersEvent event) {
        event.register(ARTIFICIAL_STAR.ARTIFICIAL_STAR, INSTANCE);
    }

    @SubscribeEvent
    public static void registerModel(ModelEvent.RegisterStandalone event) {
        event.register(STAR_MODEL, SimpleUnbakedStandaloneModel.simpleModelWrapper(STAR_MODEL_ID));
    }

    @Override
    public void render(ControllerRenderContext context, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState camera) {
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

        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(STAR_MODEL);
        if (model == null) return;

        float tick = Minecraft.getInstance().level.getGameTime() + context.partialTick();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(0.45F, 0.45F, 0.45F);
        poseStack.mulPose(new Quaternionf().fromAxisAngleDeg(0F, 1F, 1F, tick % 360F));
        nodeCollector.submitBlockModel(poseStack, RenderTypes.translucentMovingBlock(), List.of(model), new int[0],
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
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

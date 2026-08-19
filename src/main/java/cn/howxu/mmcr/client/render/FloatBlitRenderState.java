package cn.howxu.mmcr.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * GUI render state with explicit atlas UV coordinates.
 *
 * @author howxu <dev@howxu.cn>
 */
record FloatBlitRenderState(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        Matrix3x2f pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float u0,
        float u1,
        float v0,
        float v1,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds) implements GuiElementRenderState {

    FloatBlitRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float u0,
            float u1,
            float v0,
            float v1,
            int color,
            @Nullable ScreenRectangle scissorArea) {
        this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, u1, v0, v1, color, scissorArea,
                bounds(x0, y0, x1, y1, pose, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
        vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
        vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
        vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
    }

    @Nullable
    private static ScreenRectangle bounds(
            float x0, float y0, float x1, float y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle bounds = new ScreenRectangle(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1) - Mth.floor(x0), Mth.ceil(y1) - Mth.floor(y0))
                .transformMaxBounds(pose);
        return scissorArea == null ? bounds : scissorArea.intersection(bounds);
    }
}

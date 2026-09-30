package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewViewport;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Renders a preview scene into an isolated target and composites it into a GUI viewport.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneGuiRenderer implements AutoCloseable {
    private TextureTarget target;

    public void render(GuiGraphics graphics, PreviewViewport viewport, PreviewCamera camera,
            float partialTick, StructurePreviewRenderer owner) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        double pixelScale = minecraft.getWindow().getGuiScale() * owner.interactiveRenderScale();
        int targetWidth = Math.max(1, (int) Math.round(viewport.width() * pixelScale));
        int targetHeight = Math.max(1, (int) Math.round(viewport.height() * pixelScale));
        PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, viewport.width(), viewport.height());

        graphics.flush();
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        int previousReadFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] previousScissor = new int[4];
        if (scissorEnabled) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previousScissor);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        boolean matricesPushed = false;
        try {
            ensureTarget(targetWidth, targetHeight);
            RenderSystem.disableScissor();
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.backupProjectionMatrix();
            modelView.pushMatrix();
            matricesPushed = true;
            RenderSystem.setProjectionMatrix(sceneCamera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity().mul(sceneCamera.view());
            RenderSystem.applyModelViewMatrix();
            try (PreviewSceneBufferSource bufferSource = new PreviewSceneBufferSource(target.frameBufferId)) {
                PreviewSceneRenderContext context = new PreviewSceneRenderContext(new PoseStack(),
                        bufferSource, partialTick, target.frameBufferId);
                owner.renderScene(context, camera);
            }
        } finally {
            if (matricesPushed) {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
            restoreFramebuffers(previousReadFramebuffer, previousDrawFramebuffer);
            RenderSystem.viewport(previousViewport[0], previousViewport[1],
                    previousViewport[2], previousViewport[3]);
            if (scissorEnabled) {
                RenderSystem.enableScissor(
                        previousScissor[0], previousScissor[1], previousScissor[2], previousScissor[3]);
            } else {
                RenderSystem.disableScissor();
            }
            restoreDepth(depthEnabled);
            restoreBlend(blendEnabled);
            restoreCull(cullEnabled);
        }

        composite(viewport);
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != width || target.height != height) {
            int previousReadFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDrawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            target.resize(width, height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            restoreFramebuffers(previousReadFramebuffer, previousDrawFramebuffer);
        }
    }

    private void composite(PreviewViewport viewport) {
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int blendEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousTexture = RenderSystem.getShaderTexture(0);
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            Matrix4f identity = new Matrix4f();
            float x0 = viewport.x();
            float y0 = viewport.y();
            float x1 = x0 + viewport.width();
            float y1 = y0 + viewport.height();
            BufferBuilder builder = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(identity, x1, y1, 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(identity, x1, y0, 0.0F).setUv(1.0F, 1.0F);
            builder.addVertex(identity, x0, y0, 0.0F).setUv(0.0F, 1.0F);
            builder.addVertex(identity, x0, y1, 0.0F).setUv(0.0F, 0.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            RenderSystem.blendEquation(blendEquation);
            restoreDepth(depthEnabled);
            restoreBlend(blendEnabled);
            restoreCull(cullEnabled);
        }
    }

    private static void restoreFramebuffers(int readFramebuffer, int drawFramebuffer) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    }

    private static void restoreDepth(boolean enabled) {
        if (enabled) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
    }

    private static void restoreBlend(boolean enabled) {
        if (enabled) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
    }

    private static void restoreCull(boolean enabled) {
        if (enabled) RenderSystem.enableCull();
        else RenderSystem.disableCull();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (target == null) return;
        target.destroyBuffers();
        target = null;
    }
}

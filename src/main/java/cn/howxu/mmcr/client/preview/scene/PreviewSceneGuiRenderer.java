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
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
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
        GlStateBackup previousState = new GlStateBackup();
        RenderSystem.backupGlState(previousState);
        int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        ShaderInstance previousShader = RenderSystem.getShader();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        float[] previousShaderColor = RenderSystem.getShaderColor().clone();
        Matrix4f previousTextureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        float[] previousClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        double previousClearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] previousShaderTextures = new int[12];
        int[] previousTextureBindings = new int[12];
        for (int unit = 0; unit < previousShaderTextures.length; unit++) {
            previousShaderTextures[unit] = RenderSystem.getShaderTexture(unit);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            previousTextureBindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        RenderSystem.activeTexture(previousActiveTexture);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        boolean matricesPushed = false;
        try {
            try {
                graphics.flush();
                ensureTarget(targetWidth, targetHeight);
                RenderSystem.disableScissor();
                target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                modelView.pushMatrix();
                matricesPushed = true;
                RenderSystem.setProjectionMatrix(sceneCamera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
                modelView.identity().mul(sceneCamera.view());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                try (PreviewSceneBufferSource bufferSource = new PreviewSceneBufferSource(target.frameBufferId)) {
                    PreviewSceneRenderContext context = new PreviewSceneRenderContext(new PoseStack(),
                            bufferSource, partialTick, target.frameBufferId);
                    owner.renderScene(context, camera);
                }
            } finally {
                if (matricesPushed) {
                    modelView.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
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
        } finally {
            // The caller can be EMI's screenshot target, not Minecraft's main window.
            restoreFramebuffers(previousReadFramebuffer, previousDrawFramebuffer);
            RenderSystem.viewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
            RenderSystem.restoreGlState(previousState);
            if (scissorEnabled) RenderSystem.enableScissor(previousScissor[0], previousScissor[1],
                    previousScissor[2], previousScissor[3]);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            ShaderInstance currentShader = RenderSystem.getShader();
            if (currentShader != null) currentShader.clear();
            RenderSystem.setShader(() -> previousShader);
            GlStateManager._glUseProgram(previousProgram);
            RenderSystem.setShaderColor(previousShaderColor[0], previousShaderColor[1],
                    previousShaderColor[2], previousShaderColor[3]);
            RenderSystem.clearColor(previousClearColor[0], previousClearColor[1],
                    previousClearColor[2], previousClearColor[3]);
            RenderSystem.clearDepth(previousClearDepth);
            RenderSystem.setTextureMatrix(previousTextureMatrix);
            for (int unit = 0; unit < previousShaderTextures.length; unit++) {
                RenderSystem.setShaderTexture(unit, previousShaderTextures[unit]);
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                RenderSystem.bindTexture(previousTextureBindings[unit]);
            }
            RenderSystem.activeTexture(previousActiveTexture);
        }
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
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
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
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
        TextureTarget disposed = target;
        target = null;
        int readFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            disposed.destroyBuffers();
        } finally {
            restoreFramebuffers(readFramebuffer, drawFramebuffer);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.bindTexture(textureBinding);
        }
    }
}

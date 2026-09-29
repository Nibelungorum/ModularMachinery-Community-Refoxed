package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewViewport;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Renders a preview scene directly into an absolute GUI viewport.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneGuiRenderer implements AutoCloseable {
    private TextureTarget depthBackup;

    public void render(GuiGraphics graphics, PreviewViewport viewport, PreviewCamera camera,
            float partialTick, StructurePreviewRenderer owner) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft.getWindow().getGuiScale();
        int framebufferX = (int) Math.round(viewport.x() * guiScale);
        int framebufferY = (int) Math.round(minecraft.getWindow().getHeight()
                - (viewport.y() + viewport.height()) * guiScale);
        int framebufferWidth = Math.max(1, (int) Math.round(viewport.width() * guiScale));
        int framebufferHeight = Math.max(1, (int) Math.round(viewport.height() * guiScale));
        PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, viewport.width(), viewport.height());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();

        graphics.flush();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.x() + viewport.width(),
                viewport.y() + viewport.height());
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);
        int previousReadFramebuffer = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean depthBackedUp = false;
        boolean matricesPushed = false;
        try {
            ensureDepthBackup(framebufferWidth, framebufferHeight);
            copyDepth(previousDrawFramebuffer, framebufferX, framebufferY,
                    framebufferX + framebufferWidth, framebufferY + framebufferHeight,
                    depthBackup.frameBufferId, 0, 0, framebufferWidth, framebufferHeight);
            depthBackedUp = true;
            restoreFramebuffers(previousReadFramebuffer, previousDrawFramebuffer);
            RenderSystem.backupProjectionMatrix();
            modelView.pushMatrix();
            matricesPushed = true;
            RenderSystem.viewport(framebufferX, framebufferY, framebufferWidth, framebufferHeight);
            RenderSystem.clear(256, Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(sceneCamera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity().mul(sceneCamera.view());
            RenderSystem.applyModelViewMatrix();
            PreviewSceneRenderContext context = new PreviewSceneRenderContext(new PoseStack(),
                    minecraft.renderBuffers().bufferSource(), partialTick, previousDrawFramebuffer);
            try {
                owner.renderScene(context, camera);
            } finally {
                context.bufferSource().endBatch();
            }
        } finally {
            try {
                if (matricesPushed) {
                    modelView.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.restoreProjectionMatrix();
                }
            } finally {
                try {
                    if (depthBackedUp) {
                        copyDepth(depthBackup.frameBufferId, 0, 0, framebufferWidth, framebufferHeight,
                                previousDrawFramebuffer, framebufferX, framebufferY,
                                framebufferX + framebufferWidth, framebufferY + framebufferHeight);
                    }
                } finally {
                    try {
                        restoreFramebuffers(previousReadFramebuffer, previousDrawFramebuffer);
                    } finally {
                        try {
                            RenderSystem.viewport(previousViewport[0], previousViewport[1],
                                    previousViewport[2], previousViewport[3]);
                        } finally {
                            graphics.disableScissor();
                        }
                    }
                }
            }
        }
    }

    private void ensureDepthBackup(int width, int height) {
        if (depthBackup == null) {
            depthBackup = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        } else if (depthBackup.width != width || depthBackup.height != height) {
            depthBackup.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void copyDepth(int sourceFramebuffer, int sourceX0, int sourceY0, int sourceX1, int sourceY1,
            int targetFramebuffer, int targetX0, int targetY0, int targetX1, int targetY1) {
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissor = new int[4];
        if (scissorEnabled) GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        RenderSystem.disableScissor();
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            GL30.glBlitFramebuffer(sourceX0, sourceY0, sourceX1, sourceY1,
                    targetX0, targetY0, targetX1, targetY1, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            if (scissorEnabled) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        }
    }

    private static void restoreFramebuffers(int readFramebuffer, int drawFramebuffer) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (depthBackup == null) return;
        depthBackup.destroyBuffers();
        depthBackup = null;
    }
}

package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewViewport;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4fStack;

/**
 * Renders a preview scene directly into an absolute GUI viewport.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewSceneGuiRenderer {
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
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.viewport(framebufferX, framebufferY, framebufferWidth, framebufferHeight);
            RenderSystem.clear(256, Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(sceneCamera.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity().mul(sceneCamera.view());
            RenderSystem.applyModelViewMatrix();
            PreviewSceneRenderContext context = new PreviewSceneRenderContext(new PoseStack(),
                    minecraft.renderBuffers().bufferSource(), partialTick);
            try {
                owner.renderScene(context, camera);
            } finally {
                context.bufferSource().endBatch();
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
            graphics.disableScissor();
        }
    }
}

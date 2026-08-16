/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Vanilla PiP target used to render cached preview geometry off-screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewScenePictureInPictureRenderer extends PictureInPictureRenderer<PreviewSceneRenderState> {
    private GpuTexture colorTexture;
    private GpuTextureView colorTextureView;
    private GpuTexture depthTexture;
    private GpuTextureView depthTextureView;
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionMatrixBuffer = new ProjectionMatrixBuffer("MMCR structure preview");
    private PreviewSceneCamera preparedCamera;
    public PreviewScenePictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<PreviewSceneRenderState> getRenderStateClass() {
        return PreviewSceneRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "MMCR structure preview";
    }

    @Override
    public void prepare(PreviewSceneRenderState state, GuiRenderState guiRenderState, int guiScale) {
        int width = Math.max(1, state.x1() - state.x0()) * guiScale;
        int height = Math.max(1, state.y1() - state.y0()) * guiScale;
        ensureTargets(width, height);
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.outputColorTextureOverride = colorTextureView;
            RenderSystem.outputDepthTextureOverride = depthTextureView;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0D);
            projection.setupOrtho(-1000.0F, 1000.0F, width, height, true);
            RenderSystem.setProjectionMatrix(projectionMatrixBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            PoseStack poseStack = new PoseStack();
            poseStack.translate(width / 2.0F, height, 0.0F);
            float scale = guiScale * state.scale();
            poseStack.scale(scale, scale, -scale);
            renderToTexture(state, poseStack);
            bufferSource.endBatch();
            blitTexture(state, guiRenderState);
            PreviewSceneCamera camera = preparedCamera;
            if (camera != null) {
                state.owner().onPictureInPicturePrepared(depthTexture, camera,
                        state.mouseX(), state.mouseY(), state.frame());
            }
        } finally {
            preparedCamera = null;
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void ensureTargets(int width, int height) {
        if (colorTexture != null && colorTexture.getWidth(0) == width && colorTexture.getHeight(0) == height) return;
        releaseTargets();
        var device = RenderSystem.getDevice();
        colorTexture = device.createTexture(() -> "MMCR structure preview color",
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST, TextureFormat.RGBA8, width, height, 1, 1);
        colorTextureView = device.createTextureView(colorTexture);
        depthTexture = device.createTexture(() -> "MMCR structure preview depth",
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                Minecraft.getInstance().getMainRenderTarget().getDepthTexture().getFormat(), width, height, 1, 1);
        depthTextureView = device.createTextureView(depthTexture);
    }

    private void releaseTargets() {
        if (colorTextureView != null) colorTextureView.close();
        if (depthTextureView != null) depthTextureView.close();
        if (colorTexture != null) colorTexture.close();
        if (depthTexture != null) depthTexture.close();
        colorTexture = null;
        colorTextureView = null;
        depthTexture = null;
        depthTextureView = null;
    }

    @Override
    protected void blitTexture(PreviewSceneRenderState state, GuiRenderState guiRenderState) {
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(colorTextureView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(), state.x0(), state.y0(), state.x1(), state.y1(), 0.0F, 1.0F, 1.0F, 0.0F, -1,
                state.scissorArea(), null));
    }

    @Override
    public void close() {
        super.close();
        releaseTargets();
        projectionMatrixBuffer.close();
    }

    @Override
    protected void renderToTexture(PreviewSceneRenderState state, PoseStack poseStack) {
        PreviewSceneCamera camera = PreviewSceneCamera.from(state.camera(),
                Math.max(1, state.x1() - state.x0()), Math.max(1, state.y1() - state.y0()));
        Minecraft minecraft = Minecraft.getInstance();
        CameraRenderState cameraState = new CameraRenderState();
        cameraState.pos = new net.minecraft.world.phys.Vec3(camera.eye());
        cameraState.projectionMatrix = camera.projection();
        cameraState.viewRotationMatrix = camera.viewRotation();
        PreviewSceneRenderContext context = new PreviewSceneRenderContext(poseStack,
                minecraft.gameRenderer.getFeatureRenderDispatcher().getSubmitNodeStorage(),
                minecraft.renderBuffers().bufferSource(), cameraState, state.partialTick());
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            modelView.mul(camera.view());
            PreviewSceneCameraContext.with(camera.viewRotation(), camera.projection(),
                    () -> state.owner().renderScene(context, state.camera()));
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
        preparedCamera = camera;
    }

}

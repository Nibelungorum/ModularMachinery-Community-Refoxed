/*
 * Copyright (c) Low-Drag-MC and contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Modified for MMCR, Minecraft 26.1.2 / NeoForge 26.1.2.84
 */
package cn.howxu.mmcr.client.preview.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import cn.howxu.mmcr.mixin.client.preview.PictureInPictureRendererAccessor;

/**
 * Vanilla PiP target used to render cached preview geometry off-screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PreviewScenePictureInPictureRenderer extends PictureInPictureRenderer<PreviewSceneRenderState> {
    private GpuTexture preparedDepthTexture;
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
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.backupProjectionMatrix();
        try {
            super.prepare(state, guiRenderState, guiScale);
            state.owner().onPictureInPicturePrepared(preparedDepthTexture, preparedCamera,
                    state.mouseX(), state.mouseY(), state.frame());
        } finally {
            preparedDepthTexture = null;
            preparedCamera = null;
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.restoreProjectionMatrix();
        }
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
        preparedDepthTexture = ((PictureInPictureRendererAccessor) (Object) this).mmcr$getDepthTexture();
        preparedCamera = camera;
    }

}

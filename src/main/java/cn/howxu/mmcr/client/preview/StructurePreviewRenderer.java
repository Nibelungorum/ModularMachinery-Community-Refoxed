package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderState;
import cn.howxu.mmcr.client.preview.scene.PreviewScenePictureInPictureRenderer;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderer;
import cn.howxu.mmcr.client.preview.mixin.GuiGraphicsExtractorAccessor;
import cn.howxu.mmcr.client.preview.mixin.DepthTextureReadbackBridge;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects the host-neutral preview widget to the cached PiP scene renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewRenderer implements PreviewRenderer {
    private final StructurePreviewSchema schema;
    private final PreviewLevel level;
    private final PreviewSceneRenderer scene;
    private final PreviewScenePictureInPictureRenderer pictureInPicture;
    private final PreviewOwnerLifecycle lifecycle = new PreviewOwnerLifecycle();
    private final AtomicBoolean releaseScheduled = new AtomicBoolean();
    private BlockHitResult hoverHit;
    private BlockHitResult selectedHit;
    private GpuBuffer depthReadbackBuffer;
    private boolean depthReadbackInFlight;
    private volatile boolean closed;

    public StructurePreviewRenderer(StructurePreviewSchema schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL);
        this.scene = new PreviewSceneRenderer(level, schema);
        this.pictureInPicture = new PreviewScenePictureInPictureRenderer(
                net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
        StructurePreviewReloadListener.register(this);
    }

    @Override
    public StructurePreviewSchema schema() {
        return schema;
    }

    @Override
    public void setVisibility(PreviewVisibility visibility) {
        if (closed) return;
        scene.setVisibility(visibility);
    }

    @Override
    public void resetCamera() {
    }

    @Override
    public void render(PreviewRenderContext context) {
        lifecycle.drainOwnerQueue();
        if (closed || context.viewport().width() <= 0 || context.viewport().height() <= 0) return;
        GuiGraphicsExtractorAccessor graphics = (GuiGraphicsExtractorAccessor) context.graphics();
        int mouseX = graphics.mmcr$getMouseX();
        int mouseY = graphics.mmcr$getMouseY();
        PreviewViewport absoluteViewport = context.absoluteViewport();
        PreviewViewport.FramebufferViewport framebuffer = context.framebufferViewport();
        int guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        PreviewFrameViewport frame = new PreviewFrameViewport(absoluteViewport, framebuffer,
                absoluteViewport.width() * guiScale, absoluteViewport.height() * guiScale, guiScale);
        if (!frame.containsAbsoluteGui(mouseX, mouseY)) hoverHit = null;
        pictureInPicture.prepare(new PreviewSceneRenderState(scene, context.camera(),
                absoluteViewport.x(), absoluteViewport.y(), absoluteViewport.x() + absoluteViewport.width(), absoluteViewport.y() + absoluteViewport.height(),
                context.partialTick(), context.graphics().peekScissorStack(),
                mouseX, mouseY, frame, this),
                graphics.mmcr$getGuiRenderState(), guiScale);
    }

    @Override
    public Object hitResult() {
        return hoverHit;
    }

    @Override
    public void selectHit(Object hitResult) {
        if (hitResult instanceof BlockHitResult blockHitResult) selectedHit = blockHitResult;
    }

    /** Called after PiP has flushed its scene buffers into the owned depth texture. */
    public void onPictureInPicturePrepared(GpuTexture depthTexture, cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera camera,
                                            int mouseX, int mouseY, PreviewFrameViewport frame) {
        lifecycle.drainOwnerQueue();
        if (closed) return;
        if (depthTexture == null || !frame.containsAbsoluteGui(mouseX, mouseY)) {
            hoverHit = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (depthReadbackInFlight || !lifecycle.shouldRead(mouseX, mouseY, now)) return;
        PreviewFrameViewport.Pixel textureMouse = frame.depthTexturePixel(mouseX, mouseY,
                depthTexture.getWidth(0), depthTexture.getHeight(0));
        if (depthReadbackBuffer == null) {
            depthReadbackBuffer = RenderSystem.getDevice().createBuffer(() -> "MMCR preview depth readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L);
        }
        long token = lifecycle.nextReadback(mouseX, mouseY, now);
        GpuBuffer issuedBuffer = depthReadbackBuffer;
        depthReadbackInFlight = true;
        DepthTextureReadbackBridge encoder = (DepthTextureReadbackBridge) RenderSystem.getDevice().createCommandEncoder();
        encoder.mmcr$copyDepthTextureToBuffer(depthTexture, issuedBuffer, 0L, () -> {
            lifecycle.enqueueCallback(token, () -> readDepthOnOwner(issuedBuffer, token, frame, mouseX, mouseY, camera));
        }, textureMouse.x(), textureMouse.y());
    }

    public void renderScene(cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderContext context, PreviewCamera camera) {
        scene.render(context, camera, hoverHit, selectedHit);
    }

    private void readDepthOnOwner(GpuBuffer issuedBuffer, long token, PreviewFrameViewport frame, int mouseX, int mouseY,
                                  cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera camera) {
        try {
            if (closed || !lifecycle.accepts(token) || issuedBuffer != depthReadbackBuffer) return;
            try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(issuedBuffer, true, false)) {
                applyDepth(mapped.data().getFloat(0), frame, mouseX, mouseY, camera);
            }
        } finally {
            depthReadbackInFlight = false;
        }
    }

    void markDirty() {
        if (closed) return;
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            scene.markDirty();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (!closed) scene.markDirty();
            });
        }
    }

    @Override
    public void close() {
        if (!lifecycle.queueRelease(this::releaseResources)) return;
        StructurePreviewReloadListener.unregister(this);
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            lifecycle.drainOwnerQueue();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(lifecycle::drainOwnerQueue);
        }
    }

    private void applyDepth(float depth, PreviewFrameViewport frame, int mouseX, int mouseY,
                            cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera camera) {
        if (closed || depth >= 1.0F) {
            hoverHit = null;
            return;
        }
        PreviewFrameViewport.Pixel depthPixel = frame.depthTexturePixel(mouseX, mouseY);
        int depthX = depthPixel.x();
        int depthY = depthPixel.y();
        org.joml.Vector4f point = new org.joml.Vector4f(2.0F * (depthX + 0.5F) / frame.pipAllocationWidth() - 1.0F,
                1.0F - 2.0F * (depthY + 0.5F) / frame.pipAllocationHeight(), depth * 2.0F - 1.0F, 1.0F);
        org.joml.Matrix4f inverse = camera.projection().mul(camera.view(), new org.joml.Matrix4f()).invert();
        inverse.transform(point);
        net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(point.x / point.w, point.y / point.w, point.z / point.w);
        hoverHit = scene.clip(new net.minecraft.world.phys.Vec3(camera.eye()), target);
    }

    private void releaseResources() {
        if (!releaseScheduled.compareAndSet(false, true)) return;
        closed = true;
        if (depthReadbackBuffer != null) {
            depthReadbackBuffer.close();
            depthReadbackBuffer = null;
        }
        pictureInPicture.close();
        scene.dispose();
    }
}

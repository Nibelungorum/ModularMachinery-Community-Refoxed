package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderState;
import cn.howxu.mmcr.client.preview.scene.PreviewScenePictureInPictureRenderer;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderer;
import cn.howxu.mmcr.client.preview.mixin.GuiGraphicsExtractorAccessor;
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
        if (closed || context.viewport().width() <= 0 || context.viewport().height() <= 0) return;
        GuiGraphicsExtractorAccessor graphics = (GuiGraphicsExtractorAccessor) context.graphics();
        int mouseX = graphics.mmcr$getMouseX();
        int mouseY = graphics.mmcr$getMouseY();
        if (!context.viewport().contains(mouseX, mouseY)) hoverHit = null;
        int x0 = context.guiOriginX() + context.viewport().x();
        int y0 = context.guiOriginY() + context.viewport().y();
        PreviewViewport.FramebufferViewport framebuffer = context.framebufferViewport();
        PreviewFrameViewport frame = new PreviewFrameViewport(framebuffer.x(), framebuffer.y(), framebuffer.width(), framebuffer.height(),
                context.viewport().x(), context.viewport().y(), context.viewport().width(), context.viewport().height(),
                context.viewport().width(), context.viewport().height());
        pictureInPicture.prepare(new PreviewSceneRenderState(scene, context.camera(),
                x0, y0, x0 + context.viewport().width(), y0 + context.viewport().height(),
                context.partialTick(), context.graphics().peekScissorStack(),
                mouseX, mouseY, frame, this),
                graphics.mmcr$getGuiRenderState(), 1);
    }

    @Override
    public Object hitResult() {
        return hoverHit;
    }

    @Override
    public void selectHit(Object hitResult) {
        if (hitResult instanceof BlockHitResult blockHitResult) selectedHit = blockHitResult;
    }

    /** Called by this renderer's owned PiP target after its output attachments are active. */
    public void onPictureInPictureFrame(GpuTexture depthTexture, cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera camera,
                                         int mouseX, int mouseY, PreviewFrameViewport frame) {
        if (closed) return;
        if (depthTexture == null || !frame.containsLogical(mouseX, mouseY)) {
            hoverHit = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (depthReadbackInFlight || !lifecycle.shouldRead(mouseX, mouseY, now)) return;
        int textureMouseX = Math.max(0, Math.min(frame.depthTextureWidth() - 1, frame.depthX(mouseX)));
        int textureMouseY = Math.max(0, Math.min(frame.depthTextureHeight() - 1, frame.depthY(mouseY)));
        if (depthReadbackBuffer == null) {
            depthReadbackBuffer = RenderSystem.getDevice().createBuffer(() -> "MMCR preview depth readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L);
        }
        long token = lifecycle.nextReadback(mouseX, mouseY, now);
        GpuBuffer issuedBuffer = depthReadbackBuffer;
        depthReadbackInFlight = true;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(depthTexture, issuedBuffer, 0L, () -> {
            try {
                if (closed || !lifecycle.accepts(token) || issuedBuffer != depthReadbackBuffer) return;
                try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(issuedBuffer, true, false)) {
                    applyDepth(mapped.data().getFloat(0), frame, mouseX, mouseY, camera);
                }
            } finally {
                depthReadbackInFlight = false;
            }
        }, 0, textureMouseX, textureMouseY, 1, 1);
    }

    public void renderScene(cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderContext context, PreviewCamera camera) {
        scene.render(context, camera, hoverHit, selectedHit);
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
        if (!lifecycle.queueRelease()) return;
        closed = true;
        StructurePreviewReloadListener.unregister(this);
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            releaseResources();
        } else {
            net.minecraft.client.Minecraft.getInstance().execute(this::releaseResources);
        }
    }

    private void applyDepth(float depth, PreviewFrameViewport frame, int mouseX, int mouseY,
                            cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera camera) {
        if (closed || depth >= 1.0F) {
            hoverHit = null;
            return;
        }
        int depthX = frame.depthX(mouseX);
        int depthY = frame.depthY(mouseY);
        org.joml.Vector4f point = new org.joml.Vector4f(2.0F * (depthX + 0.5F) / frame.depthTextureWidth() - 1.0F,
                1.0F - 2.0F * (depthY + 0.5F) / frame.depthTextureHeight(), depth * 2.0F - 1.0F, 1.0F);
        org.joml.Matrix4f inverse = camera.projection().mul(camera.view(), new org.joml.Matrix4f()).invert();
        inverse.transform(point);
        net.minecraft.world.phys.Vec3 target = new net.minecraft.world.phys.Vec3(point.x / point.w, point.y / point.w, point.z / point.w);
        hoverHit = scene.clip(new net.minecraft.world.phys.Vec3(camera.eye()), target);
    }

    private void releaseResources() {
        if (!releaseScheduled.compareAndSet(false, true)) return;
        if (depthReadbackBuffer != null) {
            depthReadbackBuffer.close();
            depthReadbackBuffer = null;
        }
        pictureInPicture.close();
        scene.dispose();
    }
}

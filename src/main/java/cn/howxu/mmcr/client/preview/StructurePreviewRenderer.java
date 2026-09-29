package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderState;
import cn.howxu.mmcr.client.preview.scene.PreviewScenePictureInPictureRenderer;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderer;

import cn.howxu.mmcr.client.preview.scene.PreviewSceneCamera;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderContext;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

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
    private final AtomicBoolean releaseScheduled = new AtomicBoolean();
    private BlockHitResult hoverHit;
    private BlockHitResult selectedHit;
    private volatile boolean interactive;
    private volatile boolean closed;
    private long visibilityVersion;
    private long lastHoverCameraVersion = Long.MIN_VALUE;
    private int lastHoverMouseX;
    private int lastHoverMouseY;
    private int lastHoverViewportX;
    private int lastHoverViewportY;
    private int lastHoverWidth;
    private int lastHoverHeight;
    private long lastHoverVisibilityVersion = Long.MIN_VALUE;

    public StructurePreviewRenderer(StructurePreviewSchema schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL);
        this.scene = new PreviewSceneRenderer(level, schema);
        this.pictureInPicture = new PreviewScenePictureInPictureRenderer(
                Minecraft.getInstance().renderBuffers().bufferSource());
        StructurePreviewReloadListener.register(this);
    }

    @Override
    public StructurePreviewSchema schema() {
        return schema;
    }

    @Override
    public void setVisibility(PreviewVisibility visibility) {
        if (closed) return;
        visibilityVersion++;
        invalidateHoverInputs();
        scene.setVisibility(visibility);
    }

    @Override
    public void resetCamera() {
        invalidateHoverInputs();
    }

    @Override
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    @Override
    public void render(PreviewRenderContext context) {
        if (closed || context.viewport().width() <= 0 || context.viewport().height() <= 0) return;
        int mouseX = context.mouseX();
        int mouseY = context.mouseY();
        PreviewViewport absoluteViewport = context.absoluteViewport();
        if (absoluteViewport.contains(mouseX, mouseY)) {
            boolean changed = lastHoverCameraVersion != context.camera().version()
                    || lastHoverMouseX != mouseX
                    || lastHoverMouseY != mouseY
                    || lastHoverViewportX != absoluteViewport.x()
                    || lastHoverViewportY != absoluteViewport.y()
                    || lastHoverWidth != absoluteViewport.width()
                    || lastHoverHeight != absoluteViewport.height()
                    || lastHoverVisibilityVersion != visibilityVersion;
            if (changed) {
                double localMouseX = mouseX - absoluteViewport.x();
                double localMouseY = mouseY - absoluteViewport.y();
                PreviewSceneCamera camera = PreviewSceneCamera.from(context.camera(),
                        absoluteViewport.width(), absoluteViewport.height());
                hoverHit = scene.rayTrace(camera, localMouseX, localMouseY,
                        absoluteViewport.width(), absoluteViewport.height());
                lastHoverCameraVersion = context.camera().version();
                lastHoverMouseX = mouseX;
                lastHoverMouseY = mouseY;
                lastHoverViewportX = absoluteViewport.x();
                lastHoverViewportY = absoluteViewport.y();
                lastHoverWidth = absoluteViewport.width();
                lastHoverHeight = absoluteViewport.height();
                lastHoverVisibilityVersion = visibilityVersion;
            }
        } else {
            hoverHit = null;
            invalidateHoverInputs();
        }
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        pictureInPicture.prepare(new PreviewSceneRenderState(scene, context.camera(),
                absoluteViewport.x(), absoluteViewport.y(), absoluteViewport.x() + absoluteViewport.width(), absoluteViewport.y() + absoluteViewport.height(),
                context.partialTick(), null,
                this),
                context.graphics(), guiScale);
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return hoverHit;
    }

    @Override
    public void selectHit(Object hitResult) {
        selectedHit = hitResult instanceof BlockHitResult blockHitResult ? copyHit(blockHitResult) : null;
    }

    public void renderScene(PreviewSceneRenderContext context, PreviewCamera camera) {
        boolean renderTranslucent = !interactive || !ClientConfig.skipTranslucentDuringInteraction();
        boolean renderBlockEntities = !interactive || !ClientConfig.skipBlockEntitiesDuringInteraction();
        scene.render(context, camera, hoverHit, selectedHit, renderTranslucent, renderBlockEntities);
    }

    public float interactiveRenderScale() {
        return interactive ? (float) ClientConfig.interactiveRenderScale() : 1.0F;
    }

    void markDirty() {
        if (closed) return;
        if (Minecraft.getInstance().isSameThread()) {
            scene.markDirty();
        } else {
            Minecraft.getInstance().execute(() -> {
                if (!closed) scene.markDirty();
            });
        }
    }

    @Override
    public void close() {
        if (!releaseScheduled.compareAndSet(false, true)) return;
        StructurePreviewReloadListener.unregister(this);
        if (Minecraft.getInstance().isSameThread()) {
            releaseResources();
        } else {
            Minecraft.getInstance().execute(this::releaseResources);
        }
    }

    private static @Nullable BlockHitResult copyHit(@Nullable BlockHitResult hit) {
        if (hit == null) return null;
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos().immutable(), hit.isInside());
    }

    private void releaseResources() {
        closed = true;
        pictureInPicture.close();
        scene.dispose();
    }

    private void invalidateHoverInputs() {
        hoverHit = null;
        lastHoverCameraVersion = Long.MIN_VALUE;
        lastHoverVisibilityVersion = Long.MIN_VALUE;
    }
}

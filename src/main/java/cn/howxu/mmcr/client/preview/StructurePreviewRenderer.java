package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderState;
import cn.howxu.mmcr.client.preview.scene.PreviewScenePictureInPictureRenderer;
import cn.howxu.mmcr.client.preview.scene.PreviewSceneRenderer;
import cn.howxu.mmcr.mixin.client.preview.GuiGraphicsExtractorAccessor;

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
    private volatile boolean closed;

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
        PreviewViewport absoluteViewport = context.absoluteViewport();
        if (absoluteViewport.contains(mouseX, mouseY)) {
            double localMouseX = mouseX - absoluteViewport.x();
            double localMouseY = mouseY - absoluteViewport.y();
            PreviewSceneCamera camera = PreviewSceneCamera.from(context.camera(),
                    absoluteViewport.width(), absoluteViewport.height());
            hoverHit = scene.rayTrace(camera, localMouseX, localMouseY,
                    absoluteViewport.width(), absoluteViewport.height());
        } else {
            hoverHit = null;
        }
        int guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        pictureInPicture.prepare(new PreviewSceneRenderState(scene, context.camera(),
                absoluteViewport.x(), absoluteViewport.y(), absoluteViewport.x() + absoluteViewport.width(), absoluteViewport.y() + absoluteViewport.height(),
                context.partialTick(), context.graphics().peekScissorStack(),
                this),
                graphics.mmcr$getGuiRenderState(), guiScale);
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return hoverHit;
    }

    @Override
    public void selectHit(Object hitResult) {
        if (hitResult instanceof BlockHitResult blockHitResult) {
            selectedHit = copyHit(blockHitResult);
        }
    }

    public void renderScene(PreviewSceneRenderContext context, PreviewCamera camera) {
        scene.render(context, camera, hoverHit, selectedHit);
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
}

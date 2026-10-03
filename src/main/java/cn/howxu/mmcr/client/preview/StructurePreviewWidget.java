package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.config.ClientConfig;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Host-neutral interaction state for a structure preview renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewWidget implements AutoCloseable {
    private static final double DRAG_THRESHOLD_SQUARED = 9.0D;
    private static final long DOUBLE_CLICK_INTERVAL_MILLIS = 250L;

    private final PreviewRenderer renderer;
    private final LongSupplier clock;
    private final PreviewCamera camera = new PreviewCamera();
    private PreviewViewport viewport = new PreviewViewport(0, 0, 0, 0);
    private double pressX;
    private double pressY;
    private int pressButton = -1;
    private boolean dragged;
    private boolean closed;
    private long interactiveUntilNanos;
    private int selectedLayer = -1;
    private Object selectedHit;
    private long previousClickTime = Long.MIN_VALUE;
    private double previousClickX;
    private double previousClickY;
    private Object previousClickHit;

    public StructurePreviewWidget(PreviewRenderer renderer) {
        this(renderer, System::currentTimeMillis);
    }

    StructurePreviewWidget(PreviewRenderer renderer, LongSupplier clock) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
        resetCamera();
    }

    public void render(PreviewRenderContext context) {
        if (closed) return;
        if (interactiveUntilNanos != 0L && System.nanoTime() >= interactiveUntilNanos) {
            interactiveUntilNanos = 0L;
            renderer.setInteractive(false);
        }
        viewport = context.viewport();
        renderer.render(context);
    }

    /** Renders this preview inside the supplied GUI rectangle. */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
            int mouseX, int mouseY) {
        render(graphics, x, y, width, height, partialTick, 0, 0, mouseX, mouseY);
    }

    /** Renders this preview with the GUI origin supplied by an embedding host. */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
            int guiOriginX, int guiOriginY, int mouseX, int mouseY) {
        render(new PreviewRenderContext(graphics, new PreviewViewport(x, y, width, height), partialTick,
                guiOriginX, guiOriginY, mouseX, mouseY, camera));
    }

    public Object hoverHit() {
        return renderer.hitResult();
    }

    public Object selectedHit() {
        return selectedHit;
    }

    public @Nullable BlockPos selectedPosition() {
        return selectedHit instanceof BlockHitResult hit ? hit.getBlockPos().immutable() : null;
    }

    public int selectedLayer() {
        List<Integer> layers = renderer.schema().layers();
        return selectedLayer < 0 || selectedLayer >= layers.size() ? -1 : layers.get(selectedLayer);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closed || !viewport.contains(mouseX, mouseY)) return false;
        pressX = mouseX;
        pressY = mouseY;
        pressButton = button;
        dragged = false;
        interactiveUntilNanos = 0L;
        renderer.setInteractive(true);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closed || pressButton != button) return false;
        boolean handled = viewport.contains(mouseX, mouseY);
        double movementX = mouseX - pressX;
        double movementY = mouseY - pressY;
        boolean click = button == 0 && handled && !dragged
                && movementX * movementX + movementY * movementY <= DRAG_THRESHOLD_SQUARED;
        pressButton = -1;
        dragged = false;
        interactiveUntilNanos = 0L;
        renderer.setInteractive(false);
        if (click) handleClick(mouseX, mouseY, renderer.hitResult());
        else clearPreviousClick();
        return handled;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (closed || pressButton != button || !viewport.contains(mouseX, mouseY)) return false;
        dragged = true;
        if (button == 0) {
            camera.orbit((float) -dragX * 0.01F, (float) dragY * 0.01F);
        } else if (button == 2) {
            camera.pan((float) dragX, (float) dragY);
        } else {
            return false;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (closed || !viewport.contains(mouseX, mouseY)) return false;
        camera.zoom((float) Math.pow(0.9F, scrollDelta));
        renderer.setInteractive(true);
        interactiveUntilNanos = System.nanoTime() + ClientConfig.interactiveRestoreDelayNanos();
        return true;
    }

    public void selectPreviousLayer() {
        List<Integer> layers = renderer.schema().layers();
        if (layers.isEmpty()) return;
        if (selectedLayer < 0) {
            selectedLayer = layers.size() - 1;
        } else if (selectedLayer == 0) {
            selectedLayer = -1;
        } else {
            selectedLayer--;
        }
        applySelectedLayer(layers);
    }

    public void selectNextLayer() {
        List<Integer> layers = renderer.schema().layers();
        if (layers.isEmpty()) return;
        selectedLayer = selectedLayer + 1;
        if (selectedLayer >= layers.size()) selectedLayer = -1;
        applySelectedLayer(layers);
    }

    public void showAllLayers() {
        selectedLayer = -1;
        renderer.setVisibility(PreviewVisibility.ALL);
    }

    public void reset() {
        showAllLayers();
        clearPreviousClick();
        selectedHit = null;
        renderer.selectHit(null);
        resetCamera();
        renderer.resetCamera();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        clearPreviousClick();
        renderer.close();
    }

    void setViewport(PreviewViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    PreviewCamera camera() {
        return camera;
    }

    private void applySelectedLayer(List<Integer> layers) {
        renderer.setVisibility(selectedLayer < 0 ? PreviewVisibility.ALL : PreviewVisibility.singleLayer(layers.get(selectedLayer)));
    }

    private void resetCamera() {
        StructurePreviewSchema schema = renderer.schema();
        float width = schema.max().getX() - schema.min().getX() + 1.0F;
        float height = schema.max().getY() - schema.min().getY() + 1.0F;
        float depth = schema.max().getZ() - schema.min().getZ() + 1.0F;
        Vector3f center = new Vector3f(schema.center().get(0), schema.center().get(1), schema.center().get(2));
        float radius = Math.max(width, Math.max(height, depth)) * 1.5F;
        BlockState controller = schema.stateAt(BlockPos.ZERO);
        if (controller != null && controller.getBlock() instanceof MachineControllerBlock) {
            Direction facing = controller.getValue(MachineControllerBlock.FACING);
            if (facing.getAxis().isHorizontal()) {
                center.y += 0.5F;
                camera.reset(center, radius, (float) Math.atan2(facing.getStepX(), facing.getStepZ()), 0.0F);
                return;
            }
        }
        camera.reset(center, radius);
    }

    private void handleClick(double mouseX, double mouseY, Object hit) {
        if (hit == null) {
            clearPreviousClick();
            return;
        }
        long now = clock.getAsLong();
        long elapsed = now - previousClickTime;
        double movementX = mouseX - previousClickX;
        double movementY = mouseY - previousClickY;
        boolean doubleClick = elapsed >= 0L && elapsed <= DOUBLE_CLICK_INTERVAL_MILLIS
                && movementX * movementX + movementY * movementY <= DRAG_THRESHOLD_SQUARED
                && sameHit(previousClickHit, hit);
        if (doubleClick) {
            selectedHit = hit;
            renderer.selectHit(hit);
            clearPreviousClick();
        } else {
            previousClickTime = now;
            previousClickX = mouseX;
            previousClickY = mouseY;
            previousClickHit = hit;
        }
    }

    private static boolean sameHit(Object first, Object second) {
        if (first instanceof BlockHitResult firstBlock && second instanceof BlockHitResult secondBlock) {
            return firstBlock.getBlockPos().equals(secondBlock.getBlockPos());
        }
        return Objects.equals(first, second);
    }

    private void clearPreviousClick() {
        previousClickTime = Long.MIN_VALUE;
        previousClickHit = null;
    }
}

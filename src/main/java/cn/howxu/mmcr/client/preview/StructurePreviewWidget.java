package cn.howxu.mmcr.client.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/**
 * Host-neutral interaction state for a structure preview renderer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewWidget implements AutoCloseable {
    private static final double DRAG_THRESHOLD_SQUARED = 9.0D;

    private final PreviewRenderer renderer;
    private final PreviewCamera camera = new PreviewCamera();
    private PreviewViewport viewport = new PreviewViewport(0, 0, 0, 0);
    private double pressX;
    private double pressY;
    private int pressButton = -1;
    private boolean dragged;
    private boolean closed;
    private int selectedLayer = -1;
    private Object selectedHit;

    public StructurePreviewWidget(PreviewRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        resetCamera();
    }

    public void render(PreviewRenderContext context) {
        if (closed) return;
        viewport = context.viewport();
        renderer.render(context);
    }

    /** Renders this preview inside the supplied GUI rectangle. */
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        render(new PreviewRenderContext(graphics, new PreviewViewport(x, y, width, height), partialTick,
                0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), camera));
    }

    public Object hoverHit() {
        return renderer.hitResult();
    }

    public Object selectedHit() {
        return selectedHit;
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
        if (click) {
            Object hitResult = renderer.hitResult();
            if (hitResult != null) {
                selectedHit = hitResult;
                renderer.selectHit(hitResult);
            }
        }
        return handled;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (closed || pressButton != button || !viewport.contains(mouseX, mouseY)) return false;
        double movementX = mouseX - pressX;
        double movementY = mouseY - pressY;
        dragged |= movementX * movementX + movementY * movementY > DRAG_THRESHOLD_SQUARED;
        if (button == 0) {
            camera.orbit((float) dragX * 0.01F, (float) dragY * 0.01F);
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
        resetCamera();
        renderer.resetCamera();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
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
        camera.reset(new Vector3f(schema.center().get(0), schema.center().get(1), schema.center().get(2)),
                Math.max(width, Math.max(height, depth)) * 1.5F);
    }
}

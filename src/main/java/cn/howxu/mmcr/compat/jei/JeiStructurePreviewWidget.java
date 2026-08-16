package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.client.preview.StructurePreviewRenderer;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.client.preview.StructurePreviewWidget;
import cn.howxu.mmcr.mixin.client.preview.GuiGraphicsExtractorAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * JEI adapter for one independently-owned structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiStructurePreviewWidget implements IRecipeWidget, IJeiInputHandler, AutoCloseable {
    private static final int CONTROL_HEIGHT = 10;
    private static final int LAYOUT_WIDTH = 168;
    private static final int LAYOUT_HEIGHT = 240;
    private final Preview preview;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final StructurePreviewSchema schema;
    private boolean previewDragActive;
    private boolean closed;

    public JeiStructurePreviewWidget(Machine machine, int x, int y, int width, int height) {
        this(createSchema(machine), x, y, width, height);
    }

    private static StructurePreviewSchema createSchema(Machine machine) {
        return new StructurePreviewSchemaFactory().create(machine);
    }

    private JeiStructurePreviewWidget(StructurePreviewSchema schema, int x, int y, int width, int height) {
        this(schema, new StructurePreviewWidget(new StructurePreviewRenderer(schema)), x, y, width, height);
    }

    private JeiStructurePreviewWidget(StructurePreviewSchema schema, StructurePreviewWidget widget, int x, int y, int width, int height) {
        this(new Preview() {
            @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return widget.mouseClicked(mouseX, mouseY, button); }
            @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return widget.mouseReleased(mouseX, mouseY, button); }
            @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return widget.mouseDragged(mouseX, mouseY, button, dragX, dragY); }
            @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return widget.mouseScrolled(mouseX, mouseY, scrollDelta); }
            @Override public void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int originX, int originY) {
                widget.render(graphics, x, y, width, height, 0, originX, originY);
            }
            @Override public void previous() { widget.selectPreviousLayer(); }
            @Override public void next() { widget.selectNextLayer(); }
            @Override public void all() { widget.showAllLayers(); }
            @Override public void reset() { widget.reset(); }
            @Override public int selectedLayer() { return widget.selectedLayer(); }
            @Override public Object hoverHit() { return widget.hoverHit(); }
            @Override public Object selectedHit() { return widget.selectedHit(); }
        }, schema, x, y, width, height);
    }

    private JeiStructurePreviewWidget(Preview preview, StructurePreviewSchema schema, int x, int y, int width, int height) {
        this.preview = preview;
        this.schema = schema;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    static JeiStructurePreviewWidget forTesting(Preview preview, int x, int y, int width, int height) {
        return forTesting(preview, null, x, y, width, height);
    }

    static JeiStructurePreviewWidget forTesting(Preview preview, StructurePreviewSchema schema, int x, int y, int width, int height) {
        return new JeiStructurePreviewWidget(preview, schema, x, y, width, height);
    }

    static JeiStructurePreviewWidget forTesting(StructurePreviewSchema schema, PreviewFactory factory, int x, int y, int width, int height) {
        return new JeiStructurePreviewWidget(factory.create(schema), schema, x, y, width, height);
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT); }

    @Override
    public void drawWidget(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        GuiGraphicsExtractorAccessor extractor = (GuiGraphicsExtractorAccessor) graphics;
        ScreenPosition origin = absoluteGuiOrigin(extractor.mmcr$getMouseX(), extractor.mmcr$getMouseY(), mouseX, mouseY);
        preview.render(graphics, x, y, width, height, origin.x(), origin.y());
        String[] keys = {"previous_layer", "next_layer", "all_layers", "reset"};
        for (int index = 0; index < keys.length; index++) {
            graphics.text(Minecraft.getInstance().font, Component.translatable("jei.mmcr.structure_preview." + keys[index]),
                    x + index * 40, y + height + 2, 0xFF404040, false);
        }
        int selectedLayer = preview.selectedLayer();
        List<Integer> layers = schema == null ? List.of() : schema.layers();
        Component layerText = selectedLayer < 0
                ? Component.translatable("jei.mmcr.structure_preview.all_layers")
                : Component.translatable("jei.mmcr.structure_preview.layer", selectedLayer, layers.indexOf(selectedLayer) + 1, layers.size());
        graphics.text(Minecraft.getInstance().font, layerText, x, y + height - 10, 0xFF404040, false);
    }

    static ScreenPosition absoluteGuiOrigin(int absoluteMouseX, int absoluteMouseY, double localMouseX, double localMouseY) {
        return new ScreenPosition(absoluteMouseX - (int) localMouseX, absoluteMouseY - (int) localMouseY);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        preview.close();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (!insidePreview(mouseX, mouseY)) return;
        Object hit = preview.hoverHit() != null ? preview.hoverHit() : preview.selectedHit();
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            BlockState state = schema == null ? null : schema.stateAt(blockHit.getBlockPos());
            if (state == null) return;
            tooltip.add(state.getBlock().getName());
            if (!state.getFluidState().isEmpty()) tooltip.add(state.getFluidState().getType().getBucket().getDefaultInstance().getHoverName());
        }
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        if (input.getKey().getType() != InputConstants.Type.MOUSE) return false;
        int button = input.getKey().getValue();
        if (button != 0) return false;
        if (!input.isSimulate() && previewDragActive) {
            previewDragActive = false;
            boolean inside = insidePreview(mouseX, mouseY);
            if (insidePreview(mouseX, mouseY)) preview.mouseReleased(mouseX - x, mouseY - y, button);
            return inside;
        }
        int control = controlAt(mouseX, mouseY);
        if (control >= 0) {
            if (input.isSimulate()) return true;
            switch (control) {
                case 0 -> preview.previous();
                case 1 -> preview.next();
                case 2 -> preview.all();
                case 3 -> preview.reset();
                default -> { }
            }
            return true;
        }
        if (input.isSimulate()) {
            return insidePreview(mouseX, mouseY);
        }
        if (!insidePreview(mouseX, mouseY)) return false;
        boolean handled = preview.mouseClicked(previewMouseX(mouseX), previewMouseY(mouseY), button);
        previewDragActive = handled;
        return handled;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey, double dragX, double dragY) {
        return previewDragActive && insidePreview(mouseX, mouseY) && mouseKey.getType() == InputConstants.Type.MOUSE
                && preview.mouseDragged(previewMouseX(mouseX), previewMouseY(mouseY), mouseKey.getValue(), dragX, dragY);
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        return insidePreview(mouseX, mouseY)
                && preview.mouseScrolled(previewMouseX(mouseX), previewMouseY(mouseY), scrollDeltaY);
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private double previewMouseX(double mouseX) { return mouseX - x; }
    private double previewMouseY(double mouseY) { return mouseY - y; }
    private int controlAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseY < y + height + 2 || mouseY >= y + height + 2 + CONTROL_HEIGHT) return -1;
        int column = (int) (mouseX - x) / 40;
        return column >= 0 && column < 4 && mouseX < x + column * 40 + (column == 3 ? 40 : 38) ? column : -1;
    }

    interface Preview {
        boolean mouseClicked(double mouseX, double mouseY, int button);
        default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
        default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
        default boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return false; }
        default void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int originX, int originY) { }
        default void close() { }
        default void previous() { }
        default void next() { }
        default void all() { }
        default void reset() { }
        default int selectedLayer() { return -1; }
        default Object hoverHit() { return null; }
        default Object selectedHit() { return null; }
    }

    interface PreviewFactory {
        Preview create(StructurePreviewSchema schema);
    }
}

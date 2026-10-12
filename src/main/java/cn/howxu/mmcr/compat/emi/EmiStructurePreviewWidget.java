package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.client.preview.StructurePreviewPanel;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import cn.howxu.mmcr.internal.client.RecipeViewerSnapshot.Version;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Host-owned real structure panel, translated controls and native candidate queries.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiStructurePreviewWidget extends Widget implements EmiInteractiveWidget {
    public static final int CONTROLS_HEIGHT = 36;
    private static final int CANDIDATE_COUNT = 8;
    private static final String[] CONTROL_KEYS = {"previous_layer", "next_layer", "all_layers", "reset",
            "previous_level", "next_level"};
    private static final String[] BUTTON_KEYS = {"previous_layer", "next_layer", "all_layers", "reset",
            "previous_stage", "next_stage"};
    private final Object host;
    private final MachineStructureDisplay display;
    private final Version version;
    private final int x, y, width, height;
    private final LongSupplier clock;
    private final List<EmiStack> candidates = new ArrayList<>();
    private final List<Boolean> modifiers = new ArrayList<>();
    private EmiPreviewSessions.Session<StructurePreviewPanel> session;
    private boolean borrowed;
    private boolean closed;

    public EmiStructurePreviewWidget(Object host, MachineStructureDisplay display, Version version,
                                     int x, int y, int width, int height, LongSupplier clock) {
        this.host = host;
        this.display = display;
        this.version = version;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.clock = clock;
    }

    /** Add the panel first, followed by native candidate slots (outside the routed 3D region). */
    public static EmiStructurePreviewWidget add(WidgetHolder holder, MachineStructureDisplay display,
            Version version, int x, int y, int width, int height, LongSupplier clock) {
        var preview = holder.add(new EmiStructurePreviewWidget(holder, display, version, x, y, width, height, clock));
        for (int slot = 0; slot < Math.min(CANDIDATE_COUNT, height / 18); slot++) {
            holder.add(preview.new CandidateSlot(slot));
        }
        return preview;
    }

    @Override public Bounds getBounds() { return closed ? Bounds.EMPTY : new Bounds(x, y, width, height + CONTROLS_HEIGHT); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (closed) return;
        var scope = EmiRenderScope.current();
        if (session == null || !session.current(version)) {
            borrowed = scope != null;
            session = scope == null
                    ? EmiPreviewSessions.instance().page(host, display.machine().registryName(), version,
                            () -> new StructurePreviewPanel(display.machine()))
                    : scope.acquire(EmiPreviewSessions.instance(), display.machine().registryName(), version,
                            () -> new StructurePreviewPanel(display.machine()));
        }
        StructurePreviewPanel panel = session.resource();
        if (scope != null && scope.isScreenshot()) panel.prepareSynchronously();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x + 20, y, 0);
            var pose = graphics.pose().last().pose();
            int originX = Math.round(pose.m30()), originY = Math.round(pose.m31());
            panel.render(graphics, width - 20, height, delta, originX, originY,
                    originX + mouseX - x - 20, originY + mouseY - y, originX, originY);
        } finally {
            graphics.pose().popPose();
        }
        updateCandidates(panel, clock.getAsLong());
        drawControls(graphics, panel);
    }

    private void updateCandidates(StructurePreviewPanel panel, long time) {
        candidates.clear();
        modifiers.clear();
        int count = Math.min(CANDIDATE_COUNT, height / 18);
        for (int slot = 0; slot < count; slot++) {
            StructurePreviewSchema.Candidate candidate = panel.candidateAt(slot, time, count);
            if (candidate == null) break;
            candidates.add(EmiStack.of(candidate.stack()));
            modifiers.add(candidate.modifier());
        }
    }

    private void drawControls(GuiGraphics graphics, StructurePreviewPanel panel) {
        var font = Minecraft.getInstance().font;
        int count = panel.hasMultipleStages() ? 6 : 4;
        for (int control = 0; control < count; control++) {
            int cx = x + 20 + control * 20, cy = y + height + 2;
            graphics.fill(cx, cy, cx + 18, cy + 18, panel.isReady() ? 0xFF808080 : 0xFF555555);
            Component label = Component.translatable("emi.mmcr.structure_preview.button." + BUTTON_KEYS[control]);
            float scale = Math.min(0.65F, 16F / Math.max(1, font.width(label)));
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(cx + 9, cy + 9, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, label, -font.width(label) / 2, -font.lineHeight / 2, 0xFFFFFFFF, false);
            } finally { graphics.pose().popPose(); }
        }
        int selected = panel.selectedLayer();
        List<Integer> layers = panel.schema() == null ? List.of() : panel.schema().layers();
        Component layer = selected < 0 ? Component.translatable("jei.mmcr.structure_preview.all_layers")
                : Component.translatable("jei.mmcr.structure_preview.layer", selected, layers.indexOf(selected) + 1, layers.size());
        if (panel.hasMultipleStages()) layer = layer.copy().append(" ")
                .append(Component.translatable("emi.mmcr.structure_preview.stage", panel.stageNumber()));
        graphics.drawString(font, layer, x + 20, y + height + 23, 0xFF404040, false);
    }

    private StructurePreviewPanel panel() {
        return session != null && session.current(version) ? session.resource() : null;
    }

    private boolean insidePreview(double mx, double my) {
        return mx >= x + 20 && mx < x + width && my >= y && my < y + height;
    }

    private int controlAt(double mx, double my) {
        if (mx < x + 20 || my < y + height + 2 || my >= y + height + 20) return -1;
        int offset = (int) (mx - x - 20), index = offset / 20;
        int count = display.machine().structureStages().size() > 1 ? 6 : 4;
        return index < count && offset % 20 < 18 ? index : -1;
    }

    @Override public boolean mousePressed(double mx, double my, int button) {
        StructurePreviewPanel panel = panel();
        if (closed || panel == null) return false;
        int control = controlAt(mx, my);
        if (control >= 0) {
            if (button == 0 && panel.isReady()) switch (control) {
                case 0 -> panel.selectPreviousLayer();
                case 1 -> panel.selectNextLayer();
                case 2 -> panel.showAllLayers();
                case 3 -> panel.reset();
                case 4 -> panel.selectPreviousStage();
                case 5 -> panel.selectNextStage();
                default -> { }
            }
            return true;
        }
        return insidePreview(mx, my) && (button == 0 || button == 2)
                && panel.mouseClicked(mx - x - 20, my - y, button);
    }

    @Override public boolean capturesDrag(double mx, double my, int button) { return insidePreview(mx, my); }
    @Override public boolean visible() { return !closed && !borrowed; }
    // Widget.mouseClicked intentionally stays false: only the host router dispatches a press.
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        var panel = panel();
        return panel != null && panel.mouseDragged(mx - x - 20, my - y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        var panel = panel();
        return panel != null && panel.mouseReleased(mx - x - 20, my - y, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        var panel = panel();
        return panel != null && insidePreview(mx, my) && panel.mouseScrolled(mx - x - 20, my - y, vertical);
    }
    @Override public void tick() { }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (!borrowed && session != null) session.close();
    }

    @Override public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        int control = controlAt(mouseX, mouseY);
        return control < 0 || closed ? List.of() : List.of(ClientTooltipComponent.create(
                Component.translatable("jei.mmcr.structure_preview." + CONTROL_KEYS[control]).getVisualOrderText()));
    }

    /** @author howxu <dev@howxu.cn> */
    private final class CandidateSlot extends SlotWidget {
        private final int slot;
        CandidateSlot(int slot) { super(EmiStack.EMPTY, EmiStructurePreviewWidget.this.x,
                EmiStructurePreviewWidget.this.y + slot * 18); this.slot = slot; }
        @Override public EmiIngredient getStack() {
            return !closed && slot < candidates.size() ? candidates.get(slot) : EmiStack.EMPTY;
        }
        @Override public Bounds getBounds() { return getStack().isEmpty() ? Bounds.EMPTY : super.getBounds(); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (!getStack().isEmpty()) super.render(graphics, mouseX, mouseY, delta);
        }
        @Override public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            List<ClientTooltipComponent> tooltip = super.getTooltip(mouseX, mouseY);
            if (slot < modifiers.size() && modifiers.get(slot)) tooltip.add(ClientTooltipComponent.create(
                    Component.translatable("jei.mmcr.structure_preview.modifier").getVisualOrderText()));
            return tooltip;
        }
    }
}

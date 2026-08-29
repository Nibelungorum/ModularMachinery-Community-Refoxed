package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screen for a machine controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerScreen extends AbstractScrollableTextScreen<MachineControllerMenu> {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 213;
    private static final Identifier BACKGROUND = MMCR.id("textures/gui/guicontroller_large.png");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    static final int STATUS_LABEL_COLOR = 0xFFE8E8E8;
    static final int UNFORMED_STATUS_COLOR = 0xFFFF5555;
    private static final int FORMED_STATUS_COLOR = 0xFF55FF55;
    private static final int IDLE_STATUS_COLOR = 0xFFFFAA00;
    private static final int PROGRESS_STATUS_COLOR = -1;
    private static final float DETAIL_SCALE = 0.85F;
    private static final int DETAIL_LINE_SPACING = 10;

    public MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        titleLabelX += 2;
        titleLabelY += 4;
        inventoryLabelY = -1000;
    }

    @Override
    protected TextViewport scrollableTextViewport() {
        int bodyY = titleLabelY + DETAIL_LINE_SPACING;
        return new TextViewport(9, bodyY, 160, 123 - bodyY + 1,
                DETAIL_SCALE, DETAIL_LINE_SPACING);
    }

    @Override
    protected List<ControllerTextLine> scrollableTextLines() {
        return controllerTextLines(menu);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0,
                IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(DETAIL_SCALE, DETAIL_SCALE);
        graphics.text(font, title, (int) (titleLabelX / DETAIL_SCALE), (int) (titleLabelY / DETAIL_SCALE), STATUS_LABEL_COLOR, false);
        renderScrollableText(graphics, (int) (titleLabelX / DETAIL_SCALE));
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        extractTooltip(graphics, mouseX, mouseY);
    }

    private void renderScrollableText(GuiGraphicsExtractor graphics, int x) {
        List<ControllerScreenTextComposer.VisualLine> lines = wrappedTextLines();
        clampTextScrollOffset();
        int first = firstVisibleTextLine();
        int last = lastVisibleTextLineExclusive();
        for (int index = first; index < last; index++) {
            ControllerScreenTextComposer.VisualLine line = lines.get(index);
            int textY = detailTextY(textLineY(visibleTextRow(index)));
            graphics.text(font, line.text(), x, textY, line.color(), true);
        }
    }

    static int detailTextY(int localY) {
        return (int) (localY / DETAIL_SCALE);
    }

    static List<ControllerTextLine> controllerTextLines(MachineControllerMenu menu) {
        return ControllerScreenTextComposer.merge(detailLines(menu), ControllerScreenTextCache.linesAt(menu.controllerPos()));
    }

    static List<ControllerTextLine> detailLines(MachineControllerMenu menu) {
        List<ControllerTextLine> lines = new ArrayList<>();
        lines.add(statusLine(menu.isFormed(), menu.hasActiveRecipe()));
        for (String levelId : menu.foundLevelIds()) {
            MachineLevel level = MachineLevelRegistry.getLevel(Identifier.parse(levelId));
            if (level == null) continue;
            lines.add(new ControllerTextLine(levelLine(level), STATUS_LABEL_COLOR));
        }
        String failure = menu.lastFailureMessage();
        if (failure != null) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.last_failure",
                    Component.translatable(failure)), STATUS_LABEL_COLOR));
        }
        lines.addAll(moduleStatusLines(menu.isHostController(), menu.isModuleController(),
                menu.installedModuleCount(), menu.connectedHostId()));
        if (menu.isFormed()) {
            int parallelSlots = menu.parallelControllerCount();
            if (parallelSlots > 0) {
                lines.add(new ControllerTextLine(parallelSlotLine(parallelSlots), STATUS_LABEL_COLOR));
            }
            lines.add(new ControllerTextLine(parallelLine(menu.currentParallelism(), menu.maxParallelism()),
                    STATUS_LABEL_COLOR));
        }
        int totalTick = menu.activeRecipeTotalTick();
        if (menu.hasActiveRecipe() && totalTick > 0) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.progress",
                    progressPercent(menu.activeRecipeTick(), totalTick) + "%"), PROGRESS_STATUS_COLOR));
        }
        if (menu.isRedstonePaused()) {
            lines.add(new ControllerTextLine(Component.translatable("gui.mmcr.controller.redstone_stopped"),
                    STATUS_LABEL_COLOR));
        }
        return lines;
    }

    private static ControllerTextLine statusLine(boolean formed, boolean active) {
        return new ControllerTextLine(Component.translatable("gui.mmcr.controller.status_label")
                .append(Component.literal(" "))
                .append(Component.translatable(controllerStatusKey(formed, active))),
                controllerStatusColor(formed, active));
    }

    static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState predicate)) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), predicate.state().getBlock().getName());
    }

    static Component parallelLine(long parallelism, long maxParallelism) {
        return Component.translatable("gui.mmcr.controller.parallel", Component.literal(NUMBER_FORMAT.format(parallelism)), Component.literal(NUMBER_FORMAT.format(maxParallelism)));
    }

    static Component parallelSlotLine(int parallelSlots) {
        return Component.translatable("gui.mmcr.controller.parallel_slots", Component.literal(NUMBER_FORMAT.format(parallelSlots)));
    }

    static int progressPercent(int tick, int totalTick) {
        if (totalTick <= 0) return 0;
        return Math.clamp((int) ((long) tick * 100 / totalTick), 0, 100);
    }

    static List<ControllerTextLine> moduleStatusLines(boolean hostController, boolean moduleController, int installedModuleCount, Optional<Identifier> connectedHostId) {
        if (hostController) return List.of(new ControllerTextLine(Component.translatable("gui.mmcr.controller.installed_modules", Component.literal(NUMBER_FORMAT.format(installedModuleCount))), STATUS_LABEL_COLOR));
        if (!moduleController) return List.of();
        Component host = connectedHostId.isEmpty() ? Component.translatable("gui.mmcr.controller.module_unconnected") : Component.translatable("gui.mmcr.controller.module_connected", hostName(connectedHostId.get()));
        return List.of(new ControllerTextLine(host, connectedHostId.isPresent() ? STATUS_LABEL_COLOR : UNFORMED_STATUS_COLOR));
    }

    private static Component hostName(Identifier id) {
        var machine = MachineRegistry.getMachine(id);
        return machine == null ? Component.literal(id.toString()) : machine.displayName();
    }

    private static String controllerStatusKey(boolean formed, boolean active) {
        if (!formed) return "gui.mmcr.controller.unformed";
        return active ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle";
    }

    private static int controllerStatusColor(boolean formed, boolean active) {
        if (!formed) return UNFORMED_STATUS_COLOR;
        return active ? FORMED_STATUS_COLOR : IDLE_STATUS_COLOR;
    }

}

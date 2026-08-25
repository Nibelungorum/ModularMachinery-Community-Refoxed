package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.network.PktRecipeLockPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Screen for a machine controller menu.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineControllerScreen extends AbstractContainerScreen<MachineControllerMenu> {
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
    private static final int RECIPE_LOCK_BUTTON_SIZE = 20;
    private static final int PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR = 82;
    private static final int RECIPE_LOCK_ENABLED_BG_COLOR = 0xFF66BB6A;
    private Button recipeLockButton;

    public MachineControllerScreen(MachineControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        titleLabelX += 2;
        titleLabelY += 4;
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        Rect rect = recipeLockButtonRect(leftPos, topPos, imageWidth, imageHeight);
        recipeLockButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            ClientPacketDistributor.sendToServer(new PktRecipeLockPayload(menu.controllerPos(), 0));
            clearRecipeLockButtonFocus(button);
        }).bounds(rect.left(), rect.top(), rect.width(), rect.height()).build());
        updateRecipeLockTooltip();
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
        renderControllerStatus(graphics, (int) (titleLabelX / DETAIL_SCALE), (int) ((titleLabelY + 10) / DETAIL_SCALE));
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        updateRecipeLockTooltip();
        renderRecipeLockButtonIcon(graphics);
        extractTooltip(graphics, mouseX, mouseY);
    }

    private void renderControllerStatus(GuiGraphicsExtractor graphics, int x, int y) {
        boolean active = menu.hasActiveRecipe();
        boolean formed = menu.isFormed();
        int parallelSlots = menu.parallelControllerCount();
        int parallelism = menu.currentParallelism();
        int maxParallelism = menu.maxParallelism();
        Component label = Component.translatable("gui.mmcr.controller.status_label");
        graphics.text(font, label, x, y, STATUS_LABEL_COLOR, true);
        graphics.text(font, Component.translatable(controllerStatusKey(menu.isFormed(), active)), x + font.width(label) + 4, y,
                controllerStatusColor(formed, active), true);
        int lineY = y + DETAIL_LINE_SPACING;
        for (String levelId : menu.foundLevelIds()) {
            MachineLevel level = MachineLevelRegistry.getLevel(Identifier.parse(levelId));
            if (level == null) continue;
            graphics.text(font, levelLine(level), x, lineY, STATUS_LABEL_COLOR, true);
            lineY += DETAIL_LINE_SPACING;
        }
        String failure = menu.lastFailureMessage();
        if (failure != null) {
            graphics.text(font, Component.translatable("gui.mmcr.controller.last_failure", Component.translatable(failure)), x, lineY, STATUS_LABEL_COLOR, true);
            lineY += DETAIL_LINE_SPACING;
        }
        for (ControllerStatusLine line : moduleStatusLines(menu.isHostController(), menu.isModuleController(), menu.installedModuleCount(), menu.connectedHostId())) {
            graphics.text(font, line.text(), x, lineY, line.color(), true);
            lineY += DETAIL_LINE_SPACING;
        }
        if (formed) {
            if (parallelSlots > 0) {
                graphics.text(font, parallelSlotLine(parallelSlots), x, lineY, STATUS_LABEL_COLOR, true);
                lineY += DETAIL_LINE_SPACING;
            }
            graphics.text(font, parallelLine(parallelism, maxParallelism), x, lineY, STATUS_LABEL_COLOR, true);
            lineY += DETAIL_LINE_SPACING;
        }
        int totalTick = menu.activeRecipeTotalTick();
        if (menu.hasActiveRecipe() && totalTick > 0) {
            graphics.text(font, Component.translatable("gui.mmcr.controller.progress",
                    progressPercent(menu.activeRecipeTick(), totalTick) + "%"), x, lineY, PROGRESS_STATUS_COLOR, true);
            lineY += DETAIL_LINE_SPACING;
        }
        if (menu.isRedstonePaused()) {
            graphics.text(font, Component.translatable("gui.mmcr.controller.redstone_stopped"), x, lineY, STATUS_LABEL_COLOR, true);
        }
    }

    static Component levelLine(MachineLevel level) {
        var type = MachineLevelRegistry.getType(level.typeId());
        if (type == null || !(level.statePredicate() instanceof BlockPredicate.OfBlockState predicate)) return Component.empty();
        return Component.translatable("gui.mmcr.controller.level", type.displayName(), predicate.state().getBlock().getName());
    }

    static Component parallelLine(int parallelism, int maxParallelism) {
        return Component.translatable("gui.mmcr.controller.parallel", Component.literal(NUMBER_FORMAT.format(parallelism)), Component.literal(NUMBER_FORMAT.format(maxParallelism)));
    }

    static Component parallelSlotLine(int parallelSlots) {
        return Component.translatable("gui.mmcr.controller.parallel_slots", Component.literal(NUMBER_FORMAT.format(parallelSlots)));
    }

    static int progressPercent(int tick, int totalTick) {
        if (totalTick <= 0) return 0;
        return Math.clamp((int) ((long) tick * 100 / totalTick), 0, 100);
    }

    static List<ControllerStatusLine> moduleStatusLines(boolean hostController, boolean moduleController, int installedModuleCount, Optional<Identifier> connectedHostId) {
        if (hostController) return List.of(new ControllerStatusLine(Component.translatable("gui.mmcr.controller.installed_modules", Component.literal(NUMBER_FORMAT.format(installedModuleCount))), STATUS_LABEL_COLOR));
        if (!moduleController) return List.of();
        Component host = connectedHostId.isEmpty() ? Component.translatable("gui.mmcr.controller.module_unconnected") : Component.translatable("gui.mmcr.controller.module_connected", hostName(connectedHostId.get()));
        return List.of(new ControllerStatusLine(host, connectedHostId.isPresent() ? STATUS_LABEL_COLOR : UNFORMED_STATUS_COLOR));
    }

    private static Component hostName(Identifier id) {
        var machine = MachineRegistry.getMachine(id);
        return machine == null ? Component.literal(id.toString()) : machine.displayName();
    }

    static List<Component> recipeLockTooltip(boolean locked, String recipeId) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(locked ? "gui.mmcr.controller.recipe_lock.enabled" : "gui.mmcr.controller.recipe_lock.disabled"));
        if (locked && !recipeId.isEmpty()) lines.add(Component.translatable("gui.mmcr.controller.recipe_lock.recipe", Component.literal(recipeId)));
        return lines;
    }

    static void clearRecipeLockButtonFocus(Button button) {
        button.setFocused(false);
    }

    private static String controllerStatusKey(boolean formed, boolean active) {
        if (!formed) return "gui.mmcr.controller.unformed";
        return active ? "gui.mmcr.controller.running" : "gui.mmcr.controller.idle";
    }

    private static int controllerStatusColor(boolean formed, boolean active) {
        if (!formed) return UNFORMED_STATUS_COLOR;
        return active ? FORMED_STATUS_COLOR : IDLE_STATUS_COLOR;
    }

    private void updateRecipeLockTooltip() {
        if (recipeLockButton == null) return;
        String recipeId = menu.lockedRecipeId();
        recipeLockButton.setTooltip(Tooltip.create(tooltipComponent(recipeLockTooltip(menu.recipeLocked(), recipeId == null ? "" : recipeId))));
    }

    private void renderRecipeLockButtonIcon(GuiGraphicsExtractor graphics) {
        if (recipeLockButton == null || !recipeLockButton.visible) return;
        int x = recipeLockButton.getX();
        int y = recipeLockButton.getY();
        if (menu.recipeLocked()) graphics.fill(x, y, x + recipeLockButton.getWidth(), y + recipeLockButton.getHeight(), RECIPE_LOCK_ENABLED_BG_COLOR);
        graphics.item(new ItemStack(Items.KNOWLEDGE_BOOK), x + 2, y + 2, 0);
    }

    private static Component tooltipComponent(List<Component> lines) {
        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) result = result.copy().append("\n");
            result = result.copy().append(lines.get(index));
        }
        return result;
    }

    private static Rect recipeLockButtonRect(int left, int top, int width, int height) {
        return new Rect(left + width - RECIPE_LOCK_BUTTON_SIZE - 12, top + height - PLAYER_INVENTORY_HEIGHT_WITH_HOTBAR - RECIPE_LOCK_BUTTON_SIZE - 12, RECIPE_LOCK_BUTTON_SIZE, RECIPE_LOCK_BUTTON_SIZE);
    }

    private record Rect(int left, int top, int width, int height) {}

    record ControllerStatusLine(Component text, int color) {}
}

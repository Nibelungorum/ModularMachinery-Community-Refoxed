package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordinary combined item and fluid port screen.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CombinedPortScreen extends AbstractPortScreen<CombinedPortMenu> {
    private static final String COMBINED_TEXTURE_PATH = "textures/gui/combined/";
    private static final Identifier AUTO_IO_TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int GUI_TEXTURE_SIZE = 256;
    private static final int IMAGE_HEIGHT = 166;
    private static final int TANK_WIDTH = 20;
    private static final int TANK_HEIGHT = 61;
    private static final int TANK_FRAME_X = 176;
    private static final int TANK_FRAME_Y = 0;
    private static final int CAPABILITY_SELECTOR_X = 132;
    private static final int CAPABILITY_SELECTOR_Y = 4;
    private static final Layout LAYOUT = new Layout(CombinedPortMenu.FIRST_TANK_X,
            CombinedPortMenu.FIRST_TANK_Y, CombinedPortMenu.SECOND_TANK_X,
            CombinedPortMenu.SECOND_TANK_Y, CAPABILITY_SELECTOR_X, CAPABILITY_SELECTOR_Y,
            List.of("second_tank", "capability_selector"));

    public CombinedPortScreen(CombinedPortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_HEIGHT);
    }

    static List<Identifier> capabilityIds() {
        return List.of(BuiltinCapabilityDefinitions.ITEM_TYPE.id(), BuiltinCapabilityDefinitions.FLUID_TYPE.id());
    }

    static Layout layout() {
        return LAYOUT;
    }

    static Identifier textureForKind(String kind) {
        return MMCR.id(COMBINED_TEXTURE_PATH + kind.substring(kind.lastIndexOf('_') + 1) + ".png");
    }

    @Override
    protected BlockPos portPos() {
        return menu.pos();
    }

    @Override
    protected IOType ownerIOType() {
        return menu.owner() == null ? null : menu.owner().ioType();
    }

    @Override
    protected int portSlotCount() {
        return menu.itemSlotCount();
    }

    @Override
    protected Identifier texture(boolean autoIOPage) {
        return autoIOPage ? AUTO_IO_TEXTURE : textureForKind(menu.kind());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(autoIOPage), leftPos, topPos, 0, 0,
                imageWidth, imageHeight, GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
        if (autoIOPage) return;

        for (TankRenderOperation operation : tankRenderOperations(menu.fluidTankLayouts(), menu.fluidEntries(), texture(false))) {
            if (operation.kind() == TankRenderOperation.Kind.FILL) {
                FluidStorageEntry entry = operation.entry();
                FluidGuiRenderer.drawFluid(graphics, entry.resource().toStack((int) Math.min(entry.amount(), Integer.MAX_VALUE)),
                        leftPos + operation.x(), topPos + operation.y(), operation.width(), operation.height());
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, operation.texture(), leftPos + operation.x(), topPos + operation.y(),
                        operation.sourceX(), operation.sourceY(), operation.width(), operation.height(),
                        GUI_TEXTURE_SIZE, GUI_TEXTURE_SIZE);
            }
        }
    }

    static List<TankRenderOperation> tankRenderOperations(List<CombinedPortMenu.FluidTankLayout> layouts,
                                                           List<FluidStorageEntry> entries, Identifier texture) {
        List<TankRenderOperation> operations = new ArrayList<>();
        for (CombinedPortMenu.FluidTankLayout layout : layouts) {
            FluidStorageEntry entry = entries.stream()
                    .filter(candidate -> candidate.slot() == layout.slot()).findFirst().orElse(null);
            if (entry != null && entry.amount() > 0 && !entry.resource().isEmpty()) {
                int filled = FluidGuiRenderer.fillHeight(entry.amount(), entry.capacity(), TANK_HEIGHT);
                if (filled > 0) {
                    operations.add(new TankRenderOperation(TankRenderOperation.Kind.FILL, layout.x(),
                            layout.y() + TANK_HEIGHT - filled, TANK_WIDTH, filled, 0, 0, null, entry));
                }
            }
            operations.add(new TankRenderOperation(TankRenderOperation.Kind.FRAME, layout.x(), layout.y(),
                    TANK_WIDTH, TANK_HEIGHT, TANK_FRAME_X, TANK_FRAME_Y, texture, null));
        }
        return operations;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clearTooltipEntries();
        if (autoIOPage) return;
        for (ItemStorageEntry entry : menu.itemEntries()) {
            if (entry.amount() <= 0 || entry.resource().isEmpty() || entry.slot() >= menu.itemSlotCount()) continue;
            var slot = menu.getSlot(entry.slot());
            ItemStack stack = entry.resource().toStack((int) Math.min(entry.amount(), Integer.MAX_VALUE));
            graphics.item(stack, slot.x, slot.y, entry.slot());
        }
        for (CombinedPortMenu.FluidTankLayout layout : menu.fluidTankLayouts()) {
            FluidStorageEntry entry = menu.fluidEntries().stream()
                    .filter(candidate -> candidate.slot() == layout.slot()).findFirst().orElse(null);
            List<Component> tooltip = entry == null || entry.amount() <= 0 || entry.resource().isEmpty()
                    ? List.of(Component.literal("无"))
                    : ExtendedFluidScreen.tooltipLines(entry);
            addTooltip(leftPos + layout.x(), topPos + layout.y(), TANK_WIDTH, TANK_HEIGHT, tooltip);
        }
        renderCustomDisplays(graphics);
    }

    private void renderCustomDisplays(GuiGraphicsExtractor graphics) {
        int y = CAPABILITY_SELECTOR_Y;
        for (CapabilityDisplay display : menu.displayEntries()) {
            if (display.label().equals("item") && display.unit().equals("item")
                    || display.label().equals("fluid") && display.unit().equals("mB")) continue;
            int x = CAPABILITY_SELECTOR_X;
            if (display.icon().isPresent()) {
                graphics.item(display.icon().get().stack(), x, y, y);
                x += 18;
            }
            Component text = Component.literal(display.label() + ": " + display.value()
                    + (display.unit().isEmpty() ? "" : " " + display.unit()));
            graphics.text(font, text, x, y, 0x404040, false);
            addTooltip(leftPos + x, topPos + y, font.width(text), 10, List.of(text));
            y += 18;
        }
    }

    record Layout(int firstTankX, int firstTankY, int secondTankX, int secondTankY,
                  int capabilitySelectorX, int capabilitySelectorY,
                  List<String> reservedCoordinates) {
    }

    record TankRenderOperation(Kind kind, int x, int y, int width, int height,
                               int sourceX, int sourceY, Identifier texture, FluidStorageEntry entry) {
        enum Kind {
            FILL,
            FRAME
        }
    }
}

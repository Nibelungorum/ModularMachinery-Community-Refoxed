package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.internal.menu.SmartInterfaceMenu;
import cn.howxu.mmcr.internal.network.PktSmartInterfaceUpdatePayload;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.IllegalFormatException;

/**
 * Client presentation and editing controls for smart-interface bindings.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceScreen extends AbstractContainerScreen<SmartInterfaceMenu> {
    private static final Identifier TEXTURE = MMCR.id("textures/gui/inventory_normal.png");
    private int showing;
    private EditBox valueInput;

    public SmartInterfaceScreen(SmartInterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        valueInput = addRenderableWidget(new EditBox(font, leftPos + 53, topPos + 74, 70, 18, Component.empty()));
        valueInput.setMaxLength(16);
        addRenderableWidget(Button.builder(Component.literal("<"), button -> select(showing - 1))
                .bounds(leftPos + 20, topPos + 105, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> select(showing + 1))
                .bounds(leftPos + 116, topPos + 105, 40, 20).build());
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            sendValue();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SmartInterfaceBlockEntity.Binding binding = binding();
        if (binding == null) {
            graphics.text(font, Component.literal("No smart interface binding"), 8, 10, 0x404040, false);
            return;
        }
        SmartInterfaceType type = type(binding);
        graphics.text(font, Component.literal("Binding " + (showing + 1)), 8, 8, 0x404040, false);
        graphics.text(font, Component.literal(binding.controllerPos().toShortString()), 8, 24, 0x404040, false);
        if (type == null) return;
        graphics.text(font, Component.translatable(type.headerInfo()), 8, 40, 0x404040, false);
        graphics.text(font, Component.literal(valueInfo(type.valueInfo(), binding.value())), 8, 56, 0x404040, false);
        graphics.text(font, Component.translatable(type.footerInfo()), 8, 96, 0x404040, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    static String valueInfo(String format, float value) {
        if (format == null || format.isEmpty()) return "Value: " + value;
        try {
            return String.format(format, value);
        } catch (IllegalFormatException ignored) {
            return "Value: " + value;
        }
    }

    static Float parseFiniteValue(String value) {
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void select(int index) {
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        if (smartInterface == null || smartInterface.binding(index).isEmpty()) return;
        showing = index;
    }

    private void sendValue() {
        Float value = parseFiniteValue(valueInput.getValue());
        if (value == null || binding() == null) return;
        ClientPacketDistributor.sendToServer(new PktSmartInterfaceUpdatePayload(menu.pos(), showing, value));
        valueInput.setValue("");
    }

    private SmartInterfaceBlockEntity.Binding binding() {
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        return smartInterface == null ? null : smartInterface.binding(showing).orElse(null);
    }

    private SmartInterfaceBlockEntity smartInterface() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof SmartInterfaceBlockEntity smartInterface
                ? smartInterface : null;
    }

    private static SmartInterfaceType type(SmartInterfaceBlockEntity.Binding binding) {
        var machine = MachineDefinitions.getRegistration(binding.machineId());
        return machine == null ? null : machine.smartInterfaceTypes().get(binding.type());
    }
}

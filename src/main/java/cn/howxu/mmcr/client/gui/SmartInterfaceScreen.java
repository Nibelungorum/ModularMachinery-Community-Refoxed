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
import net.minecraft.client.input.CharacterEvent;
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
    private static final Identifier TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private int showing;
    private EditBox valueInput;
    private Button previous;
    private Button next;

    public SmartInterfaceScreen(SmartInterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        valueInput = addRenderableWidget(new EditBox(font, leftPos + 98, topPos + 35, 70, 10, Component.empty()));
        valueInput.setMaxLength(16);
        previous = addRenderableWidget(Button.builder(Component.translatable("mmcr.smart_interface.previous"), button -> select(showing - 1))
                .bounds(leftPos + 7, topPos + 58, 40, 20).build());
        next = addRenderableWidget(Button.builder(Component.translatable("mmcr.smart_interface.next"), button -> select(showing + 1))
                .bounds(leftPos + 129, topPos + 58, 40, 20).build());
        updateWidgets();
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
    public boolean charTyped(CharacterEvent event) {
        return acceptsInputCharacter(event.codepoint()) && super.charTyped(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SmartInterfaceBlockEntity.Binding binding = binding();
        if (binding == null) {
            graphics.text(font, Component.translatable("mmcr.smart_interface.empty_binding"), 7, 16, 0xFFFFFF, true);
            return;
        }
        SmartInterfaceType type = type(binding);
        graphics.text(font, Component.translatable("mmcr.smart_interface.title", showing + 1, bindingCount()), 4, 4, 0xFFFFFF, true);
        graphics.text(font, Component.literal(binding.controllerPos().toShortString()), 7, 16, 0xFFFFFF, true);
        if (type == null) return;
        graphics.text(font, Component.translatable(type.headerInfo()), 7, 26, 0xFFFFFF, true);
        graphics.text(font, Component.literal(valueInfo(type.valueInfo(), binding.value())), 7, 36, 0xFFFFFF, true);
        graphics.text(font, Component.translatable(type.footerInfo()), 7, 46, 0xFFFFFF, true);
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

    static boolean acceptsInputCharacter(int codepoint) {
        return Character.isDigit(codepoint) || codepoint == '.' || codepoint == 'E';
    }

    static int clampPage(int page, int bindingCount) {
        return bindingCount <= 0 ? 0 : Math.clamp(page, 0, bindingCount - 1);
    }

    private void select(int index) {
        showing = clampPage(index, bindingCount());
        updateWidgets();
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

    private int bindingCount() {
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        if (smartInterface == null) return 0;
        int count = 0;
        while (smartInterface.binding(count).isPresent()) count++;
        return count;
    }

    private void updateWidgets() {
        if (valueInput == null) return;
        int count = bindingCount();
        showing = clampPage(showing, count);
        boolean bound = count > 0;
        valueInput.visible = bound;
        valueInput.active = bound;
        previous.visible = bound;
        previous.active = showing > 0;
        next.visible = bound;
        next.active = showing + 1 < count;
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

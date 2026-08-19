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

import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Client presentation and editing controls for smart-interface parameters.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SmartInterfaceScreen extends AbstractContainerScreen<SmartInterfaceMenu> {
    private static final Identifier TEXTURE = MMCR.id("textures/gui/guismartinterface.png");
    private static final int CONTENT_X = 7;
    private static final int TITLE_Y = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int CONTROL_GAP = 6;
    private static final int INPUT_WIDTH = 116;
    private static final int INPUT_HEIGHT = 18;
    private static final int SAVE_X = 127;
    private static final int SAVE_WIDTH = 42;
    private static final int SAVE_HEIGHT = 20;
    private static final int NAVIGATION_WIDTH = 50;
    private static final int NAVIGATION_HEIGHT = 20;
    private static final int NEXT_X = 119;
    private static final int LABEL_COLOR = 0xFF404040;
    private int showing;
    private NumericEditBox valueInput;
    private Button save;
    private Button previous;
    private Button next;

    public SmartInterfaceScreen(SmartInterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        ControlLayout layout = controlLayout();
        valueInput = addRenderableWidget(new NumericEditBox(font, leftPos + CONTENT_X, topPos + layout.inputY(), INPUT_WIDTH, INPUT_HEIGHT,
                Component.translatable("mmcr.smart_interface.input")));
        valueInput.setMaxLength(16);
        save = addRenderableWidget(Button.builder(Component.translatable("mmcr.smart_interface.save"), button -> {
            sendValue();
            button.setFocused(false);
        })
                .bounds(leftPos + SAVE_X, topPos + layout.inputY() - 1, SAVE_WIDTH, SAVE_HEIGHT).build());
        previous = addRenderableWidget(Button.builder(Component.translatable("mmcr.smart_interface.previous"), button -> {
            select(showing - 1);
            button.setFocused(false);
        })
                .bounds(leftPos + layout.previousX(), topPos + layout.navigationY(), NAVIGATION_WIDTH, NAVIGATION_HEIGHT).build());
        next = addRenderableWidget(Button.builder(Component.translatable("mmcr.smart_interface.next"), button -> {
            select(showing + 1);
            button.setFocused(false);
        })
                .bounds(leftPos + layout.nextX(), topPos + layout.navigationY(), NAVIGATION_WIDTH, NAVIGATION_HEIGHT).build());
        updateWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            sendValue();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        extractBackground(graphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        List<String> parameterTypes = parameterTypes();
        String typeName = selectedTypeName();
        SmartInterfaceType type = selectedType();
        if (smartInterface == null || typeName == null) {
            renderLabel(graphics, Component.translatable("mmcr.smart_interface.empty_binding"), CONTENT_X, TITLE_Y + LINE_HEIGHT);
            return;
        }
        renderLabel(graphics, Component.translatable("mmcr.smart_interface.title", showing + 1, parameterTypes.size()), CONTENT_X, TITLE_Y);
        if (type == null) return;
        float value = smartInterface.value(typeName).orElse(type.defaultValue());
        renderLabel(graphics, currentValueLabel(type, value), CONTENT_X, TITLE_Y + LINE_HEIGHT);
        renderLabel(graphics, descriptionLabel(type), CONTENT_X, TITLE_Y + LINE_HEIGHT * 2);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    private void renderLabel(GuiGraphicsExtractor graphics, Component label, int x, int y) {
        graphics.text(font, label, leftPos + x, topPos + y, LABEL_COLOR, false);
    }

    static Optional<Float> parseValue(String value, SmartInterfaceType.ValueType valueType) {
        try {
            float parsed = Float.parseFloat(value);
            SmartInterfaceType.ValueType type = valueType == null ? SmartInterfaceType.ValueType.FLOAT : valueType;
            if (!Float.isFinite(parsed)) return Optional.empty();
            if (type == SmartInterfaceType.ValueType.INTEGER && parsed != Math.rint(parsed)) return Optional.empty();
            return Optional.of(parsed);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    static boolean acceptsInputCandidate(String value, SmartInterfaceType.ValueType valueType) {
        SmartInterfaceType.ValueType type = valueType == null ? SmartInterfaceType.ValueType.FLOAT : valueType;
        if (value.isEmpty()) return true;
        return switch (type) {
            case INTEGER -> value.matches("[0-9]+");
            case FLOAT -> value.matches("[0-9]+(\\.[0-9]*)?([Ee][0-9]*)?") || value.matches("\\.[0-9]*([Ee][0-9]*)?");
        };
    }

    static int clampPage(int page, int parameterCount) {
        return parameterCount <= 0 ? 0 : Math.clamp(page, 0, parameterCount - 1);
    }

    static ControlLayout controlLayout() {
        int inputY = TITLE_Y + LINE_HEIGHT * 3 + 2;
        return new ControlLayout(inputY, inputY + SAVE_HEIGHT + 2, CONTENT_X, NEXT_X);
    }

    record ControlLayout(int inputY, int navigationY, int previousX, int nextX) {
    }

    private void select(int index) {
        if (valueInput != null) valueInput.setValue("");
        showing = clampPage(index, parameterTypes().size());
        updateWidgets();
    }

    private void sendValue() {
        SmartInterfaceType type = selectedType();
        String typeName = selectedTypeName();
        if (type == null || typeName == null) return;
        Optional<Float> value = parseValue(valueInput.getValue(), type.valueType());
        if (value.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new PktSmartInterfaceUpdatePayload(menu.pos(), typeName, value.get()));
        valueInput.setValue("");
    }

    private List<String> parameterTypes() {
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        return smartInterface == null ? List.of() : smartInterface.parameterTypes();
    }

    private @Nullable String selectedTypeName() {
        List<String> types = parameterTypes();
        return showing < 0 || showing >= types.size() ? null : types.get(showing);
    }

    private @Nullable SmartInterfaceType selectedType() {
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        String typeName = selectedTypeName();
        if (smartInterface == null || typeName == null) return null;
        var machineId = smartInterface.machineId().orElse(null);
        if (machineId == null) return null;
        var machine = MachineDefinitions.getRegistration(machineId);
        return machine == null ? null : machine.smartInterfaceTypes().get(typeName);
    }

    private void updateWidgets() {
        if (valueInput == null) return;
        int count = parameterTypes().size();
        showing = clampPage(showing, count);
        boolean hasParameters = count > 0;
        SmartInterfaceType type = selectedType();
        valueInput.setValueType(type == null ? SmartInterfaceType.ValueType.FLOAT : type.valueType());
        ControlLayout layout = controlLayout();
        valueInput.setX(leftPos + CONTENT_X);
        valueInput.setY(topPos + layout.inputY());
        save.setX(leftPos + SAVE_X);
        save.setY(topPos + layout.inputY() - 1);
        previous.setX(leftPos + layout.previousX());
        previous.setY(topPos + layout.navigationY());
        next.setX(leftPos + layout.nextX());
        next.setY(topPos + layout.navigationY());
        valueInput.visible = hasParameters;
        valueInput.active = hasParameters;
        save.visible = hasParameters;
        save.active = hasParameters;
        previous.visible = count > 1;
        previous.active = showing > 0;
        next.visible = count > 1;
        next.active = showing + 1 < count;
    }

    static Component currentValueLabel(SmartInterfaceType type, float value) {
        Component name = Component.translatable(type.translationKey());
        return Component.translatable("mmcr.smart_interface.value", name, rawValue(type, value));
    }

    static Component descriptionLabel(SmartInterfaceType type) {
        return Component.translatable(type.descriptionKey());
    }

    private static String rawValue(SmartInterfaceType type, float value) {
        return type.valueType() == SmartInterfaceType.ValueType.INTEGER && value == Math.rint(value)
                ? Integer.toString((int) value)
                : Float.toString(value);
    }

    private SmartInterfaceBlockEntity smartInterface() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof SmartInterfaceBlockEntity smartInterface
                ? smartInterface : null;
    }

    private static final class NumericEditBox extends EditBox {
        private SmartInterfaceType.ValueType valueType = SmartInterfaceType.ValueType.FLOAT;

        private NumericEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            setFilter(value -> acceptsInputCandidate(value, valueType));
        }

        private void setValueType(SmartInterfaceType.ValueType valueType) {
            this.valueType = valueType == null ? SmartInterfaceType.ValueType.FLOAT : valueType;
            if (!acceptsInputCandidate(getValue(), this.valueType)) {
                setValue("");
            }
        }
    }

}

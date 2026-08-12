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
import org.jetbrains.annotations.Nullable;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Optional;

/**
 * Client presentation and editing controls for smart-interface parameters.
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
        SmartInterfaceBlockEntity smartInterface = smartInterface();
        String typeName = selectedTypeName();
        if (smartInterface == null || typeName == null) {
            graphics.text(font, Component.translatable("mmcr.smart_interface.empty_binding"), 7, 16, 0xFFFFFF, true);
            return;
        }
        SmartInterfaceType type = selectedType();
        graphics.text(font, Component.translatable("mmcr.smart_interface.title", showing + 1, parameterTypes().size()), 4, 4, 0xFFFFFF, true);
        String machineId = smartInterface.machineId().map(Identifier::toString).orElse("");
        graphics.text(font, Component.literal(machineId + " (" + smartInterface.controllerPositions().size() + ")"), 7, 16, 0xFFFFFF, true);
        if (type == null) return;
        graphics.text(font, Component.translatable(type.headerInfo()), 7, 26, 0xFFFFFF, true);
        float value = smartInterface.value(typeName).orElse(type.defaultValue());
        graphics.text(font, Component.literal(valueInfo(type, value)), 7, 36, 0xFFFFFF, true);
        graphics.text(font, Component.translatable(type.footerInfo()), 7, 46, 0xFFFFFF, true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    static String valueInfo(SmartInterfaceType type, float value) {
        boolean integer = type != null && type.valueType() == SmartInterfaceType.ValueType.INTEGER;
        String fallback = "Value: " + (integer ? Integer.toString((int) value) : Float.toString(value));
        String format = type == null ? "" : type.valueInfo();
        if (format == null || format.isEmpty()) return fallback;
        try {
            return integer ? String.format(format, (int) value) : String.format(format, value);
        } catch (IllegalFormatException ignored) {
            return fallback;
        }
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

    static boolean acceptsInputCharacter(int codepoint) {
        return Character.isDigit(codepoint) || codepoint == '.' || codepoint == 'E';
    }

    static int clampPage(int page, int parameterCount) {
        return parameterCount <= 0 ? 0 : Math.clamp(page, 0, parameterCount - 1);
    }

    private void select(int index) {
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
        valueInput.visible = hasParameters;
        valueInput.active = hasParameters;
        previous.visible = hasParameters;
        previous.active = showing > 0;
        next.visible = hasParameters;
        next.active = showing + 1 < count;
    }

    private SmartInterfaceBlockEntity smartInterface() {
        if (Minecraft.getInstance().level == null) return null;
        return Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof SmartInterfaceBlockEntity smartInterface
                ? smartInterface : null;
    }

}

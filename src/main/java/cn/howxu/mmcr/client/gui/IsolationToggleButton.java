package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Isolation control with a server-confirmed selected state.
 * @author howxu <dev@howxu.cn>
 */
public class IsolationToggleButton extends StyledButton {
    private final BooleanSupplier selected;

    public IsolationToggleButton(int x, int y, Component label, BooleanSupplier selected, Button.OnPress onPress) {
        super(x, y, 16, 16, label, onPress);
        this.selected = selected;
    }

    public void updateState(InputIsolationMenuState state, boolean controller) {
        visible = state.supported();
        active = visible;
        setTooltip(Tooltip.create(tooltip(state, controller)));
    }

    public static Component tooltip(InputIsolationMenuState state, boolean controller) {
        String prefix = controller ? "mmcr.input_isolation.full" : "mmcr.input_isolation.port";
        String statusKey = state.enabled() ? ".enabled" : ".disabled";
        Component value = Component.translatable("mmcr.input_isolation" + statusKey)
                .withStyle(state.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED);
        Component status = Component.translatable(prefix + statusKey, value);
        return Component.translatable(prefix + ".tooltip", status);
    }

    @Override
    protected void drawBackground(GuiGraphics graphics) {
        if (!selected.getAsBoolean()) {
            super.drawBackground(graphics);
            return;
        }
        int border = isHoveredOrFocused() ? 0xFFF2F3F3 : 0xFF66BB6A;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF2E7D32);
    }
}

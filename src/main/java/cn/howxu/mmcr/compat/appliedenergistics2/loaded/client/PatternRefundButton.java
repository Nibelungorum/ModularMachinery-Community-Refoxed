package cn.howxu.mmcr.compat.appliedenergistics2.loaded.client;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Native AE toolbar action. Only the server's menu update changes the indicator.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
final class PatternRefundButton extends IconButton {
    private boolean nonEmpty;

    PatternRefundButton(Runnable request) { super(button -> request.run()); }
    void updateState(boolean nonEmpty) { this.nonEmpty = nonEmpty; }
    @Override protected Icon getIcon() { return Icon.BACK; }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.renderWidget(graphics, mouseX, mouseY, partial);
        if (!visible) return;
        int y = getY() + (isHalfSize() ? 0 : 1 + (isHovered() ? 1 : 0));
        getIcon().getBlitter().color(nonEmpty ? 1f : 0f, nonEmpty ? 0f : 1f, 0f, 0.5f)
                .dest(getX(), y).zOffset(isHalfSize() ? 21 : 4).blit(graphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(Component.translatable("mmcr.pattern_refund.action"),
                Component.translatable(nonEmpty ? "mmcr.pattern_refund.has_contents" : "mmcr.pattern_refund.empty")
                        .withStyle(nonEmpty ? ChatFormatting.RED : ChatFormatting.GREEN));
    }
}

package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.compat.ars_nouveau.client.SourceGuiRenderer;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Source identity is independent of recipe direction and quantity.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceEmiStack extends EmiStack {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mmcr", "source");

    public SourceEmiStack(long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
        this.amount = amount;
    }

    @Override
    public EmiStack copy() {
        EmiStack copy = new SourceEmiStack(amount).setChance(chance).comparison(comparison);
        copy.setRemainder(getRemainder().copy());
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return amount <= 0;
    }

    @Override
    public DataComponentPatch getComponentChanges() {
        return DataComponentPatch.EMPTY;
    }

    @Override
    public Object getKey() {
        return ID;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        if (isEmpty()) return;
        if ((flags & RENDER_ICON) != 0) {
            SourceGuiRenderer.drawSource(graphics, x, y, 16, 16);
        }
        if ((flags & RENDER_AMOUNT) != 0 && amount != 1) {
            EmiRenderHelper.renderAmount(EmiDrawContext.wrap(graphics), x, y,
                    Component.literal(Long.toString(amount)));
        }
        if ((flags & RENDER_REMAINDER) != 0) {
            EmiRender.renderRemainderIcon(this, graphics, x, y);
        }
    }

    @Override
    public List<Component> getTooltipText() {
        return List.of(getName());
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        tooltip.add(EmiTooltipComponents.of(getName()));
        if (amount > 1) tooltip.add(EmiTooltipComponents.getAmount(this));
        tooltip.addAll(super.getTooltip());
        return tooltip;
    }

    @Override
    public Component getName() {
        return Component.translatable("jei.mmcr.source");
    }
}

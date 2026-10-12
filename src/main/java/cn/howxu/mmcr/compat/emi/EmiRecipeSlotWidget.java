package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.util.ReadableNumber;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** A native query slot whose drawing and interaction share the exact same visibility bounds.
 * @author howxu <dev@howxu.cn>
 */
public final class EmiRecipeSlotWidget extends SlotWidget {
    private final RecipeDisplayEntry entry;
    private final Supplier<Bounds> visibleBounds;

    public EmiRecipeSlotWidget(RecipeDisplayEntry entry, EmiIngredient ingredient, Supplier<Bounds> visibleBounds) {
        super(EmiIngredientAdapters.deepCopy(ingredient), 0, 0);
        this.entry = entry;
        this.visibleBounds = visibleBounds;
    }

    public RecipeDisplayEntry entry() { return entry; }
    public boolean visible() { return !getBounds().empty(); }
    @Override public Bounds getBounds() { return visibleBounds.get(); }
    @Override public EmiIngredient getStack() { return visible() ? EmiIngredientAdapters.deepCopy(super.getStack()) : EmiStack.EMPTY; }
    @Override public EmiRecipe getRecipe() { return visible() ? super.getRecipe() : null; }
    public String quantityText() { return visible() ? RecipeDisplayDrawing.quantityText(entry.kind(), entry.amount()) : ""; }

    public List<Component> tooltipText() {
        if (!visible()) return List.of();
        List<Component> text = new ArrayList<>(entry.tooltip());
        String key = switch (entry.kind()) {
            case ITEM -> "jei.mmcr.machine_recipe.item_count";
            case FLUID -> "jei.mmcr.machine_recipe.fluid_amount";
            case CHEMICAL -> "jei.mmcr.machine_recipe.chemical_amount";
            case SOURCE -> entry.io() == IOType.INPUT ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output";
            case TEXT -> null;
        };
        if (key != null) text.add(Component.translatable(key, ReadableNumber.formatExact(entry.amount())));
        if (entry.io() == IOType.INPUT && entry.chance() == 0F) text.add(RecipeDisplayDrawing.chanceLabel(true, 0F));
        return List.copyOf(text);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (visible()) super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public void drawStack(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!visible()) return;
        Bounds area = getBounds();
        getStack().render(graphics, area.x() + 1, area.y() + 1, delta,
                EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_INGREDIENT);
    }

    @Override public void drawOverlay(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!visible()) return;
        super.drawOverlay(graphics, mouseX, mouseY, delta);
        Bounds area = getBounds();
        RecipeDisplayDrawing.drawSlotOverlay(graphics, entry, area.x() + 1, area.y() + 1);
    }

    @Override public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        if (!visible() || !getBounds().contains(mouseX, mouseY)) return List.of();
        List<ClientTooltipComponent> tooltip = new ArrayList<>(super.getTooltip(mouseX, mouseY));
        tooltipText().forEach(text -> tooltip.add(ClientTooltipComponent.create(text.getVisualOrderText())));
        return tooltip;
    }

    @Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return visible() && getBounds().contains(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return visible() && super.keyPressed(keyCode, scanCode, modifiers);
    }
}

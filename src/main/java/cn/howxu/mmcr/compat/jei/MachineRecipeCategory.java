package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;

    private final IDrawable icon;

    public MachineRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.getSlotDrawable();
    }

    @Override
    public IRecipeType<MachineRecipeDisplay> getRecipeType() {
        return JeiMachineRecipeTypes.MACHINE_RECIPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.mmcr.machine_recipe");
    }

    @Override
    public int getWidth() {
        return MachineRecipeLayout.WIDTH;
    }

    @Override
    public int getHeight() {
        return MachineRecipeLayout.HEIGHT;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe, IFocusGroup focuses) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        for (MachineRecipeLayout.SlotPlan slot : layout.itemInputs()) {
            builder.addInputSlot(slot.x(), slot.y())
                    .setStandardSlotBackground()
                    .add(recipe.itemInputs().get(slot.index()));
        }
        for (MachineRecipeLayout.SlotPlan slot : layout.itemOutputs()) {
            builder.addOutputSlot(slot.x(), slot.y())
                    .setOutputSlotBackground()
                    .add(recipe.itemOutputs().get(slot.index()));
        }
        for (MachineRecipeLayout.SlotPlan slot : layout.fluidInputs()) {
            int amount = recipe.fluidInputAmounts().get(slot.index());
            recipe.fluidInputs().get(slot.index()).fluids().stream().findFirst().ifPresent(fluid ->
                    builder.addInputSlot(slot.x(), slot.y())
                            .setStandardSlotBackground()
                            .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, amount), true, 16, 16)
                            .add(fluid.value(), amount));
        }
        for (MachineRecipeLayout.SlotPlan slot : layout.fluidOutputs()) {
            var stack = recipe.fluidOutputs().get(slot.index());
            builder.addOutputSlot(slot.x(), slot.y())
                    .setOutputSlotBackground()
                    .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, stack.getAmount()), true, 16, 16)
                    .add(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
        }
        builder.moveRecipeTransferButton(132, 58);
    }

    @Override
    public void draw(MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        guiGraphics.text(Minecraft.getInstance().font,
                Component.translatable("jei.mmcr.machine_recipe.duration", recipe.durationTicks(), seconds(recipe.durationTicks())),
                layout.durationTextX(), layout.durationTextY(), 0xFF404040, false);

        int y = layout.durationTextY() + 10;
        for (EnergyIngredient energy : recipe.energyInputs()) {
            guiGraphics.text(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_in", energy.fePerTick()),
                    layout.durationTextX(), y, 0xFF404040, false);
            y += 10;
        }
        for (EnergyIngredient energy : recipe.energyOutputs()) {
            guiGraphics.text(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_out", energy.fePerTick()),
                    layout.durationTextX(), y, 0xFF404040, false);
            y += 10;
        }
    }

    @Override
    public @Nullable Identifier getIdentifier(MachineRecipeDisplay recipe) {
        return recipe.recipeId();
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }
}

package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.compat.jei.MachineRecipeLayout.OverflowSlotPlan;
import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;
    static final int RECIPE_ARROW_X = 64;
    static final int RECIPE_ARROW_Y = 3;

    private final Machine machine;
    private final IRecipeType<MachineRecipeDisplay> recipeType;
    private final IDrawable icon;
    private final IDrawable slotBackground;

    public MachineRecipeCategory(IGuiHelper guiHelper, Machine machine) {
        this.machine = machine;
        this.recipeType = JeiMachineRecipeTypes.forMachine(machine.registryName());
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(machine).get());
        this.slotBackground = guiHelper.getSlotDrawable();
    }

    @Override
    public IRecipeType<MachineRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(machine.localizedName());
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
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI set machine recipe layout: recipeId={}, machineId={}, itemInputs={}, fluidInputs={}, itemOutputs={}, fluidOutputs={}, energyInputs={}, energyOutputs={}, inputSlots={}, inputHidden={}, outputSlots={}, outputHidden={}",
                    recipe.recipeId(), recipe.machineId(), recipe.itemInputs().size(), recipe.fluidInputs().size(),
                    recipe.itemOutputs().size(), recipe.fluidOutputs().size(), recipe.energyInputs().size(), recipe.energyOutputs().size(),
                    layout.inputs().slots().size(), layout.inputs().hiddenEntries().size(),
                    layout.outputs().slots().size(), layout.outputs().hiddenEntries().size());
        }
        addRegion(builder, recipe, layout.inputs(), true);
        addRegion(builder, recipe, layout.outputs(), false);
        builder.moveRecipeTransferButton(132, 130);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineRecipeDisplay recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(RECIPE_ARROW_X, RECIPE_ARROW_Y);
    }

    @Override
    public void draw(MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        if (MMCR.LOG.isDebugEnabled() && (layout.hasInputOverflow() || layout.hasOutputOverflow())) {
            MMCR.LOG.debug("JEI draw machine recipe overflow markers: recipeId={}, machineId={}, mouseX={}, mouseY={}, jeiSlotViews={}, inputHidden={}, outputHidden={}",
                    recipe.recipeId(), recipe.machineId(), mouseX, mouseY, recipeSlotsView.getSlotViews().size(),
                    layout.inputs().hiddenEntries().size(), layout.outputs().hiddenEntries().size());
        }
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
        drawOverflowSlot(layout.inputs().overflowSlot(), guiGraphics, slotBackground);
        drawOverflowSlot(layout.outputs().overflowSlot(), guiGraphics, slotBackground);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        if (isMouseOver(layout.inputs().overflowSlot(), mouseX, mouseY)) {
            appendOverflowTooltip(tooltip, recipe, layout.inputs().hiddenEntries(), true);
        } else if (isMouseOver(layout.outputs().overflowSlot(), mouseX, mouseY)) {
            appendOverflowTooltip(tooltip, recipe, layout.outputs().hiddenEntries(), false);
        }
    }

    @Override
    public @Nullable Identifier getIdentifier(MachineRecipeDisplay recipe) {
        return recipe.recipeId();
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0F);
    }

    static List<ItemStack> itemStacks(Ingredient ingredient, int count) {
        return ingredient.items()
                .map(item -> item.value().getDefaultInstance().copyWithCount(count))
                .toList();
    }

    static Component overflowEntry(int amount, Component displayName) {
        return Component.translatable("jei.mmcr.machine_recipe.overflow_entry", amount, displayName);
    }

    private static void addRegion(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.RegionPlan region, boolean input) {
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI add machine recipe {} region: recipeId={}, slots={}, overflowSlot={}, hiddenEntries={}",
                    input ? "input" : "output", recipe.recipeId(), region.slots().size(), region.overflowSlot(), region.hiddenEntries().size());
        }
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            addEntry(builder, recipe, slot, input);
        }
    }

    private static void addEntry(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.SlotPlan slot, boolean input) {
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI add machine recipe entry slot: recipeId={}, region={}, entry={}, x={}, y={}",
                    recipe.recipeId(), input ? "input" : "output", slot.entry(), slot.x(), slot.y());
        }
        IRecipeSlotBuilder jeiSlot = input ? builder.addInputSlot(slot.x(), slot.y()) : builder.addOutputSlot(slot.x(), slot.y());
        jeiSlot.setStandardSlotBackground();
        switch (slot.entry().kind()) {
            case FLUID -> addFluid(jeiSlot, recipe, slot.entry(), input);
            case ITEM -> addItem(jeiSlot, recipe, slot.entry(), input);
        }
    }

    private static void addItem(IRecipeSlotBuilder jeiSlot, MachineRecipeDisplay recipe,
            MachineRecipeLayout.EntryPlan entry, boolean input) {
        if (input) {
            jeiSlot.addItemStacks(itemStacks(recipe.itemInputs().get(entry.index()), recipe.itemInputCounts().get(entry.index())));
        } else {
            jeiSlot.add(normalizeOutputStack(recipe.itemOutputs().get(entry.index())));
        }
    }

    private static void addFluid(IRecipeSlotBuilder jeiSlot, MachineRecipeDisplay recipe,
            MachineRecipeLayout.EntryPlan entry, boolean input) {
        if (input) {
            int amount = recipe.fluidInputAmounts().get(entry.index());
            recipe.fluidInputs().get(entry.index()).fluids().stream().findFirst()
                    .ifPresent(fluid -> jeiSlot.setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, amount), true, 16, 16).add(fluid.value(), amount));
        } else {
            var stack = recipe.fluidOutputs().get(entry.index());
            jeiSlot.setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, stack.getAmount()), false, 16, 16)
                    .add(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
        }
    }

    private static void appendOverflowTooltip(ITooltipBuilder tooltip,
            MachineRecipeDisplay recipe, List<MachineRecipeLayout.EntryPlan> hiddenEntries, boolean input) {
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI append machine recipe overflow tooltip: recipeId={}, region={}, hiddenEntries={}",
                    recipe.recipeId(), input ? "input" : "output", hiddenEntries.size());
        }
        tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow"));
        for (MachineRecipeLayout.EntryPlan entry : hiddenEntries) {
            if (entry.kind() == MachineRecipeLayout.Kind.ITEM) {
                if (input) {
                    int amount = recipe.itemInputCounts().get(entry.index());
                    Component displayName = itemStacks(recipe.itemInputs().get(entry.index()), amount).stream()
                            .findFirst()
                            .map(ItemStack::getHoverName)
                            .orElse(Component.empty());
                    logOverflowTooltipEntry(recipe, input, entry, amount, displayName);
                    tooltip.add(overflowEntry(amount, displayName));
                } else {
                    ItemStack stack = recipe.itemOutputs().get(entry.index());
                    logOverflowTooltipEntry(recipe, input, entry, stack.getCount(), stack.getHoverName());
                    tooltip.add(overflowEntry(stack.getCount(), stack.getHoverName()));
                }
            } else {
                if (input) {
                    int amount = recipe.fluidInputAmounts().get(entry.index());
                    Component displayName = recipe.fluidInputs().get(entry.index()).fluids().stream()
                            .findFirst()
                            .map(fluid -> new FluidStack(fluid.value(), amount).getHoverName())
                            .orElse(Component.empty());
                    logOverflowTooltipEntry(recipe, input, entry, amount, displayName);
                    tooltip.add(overflowEntry(amount, displayName));
                } else {
                    var fluidStack = recipe.fluidOutputs().get(entry.index());
                    logOverflowTooltipEntry(recipe, input, entry, fluidStack.getAmount(), fluidStack.getHoverName());
                    tooltip.add(overflowEntry(fluidStack.getAmount(), fluidStack.getHoverName()));
                }
            }
        }
    }

    private static void logOverflowTooltipEntry(MachineRecipeDisplay recipe, boolean input,
            MachineRecipeLayout.EntryPlan entry, int amount, Component displayName) {
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI overflow tooltip entry: recipeId={}, region={}, entry={}, amount={}, displayName={}",
                    recipe.recipeId(), input ? "input" : "output", entry, amount, displayName.getString());
        }
    }

    private static void drawOverflowSlot(@Nullable OverflowSlotPlan slot,
            GuiGraphicsExtractor guiGraphics, IDrawable slotBackground) {
        if (slot != null) {
            slotBackground.draw(guiGraphics, slot.x() - 1, slot.y() - 1);
            guiGraphics.text(Minecraft.getInstance().font, "...", slot.x(), slot.y() + 4, 0xFF404040, false);
        }
    }

    private static boolean isMouseOver(@Nullable OverflowSlotPlan slot, double mouseX, double mouseY) {
        return slot != null && mouseX >= slot.x() && mouseX < slot.x() + 16 && mouseY >= slot.y() && mouseY < slot.y() + 16;
    }

    private static ItemStack normalizeOutputStack(ItemStack stack) {
        if (stack.isComponentsPatchEmpty()) {
            return new ItemStack(stack.getItem(), stack.getCount());
        }
        return stack;
    }
}

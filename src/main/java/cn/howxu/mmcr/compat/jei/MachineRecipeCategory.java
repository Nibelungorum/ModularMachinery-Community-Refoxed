package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;

    private final Machine machine;
    private final IRecipeType<MachineRecipeDisplay> recipeType;
    private final IDrawable icon;

    public MachineRecipeCategory(IGuiHelper guiHelper, Machine machine) {
        this.machine = machine;
        this.recipeType = JeiMachineRecipeTypes.forMachine(machine.registryName());
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(machine).get());
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
        addRegion(builder, recipe, layout.inputs(), true);
        addRegion(builder, recipe, layout.outputs(), false);
        builder.moveRecipeTransferButton(84, 150);
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
        for (MachineRecipeLayout.SlotPlan slot : layout.inputs().slots()) {
            if (slot.entry() == null) guiGraphics.text(Minecraft.getInstance().font, "...", slot.x(), slot.y() + 4, 0xFF404040, false);
        }
        for (MachineRecipeLayout.SlotPlan slot : layout.outputs().slots()) {
            if (slot.entry() == null) guiGraphics.text(Minecraft.getInstance().font, "...", slot.x(), slot.y() + 4, 0xFF404040, false);
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

    static Optional<ItemStack> firstItemStack(Ingredient ingredient, int count) {
        return firstItemStack(itemStacks(ingredient, count));
    }

    static Optional<ItemStack> firstItemStack(List<ItemStack> stacks) {
        return stacks.stream().findFirst();
    }

    private static void addRegion(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.RegionPlan region, boolean input) {
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            if (slot.entry() == null) {
                IRecipeSlotBuilder jeiSlot = input ? builder.addInputSlot(slot.x(), slot.y()) : builder.addOutputSlot(slot.x(), slot.y());
                jeiSlot.setStandardSlotBackground()
                        .addRichTooltipCallback((view, tooltip) -> appendOverflowTooltip(tooltip, recipe, region.hiddenEntries(), input));
            } else {
                addEntry(builder, recipe, slot, input);
            }
        }
    }

    private static void addEntry(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.SlotPlan slot, boolean input) {
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
        tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow"));
        for (MachineRecipeLayout.EntryPlan entry : hiddenEntries) {
            if (entry.kind() == MachineRecipeLayout.Kind.ITEM) {
                if (input) {
                    firstItemStack(recipe.itemInputs().get(entry.index()), recipe.itemInputCounts().get(entry.index()))
                            .ifPresent(stack -> tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow_entry", stack.getCount(), stack.getHoverName())));
                } else {
                    ItemStack stack = recipe.itemOutputs().get(entry.index());
                    tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow_entry", stack.getCount(), stack.getHoverName()));
                }
            } else {
                if (input) {
                    var fluid = recipe.fluidInputs().get(entry.index()).fluids().stream().findFirst().orElseThrow().value();
                    int amount = recipe.fluidInputAmounts().get(entry.index());
                    tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow_entry", amount, new FluidStack(fluid, amount).getHoverName()));
                } else {
                    var fluidStack = recipe.fluidOutputs().get(entry.index());
                    tooltip.add(Component.translatable("jei.mmcr.machine_recipe.overflow_entry", fluidStack.getAmount(), fluidStack.getHoverName()));
                }
            }
        }
    }

    private static ItemStack normalizeOutputStack(ItemStack stack) {
        if (stack.isComponentsPatchEmpty()) {
            return new ItemStack(stack.getItem(), stack.getCount());
        }
        return stack;
    }
}

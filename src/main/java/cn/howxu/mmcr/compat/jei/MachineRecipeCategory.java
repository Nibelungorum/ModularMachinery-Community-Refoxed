package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.registry.ModBlocks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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
        for (MachineRecipeLayout.SlotPlan slot : layout.inputs().slots()) {
            MachineRecipeLayout.EntryPlan entry = slot.entry();
            if (entry == null) continue;
            if (entry.kind() == MachineRecipeLayout.Kind.ITEM) {
                Ingredient ingredient = recipe.itemInputs().get(entry.index());
                int count = recipe.itemInputCounts().get(entry.index());
                List<ItemStack> stacks = itemStacks(ingredient, count);
                MMCR.LOG.info("JEI input slot recipe={} machine={} index={} position=({}, {}) requestedCount={} ingredient={} candidateCount={} stacks={}",
                        recipe.recipeId(), recipe.machineId(), entry.index(), slot.x(), slot.y(), count, ingredient,
                        stacks.size(), stacks.stream().map(MachineRecipeCategory::describeStack).toList());
                builder.addInputSlot(slot.x(), slot.y())
                        .setStandardSlotBackground()
                        .addItemStacks(stacks);
            } else {
                int amount = recipe.fluidInputAmounts().get(entry.index());
                recipe.fluidInputs().get(entry.index()).fluids().stream().findFirst().ifPresent(fluid ->
                        builder.addInputSlot(slot.x(), slot.y())
                                .setStandardSlotBackground()
                                .setFluidRenderer(Math.max(FLUID_SLOT_CAPACITY, amount), true, 16, 16)
                                .add(fluid.value(), amount));
            }
        }
        for (MachineRecipeLayout.SlotPlan slot : layout.outputs().slots()) {
            MachineRecipeLayout.EntryPlan entry = slot.entry();
            if (entry == null) continue;
            if (entry.kind() == MachineRecipeLayout.Kind.ITEM) {
                ItemStack rawStack = recipe.itemOutputs().get(entry.index());
                ItemStack stack = normalizeOutputStack(rawStack);
                MMCR.LOG.info("JEI output slot recipe={} machine={} index={} position=({}, {}) rawStack={} jeiStack={}",
                        recipe.recipeId(), recipe.machineId(), entry.index(), slot.x(), slot.y(), describeStack(rawStack), describeStack(stack));
                builder.addOutputSlot(slot.x(), slot.y())
                        .setOutputSlotBackground()
                        .add(stack);
            } else {
                var stack = recipe.fluidOutputs().get(entry.index());
                builder.addOutputSlot(slot.x(), slot.y())
                        .setOutputSlotBackground()
                        .setFluidRenderer(1, false, 16, 16)
                        .add(stack.getFluid(), stack.getAmount(), stack.getComponentsPatch());
            }
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

    static List<ItemStack> itemStacks(Ingredient ingredient, int count) {
        return ingredient.items()
                .map(item -> item.value().getDefaultInstance().copyWithCount(count))
                .toList();
    }

    private static String describeStack(ItemStack stack) {
        return "{item=" + stack.getItem() + ", count=" + stack.getCount()
                + ", empty=" + stack.isEmpty() + ", components=" + stack.getComponents() + "}";
    }

    private static ItemStack normalizeOutputStack(ItemStack stack) {
        if (stack.isComponentsPatchEmpty()) {
            return new ItemStack(stack.getItem(), stack.getCount());
        }
        return stack;
    }
}

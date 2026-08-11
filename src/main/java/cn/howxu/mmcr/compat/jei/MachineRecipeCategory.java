package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
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
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;
    private static final int OVERFLOW_TEXT_OFFSET_X = 5;
    static final int RECIPE_ARROW_X = 64;
    static final int RECIPE_ARROW_Y = 8;
    static final int ITEM_OVERLAY_X = -3;
    static final int ITEM_OVERLAY_Y = -3;
    static final float ITEM_OVERLAY_SCALE = 0.6F;

    private final Machine machine;
    private final IRecipeType<MachineRecipeDisplay> recipeType;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final IGuiHelper guiHelper;

    public MachineRecipeCategory(IGuiHelper guiHelper, Machine machine) {
        this.guiHelper = guiHelper;
        this.machine = machine;
        this.recipeType = JeiMachineRecipeTypes.forMachine(machine.registryName());
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(machine.registryName()).get());
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
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        for (LevelRequirement requirement : sortedLevelRequirements(recipe.recipe())) {
            guiGraphics.text(Minecraft.getInstance().font, levelRequirement(requirement, gameTime),
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

    static String inputOverlayText(float consumeChance, String language) {
        if (consumeChance == 0F) return language.startsWith("zh") ? "不消耗" : "Keep";
        return consumeChance < 1F ? Math.round(consumeChance * 100F) + "%" : "";
    }

    static Component overflowEntry(int amount, Component displayName) {
        return Component.translatable("jei.mmcr.machine_recipe.overflow_entry", amount, displayName);
    }

    static Component outputStackName(ItemStack stack) {
        Component hoverName = stack.getHoverName();
        if (!hoverName.getString().isEmpty()) {
            return hoverName;
        }
        return Component.translatable(stack.getItem().getDescriptionId());
    }

    static Component levelRequirement(LevelRequirement requirement, long gameTime) {
        MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
        var type = MachineLevelRegistry.getType(requirement.typeId());
        if (required == null || type == null) return Component.empty();
        List<MachineLevel> eligible = MachineLevelRegistry.levelsForType(requirement.typeId()).stream()
                .filter(level -> level.priority() >= required.priority())
                .sorted(Comparator.comparingInt(MachineLevel::priority))
                .toList();
        if (eligible.isEmpty()) return Component.empty();
        int cycleLength = eligible.size() * 2 - 2;
        int index = cycleLength == 0 ? 0 : (int) ((gameTime / 20) % cycleLength);
        if (index >= eligible.size()) index = cycleLength - index;
        MachineLevel selected = eligible.get(index);
        Component levelName = selected.statePredicate() instanceof BlockPredicate.OfBlockState predicate
                ? predicate.state().getBlock().getName()
                : selected.representative().getHoverName();
        Component suffix = selected.id().equals(required.id())
                ? Component.translatable("jei.mmcr.machine_recipe.minimum_level")
                : Component.empty();
        return type.displayName().copy()
                .append(Component.literal(": "))
                .append(levelName)
                .append(suffix);
    }

    private static List<LevelRequirement> sortedLevelRequirements(MachineRecipe recipe) {
        return recipe.levelRequirements().stream()
                .sorted(Comparator.comparing((LevelRequirement requirement) -> requirement.typeId().toString())
                        .thenComparing(requirement -> requirement.levelId().toString()))
                .toList();
    }

    private static void addRegion(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.RegionPlan region, boolean input) {
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            addEntry(builder, recipe, slot, input);
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
            MachineRecipeDisplay.ItemInputDisplay item = recipe.itemInputs().get(entry.index());
            jeiSlot.addItemStacks(item.stacks());
            String overlayText = inputOverlayText(item.consumeChance(), Minecraft.getInstance().getLanguageManager().getSelected());
            if (!overlayText.isEmpty()) {
                jeiSlot.setOverlay(new TextOverlayDrawable(overlayText, 0xFFFF4040, ITEM_OVERLAY_SCALE),
                        ITEM_OVERLAY_X, ITEM_OVERLAY_Y);
            }
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendInputTooltip(tooltip, item));
        } else {
            ItemStack stack = recipe.itemOutputs().get(entry.index());
            jeiSlot.add(new ItemStack(stack.getItem().builtInRegistryHolder(), stack.getCount(), stack.getComponentsPatch()));
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
        tooltip.add(Component.translatable(input
                ? "jei.mmcr.machine_recipe.input_overflow"
                : "jei.mmcr.machine_recipe.output_overflow"));
        for (MachineRecipeLayout.EntryPlan entry : hiddenEntries) {
            if (entry.kind() == MachineRecipeLayout.Kind.ITEM) {
                if (input) {
                    MachineRecipeDisplay.ItemInputDisplay item = recipe.itemInputs().get(entry.index());
                    Component displayName = item.stacks().stream()
                            .findFirst()
                            .map(ItemStack::getHoverName)
                            .orElse(Component.empty());
                    tooltip.add(overflowEntry(item.count(), displayName));
                } else {
                    ItemStack stack = recipe.itemOutputs().get(entry.index());
                    tooltip.add(overflowEntry(stack.getCount(), outputStackName(stack)));
                }
            } else {
                if (input) {
                    int amount = recipe.fluidInputAmounts().get(entry.index());
                    Component displayName = recipe.fluidInputs().get(entry.index()).fluids().stream()
                            .findFirst()
                            .map(fluid -> new FluidStack(fluid.value(), amount).getHoverName())
                            .orElse(Component.empty());
                    tooltip.add(overflowEntry(amount, displayName));
                } else {
                    var fluidStack = recipe.fluidOutputs().get(entry.index());
                    tooltip.add(overflowEntry(fluidStack.getAmount(), fluidStack.getHoverName()));
                }
            }
        }
    }

    private static void drawOverflowSlot(@Nullable OverflowSlotPlan slot,
            GuiGraphicsExtractor guiGraphics, IDrawable slotBackground) {
        if (slot != null) {
            slotBackground.draw(guiGraphics, slot.x() - 1, slot.y() - 1);
            guiGraphics.text(Minecraft.getInstance().font, "...", slot.x() + OVERFLOW_TEXT_OFFSET_X, slot.y() + 4, 0xFF404040, false);
        }
    }

    private static void appendInputTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay.ItemInputDisplay item) {
        if (item.consumeChance() == 0F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.keep"));
        } else if (item.consumeChance() < 1F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.consume_chance",
                    Math.round(item.consumeChance() * 100F) + "%"));
        }
        if (item.hasUnexportedComponentConstraints()) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.component_constraints"));
        }
    }

    private static boolean isMouseOver(@Nullable OverflowSlotPlan slot, double mouseX, double mouseY) {
        return slot != null && mouseX >= slot.x() && mouseX < slot.x() + 16 && mouseY >= slot.y() && mouseY < slot.y() + 16;
    }

    private record TextOverlayDrawable(String text, int color, float scale) implements IDrawable {
        @Override
        public int getWidth() {
            return (int) Math.ceil(Minecraft.getInstance().font.width(text) * scale);
        }

        @Override
        public int getHeight() {
            return (int) Math.ceil(Minecraft.getInstance().font.lineHeight * scale);
        }

        @Override
        public void draw(GuiGraphicsExtractor guiGraphics, int xOffset, int yOffset) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(xOffset / scale, yOffset / scale);
            guiGraphics.pose().scale(scale, scale);
            guiGraphics.text(Minecraft.getInstance().font, text, 0, 0, color, false);
            guiGraphics.pose().popMatrix();
        }
    }
}

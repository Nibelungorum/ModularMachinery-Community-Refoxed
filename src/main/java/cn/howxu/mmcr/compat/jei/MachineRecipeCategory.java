package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.compat.jei.MachineRecipeLayout.OverflowSlotPlan;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.ReadableNumber;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;
    private static final int OVERFLOW_TEXT_OFFSET_X = 5;
    private static final float TEXT_SCALE = 0.85F;
    private static final int TEXT_LINE_SPACING = 10;
    private static final float SMART_INTERFACE_TEXT_SCALE = 0.85F;
    private static final int SMART_INTERFACE_LINE_SPACING = 10;
    static final int RECIPE_ARROW_X = 72;
    static final int RECIPE_ARROW_Y = 8;
    static final int ITEM_OVERLAY_X = 0;
    static final int ITEM_OVERLAY_Y = 0;
    static final float ITEM_OVERLAY_SCALE = 0.6F;
    private static final IIngredientRenderer<FluidStack> FULL_FLUID_RENDERER = new IIngredientRenderer<>() {
        @Override
        public void render(GuiGraphicsExtractor guiGraphics, FluidStack fluid) {
            FluidGuiRenderer.drawFluid(guiGraphics, fluid, 0, 0, 16, 16);
        }

        @Override
        public List<Component> getTooltip(FluidStack fluid, TooltipFlag tooltipFlag) {
            Minecraft minecraft = Minecraft.getInstance();
            return fluidTooltip(fluid, Item.TooltipContext.of(minecraft.level), minecraft.player, tooltipFlag);
        }
    };

    private final Component title;
    private final IRecipeType<MachineRecipeDisplay> recipeType;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final IGuiHelper guiHelper;

    public MachineRecipeCategory(IGuiHelper guiHelper, Machine machine) {
        this(guiHelper, machine.registryName(), machine.displayName());
    }

    public MachineRecipeCategory(IGuiHelper guiHelper, Identifier machineId, Component title) {
        this.guiHelper = guiHelper;
        this.title = title;
        this.recipeType = JeiMachineRecipeTypes.forMachine(machineId);
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(machineId).get());
        this.slotBackground = guiHelper.getSlotDrawable();
    }

    @Override
    public IRecipeType<MachineRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 168;
            case 2 -> 168;
            case 3 -> 168;
            default -> 168;
        };
    }

    @Override
    public int getHeight() {
        return switch ((int) Minecraft.getInstance().getWindow().getGuiScale()) {
            case 1 -> 300;
            case 2 -> 280;
            case 3 -> 220;
            default -> 150;
        };
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
        addTransferSlots(builder, recipe);
        builder.moveRecipeTransferButton(layout.transferButtonX(), layout.transferButtonY() - 3);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineRecipeDisplay recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200).setPosition(RECIPE_ARROW_X, RECIPE_ARROW_Y);
    }

    @Override
    public void draw(MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE);
        int textX = (int) (layout.durationTextX() / TEXT_SCALE);
        guiGraphics.text(Minecraft.getInstance().font,
                Component.translatable("jei.mmcr.machine_recipe.duration", recipe.durationTicks(), seconds(recipe.durationTicks())),
                textX, (int) (layout.durationTextY() / TEXT_SCALE), 0xFF404040, false);

        int y = layout.durationTextY() + TEXT_LINE_SPACING;
        for (EnergyIngredient energy : recipe.energyInputs()) {
            guiGraphics.text(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_in", ReadableNumber.format(energy.fePerTick()),
                            ReadableNumber.format((long) energy.fePerTick() * recipe.durationTicks())),
                    textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        for (EnergyIngredient energy : recipe.energyOutputs()) {
            guiGraphics.text(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_out", ReadableNumber.format(energy.fePerTick())),
                    textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        Component hostRequirement = hostRequirementComponent(recipe, gameTime);
        if (!hostRequirement.getString().isEmpty()) {
            y = layout.hostRequirementTextY();
            guiGraphics.text(Minecraft.getInstance().font, hostRequirement, textX,
                    (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        for (LevelRequirement requirement : sortedLevelRequirements(recipe.recipe())) {
            guiGraphics.text(Minecraft.getInstance().font, levelRequirement(requirement, gameTime),
                    textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        y = layout.smartInterfaceTextY(recipe);
        guiGraphics.pose().popMatrix();
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(SMART_INTERFACE_TEXT_SCALE, SMART_INTERFACE_TEXT_SCALE);
        textX = (int) (layout.durationTextX() / SMART_INTERFACE_TEXT_SCALE);
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceInputs()) {
            guiGraphics.text(Minecraft.getInstance().font, smartInterface.label(),
                    textX, (int) (y / SMART_INTERFACE_TEXT_SCALE), 0xFF404040, false);
            y += SMART_INTERFACE_LINE_SPACING;
        }
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceOutputs()) {
            guiGraphics.text(Minecraft.getInstance().font, smartInterface.label(),
                    textX, (int) (y / SMART_INTERFACE_TEXT_SCALE), 0xFF404040, false);
            y += SMART_INTERFACE_LINE_SPACING;
        }
        guiGraphics.pose().popMatrix();
        drawTextEntries(recipe, layout.inputs(), true, guiGraphics);
        drawTextEntries(recipe, layout.outputs(), false, guiGraphics);
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
        } else {
            smartInterfaceTooltip(recipe, layout, mouseX, mouseY).ifPresent(tooltip::add);
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

    static String outputOverlayText(float chance) {
        return chance < 1F ? Math.round(chance * 100F) + "%" : "";
    }

    static Component overflowEntry(int amount, Component displayName) {
        return Component.translatable("jei.mmcr.machine_recipe.overflow_entry", ReadableNumber.format(amount), displayName);
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

    static Component hostRequirementComponent(MachineRecipeDisplay recipe, long gameTime) {
        if (recipe.requiredHostIds().isEmpty()) return Component.empty();
        List<Identifier> hostIds = List.copyOf(recipe.requiredHostIds());
        int index = (int) ((gameTime / 20) % hostIds.size());
        Identifier hostId = hostIds.get(index);
        var registration = MachineDefinitions.getRegistration(hostId);
        Component hostName = registration == null ? Component.literal(hostId.toString()) : registration.displayName();
        return Component.translatable("jei.mmcr.machine_recipe.required_host", hostName);
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
        IRecipeSlotBuilder jeiSlot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY, slot.x(), slot.y());
        jeiSlot.setStandardSlotBackground();
        Optional<JeiDisplayEntry> entry = displayEntry(recipe, slot.entry(), input);
        if (entry.isEmpty()) return;
        if (entry.get().typeId().equals(ItemRequirement.TYPE.id())) {
            addItem(jeiSlot, recipe, slot.entry(), input);
        } else if (entry.get().typeId().equals(FluidRequirement.TYPE.id())) {
            addFluid(jeiSlot, recipe, slot.entry(), entry.get(), input);
        } else {
            addGeneric(jeiSlot, entry.get());
        }
    }

    private static Optional<JeiDisplayEntry> displayEntry(MachineRecipeDisplay recipe,
            MachineRecipeLayout.EntryPlan plan, boolean input) {
        if (plan.displayEntry() != null) return Optional.of(plan.displayEntry());
        var type = switch (plan.kind()) {
            case ITEM -> VanillaTypes.ITEM_STACK;
            case FLUID -> NeoForgeTypes.FLUID_STACK;
            case GENERIC, TEXT -> null;
        };
        return recipe.entries().stream()
                .filter(entry -> entry.role() == (input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT))
                .filter(entry -> entry.ingredientType() == type)
                .skip(plan.index())
                .findFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addGeneric(IRecipeSlotBuilder slot, JeiDisplayEntry entry) {
        if (entry.isTextOnly()) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add((Component) entry.ingredient()));
            return;
        }
        String quantity = entry.ingredientType() == VanillaTypes.ITEM_STACK
                ? itemQuantityText(entry.count()) : fluidQuantityText(entry.count());
        if (entry.ingredientType() == VanillaTypes.ITEM_STACK) {
            String chance = entry.role() == RecipeIngredientRole.INPUT
                    ? inputOverlayText(entry.chance(), selectedLanguage())
                    : outputOverlayText(entry.chance());
            setItemOverlay(slot, chance, quantity);
        } else {
            setQuantityOverlay(slot, quantity);
        }
        if (entry.renderer() != null) {
            slot.setCustomRenderer((mezz.jei.api.ingredients.IIngredientType) entry.ingredientType(), entry.renderer());
        }
        if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) {
            slot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER);
        }
        if (entry.ingredient() instanceof List<?> ingredients) {
            slot.addIngredients((mezz.jei.api.ingredients.IIngredientType) entry.ingredientType(), ingredients);
            return;
        }
        slot.add((mezz.jei.api.ingredients.IIngredientType) entry.ingredientType(), entry.ingredient());
    }

    private static void drawTextEntries(MachineRecipeDisplay recipe, MachineRecipeLayout.RegionPlan region,
            boolean input, GuiGraphicsExtractor guiGraphics) {
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            if (slot.entry().kind() != MachineRecipeLayout.Kind.TEXT) continue;
            displayEntry(recipe, slot.entry(), input).ifPresent(entry -> guiGraphics.text(Minecraft.getInstance().font,
                    (Component) entry.ingredient(), slot.x(), slot.y() + 4, 0xFF404040, false));
        }
    }

    private static void addTransferSlots(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe) {
        for (int index = 0; index < recipe.fluidInputs().size(); index++) {
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            int amount = recipe.fluidInputAmounts().get(index);
            recipe.fluidInputs().get(index).fluids()
                    .forEach(fluid -> slot.add(fluid.value(), amount));
        }
        for (MachineRecipeDisplay.ItemInputDisplay item : recipe.itemInputs()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            addActualItem(slot, item);
        }
        for (FluidStack fluid : recipe.fluidOutputs()) {
            builder.addOutputSlot(-1000, -1000)
                    .add(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        }
        for (MachineRecipeDisplay.ItemOutputDisplay output : recipe.itemOutputs()) {
            builder.addOutputSlot(-1000, -1000).add(output.stack());
        }
        for (JeiDisplayEntry entry : recipe.entries()) {
            if (entry.typeId().equals(ItemRequirement.TYPE.id())
                    || entry.typeId().equals(FluidRequirement.TYPE.id())) continue;
            if (entry.role() != RecipeIngredientRole.INPUT || !entry.transferable()) continue;
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            addGeneric(slot, entry);
        }
    }

    private static void addActualItem(IRecipeSlotBuilder slot, MachineRecipeDisplay.ItemInputDisplay item) {
        List<ItemStack> stacks = item.stacks();
        if (stacks.isEmpty() && item.ingredient() != null) {
            slot.add(item.ingredient());
        } else {
            slot.addItemStacks(stacks);
        }
    }

    private static void addItem(IRecipeSlotBuilder jeiSlot, MachineRecipeDisplay recipe,
            MachineRecipeLayout.EntryPlan entry, boolean input) {
        if (input) {
            MachineRecipeDisplay.ItemInputDisplay item = recipe.itemInputs().get(entry.index());
            String overlayText = inputOverlayText(item.consumeChance(), selectedLanguage());
            setItemOverlay(jeiSlot, overlayText, itemQuantityText(item.count()));
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendInputTooltip(tooltip, item));
            List<ItemStack> stacks = item.stacks().stream().map(MachineRecipeCategory::jeiItemStack).toList();
            if (stacks.isEmpty() && item.ingredient() != null) {
                jeiSlot.add(item.ingredient());
            } else {
                jeiSlot.addItemStacks(stacks);
            }
        } else {
            MachineRecipeDisplay.ItemOutputDisplay output = recipe.itemOutputs().get(entry.index());
            ItemStack stack = output.stack();
            String overlayText = outputOverlayText(output.chance());
            setItemOverlay(jeiSlot, overlayText, itemQuantityText(stack.getCount()));
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendOutputTooltip(tooltip, output));
            jeiSlot.add(jeiItemStack(stack));
        }
    }

    static ItemStack jeiItemStack(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    static String describeAddedItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "<empty>";
        String itemId = stack.getItem().builtInRegistryHolder().getRegisteredName();
        String components = stack.getComponents().keySet().stream()
                .map(type -> type + "=" + stack.get(type))
                .collect(Collectors.joining(", ", "[", "]"));
        String patch = stack.getComponentsPatch().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
        return itemId + " x" + stack.getCount()
                + " name=" + stack.getHoverName().getString()
                + " components=" + components
                + " patch=" + patch;
    }

    private static void addFluid(IRecipeSlotBuilder jeiSlot, MachineRecipeDisplay recipe,
            MachineRecipeLayout.EntryPlan entry, JeiDisplayEntry display, boolean input) {
        if (input) {
            int amount = recipe.fluidInputAmounts().get(entry.index());
            if (!(display.ingredient() instanceof FluidStack fluid) || fluid.isEmpty()) return;
            setQuantityOverlay(jeiSlot, fluidQuantityText(amount));
            jeiSlot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER)
                    .add(fluid.getFluid(), FLUID_SLOT_CAPACITY, fluid.getComponentsPatch());
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendFluidQuantityTooltip(tooltip, amount));
        } else {
            var stack = recipe.fluidOutputs().get(entry.index());
            setQuantityOverlay(jeiSlot, fluidQuantityText(stack.getAmount()));
            jeiSlot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER)
                    .add(stack.getFluid(), FLUID_SLOT_CAPACITY, stack.getComponentsPatch());
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendFluidQuantityTooltip(tooltip, stack.getAmount()));
        }
    }

    private static String selectedLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? "" : minecraft.getLanguageManager().getSelected();
    }

    private static void setItemOverlay(IRecipeSlotBuilder jeiSlot, String chanceText, String quantityText) {
        if (!chanceText.isEmpty() || !quantityText.isEmpty()) {
            jeiSlot.setOverlay(new JeiSlotOverlayDrawable(chanceText, quantityText), ITEM_OVERLAY_X, ITEM_OVERLAY_Y);
        }
    }

    private static void setQuantityOverlay(IRecipeSlotBuilder jeiSlot, String quantityText) {
        if (!quantityText.isEmpty()) {
            jeiSlot.setOverlay(new JeiSlotOverlayDrawable("", quantityText), ITEM_OVERLAY_X, ITEM_OVERLAY_Y);
        }
    }

    static String itemQuantityText(long count) {
        return count > 1 ? ReadableNumber.formatForSlot(count, 0, "") : "";
    }

    static String fluidQuantityText(int amount) {
        return amount <= 10
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
    }

    static List<Component> fluidTooltip(FluidStack fluid, Item.TooltipContext context,
            @Nullable Player player, TooltipFlag tooltipFlag) {
        return fluid.getTooltipLines(context, player, tooltipFlag);
    }

    static String itemTooltipQuantity(int count) {
        return count > 1 ? ReadableNumber.formatExact(count) : "";
    }

    static String fluidTooltipQuantity(int amount) {
        return amount <= 10
                ? ReadableNumber.formatExact(amount) + "mB"
                : fluidBucketText(amount, 3);
    }

    private static String fluidBucketText(int amount, int decimalPlaces) {
        return BigDecimal.valueOf(amount, 3)
                .setScale(decimalPlaces, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString() + "B";
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
                    MachineRecipeDisplay.ItemOutputDisplay output = recipe.itemOutputs().get(entry.index());
                    ItemStack stack = output.stack();
                    tooltip.add(overflowEntry(stack.getCount(), outputStackName(stack)));
                }
            } else if (entry.kind() == MachineRecipeLayout.Kind.FLUID) {
                if (input) {
                    int amount = recipe.fluidInputAmounts().get(entry.index());
                    Component displayName = entry.displayEntry() != null
                            && entry.displayEntry().ingredient() instanceof FluidStack fluid
                            && !fluid.isEmpty()
                            ? fluid.copyWithAmount(amount).getHoverName()
                            : Component.empty();
                    tooltip.add(overflowEntry(amount, displayName));
                } else {
                    var fluidStack = recipe.fluidOutputs().get(entry.index());
                    tooltip.add(overflowEntry(fluidStack.getAmount(), fluidStack.getHoverName()));
                }
            } else if (entry.displayEntry() != null) {
                Object ingredient = entry.displayEntry().ingredient();
                Component name = ingredient instanceof Component component
                        ? component
                        : Component.literal(String.valueOf(ingredient));
                tooltip.add(overflowEntry(entry.displayEntry().count(), name));
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
        String quantity = itemTooltipQuantity(item.count());
        if (!quantity.isEmpty()) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.item_count", quantity));
        }
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

    private static void appendOutputTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay.ItemOutputDisplay output) {
        String quantity = itemTooltipQuantity(output.stack().getCount());
        if (!quantity.isEmpty()) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.item_count", quantity));
        }
        if (output.chance() < 1F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.output_chance",
                    Math.round(output.chance() * 100F) + "%"));
        }
    }

    private static void appendFluidQuantityTooltip(ITooltipBuilder tooltip, int amount) {
        String quantity = fluidTooltipQuantity(amount);
        if (!quantity.isEmpty()) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.fluid_amount", quantity));
        }
    }

    private static boolean isMouseOver(@Nullable OverflowSlotPlan slot, double mouseX, double mouseY) {
        return slot != null && mouseX >= slot.x() && mouseX < slot.x() + 16 && mouseY >= slot.y() && mouseY < slot.y() + 16;
    }

    private static Optional<Component> smartInterfaceTooltip(MachineRecipeDisplay recipe, MachineRecipeLayout layout,
            double mouseX, double mouseY) {
        if (mouseX < layout.durationTextX()) return Optional.empty();
        int y = layout.smartInterfaceTextY(recipe);
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceInputs()) {
            if (mouseY >= y && mouseY < y + TEXT_LINE_SPACING) return Optional.of(smartInterface.tooltip());
            y += SMART_INTERFACE_LINE_SPACING;
        }
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceOutputs()) {
            if (mouseY >= y && mouseY < y + TEXT_LINE_SPACING) return Optional.of(smartInterface.tooltip());
            y += SMART_INTERFACE_LINE_SPACING;
        }
        return Optional.empty();
    }

}

package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.client.LightningJeiDisplay;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.client.render.FluidGuiRenderer;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.ReadableNumber;
import cn.howxu.mmcr.util.SaturatingLong;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * JEI category for MMCR machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeCategory implements IRecipeCategory<MachineRecipeDisplay> {

    private static final int FLUID_SLOT_CAPACITY = 1000;
    private static final float TEXT_SCALE = 0.85F;
    private static final int TEXT_LINE_SPACING = MachineRecipeLayout.TEXT_LINE_SPACING;
    private static final float SMART_INTERFACE_TEXT_SCALE = 0.85F;
    private static final int SMART_INTERFACE_LINE_SPACING = MachineRecipeLayout.TEXT_LINE_SPACING;
    private static final int JEI_SLOT_SIZE = 16;
    private static final int LEVEL_LABEL_SLOT_GAP = 2;
    private static final int LEVEL_ITEM_CYCLE_TICKS = 60;
    static final int ITEM_OVERLAY_X = 0;
    static final int ITEM_OVERLAY_Y = 0;
    static final float ITEM_OVERLAY_SCALE = 0.6F;
    private static final IIngredientRenderer<FluidStack> FULL_FLUID_RENDERER = new IIngredientRenderer<>() {
        @Override
        public void render(GuiGraphics guiGraphics, FluidStack fluid) {
            FluidGuiRenderer.drawFluid(guiGraphics, fluid, 0, 0, 16, 16);
        }

        @Override
        @Deprecated(forRemoval = true)
        public List<Component> getTooltip(FluidStack ingredient, TooltipFlag tooltipFlag) {
            return fluidTooltip(ingredient);
        }

        @Override
        public List<Component> getTooltip(FluidStack ingredient, Item.TooltipContext tooltipContext,
                @Nullable Player player, TooltipFlag tooltipFlag) {
            return fluidTooltip(ingredient);
        }
    };

    private final Component title;
    private final RecipeType<MachineRecipeDisplay> recipeType;
    private final IDrawable icon;
    private final Map<ResourceLocation, IDrawable> slotBackgrounds = new HashMap<>();
    private final Map<ResourceLocation, IDrawable> detailIcons = new HashMap<>();
    private final IGuiHelper guiHelper;

    public MachineRecipeCategory(IGuiHelper guiHelper, ResourceLocation poolId, ResourceLocation iconMachineId) {
        this.guiHelper = guiHelper;
        this.title = Component.translatable(poolId.toLanguageKey("recipe_pool"));
        this.recipeType = JeiMachineRecipeTypes.forPool(poolId);
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.controllerFor(iconMachineId).get());
    }

    @Override
    public @NotNull RecipeType<MachineRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    @Override
    public @NotNull Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return MachineRecipeLayout.CATEGORY_WIDTH;
    }

    @Override
    public int getHeight() {
        return MachineRecipeLayout.categoryHeight((int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe, IFocusGroup focuses) {
        setRecipe(builder, recipe, MachineRecipeLayout.forDisplay(recipe), Minecraft.getInstance().font::width);
    }

    void setRecipe(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe, MachineRecipeLayout layout,
                   ToIntFunction<Component> textWidth) {
        addRegion(builder, recipe, layout.inputs(), true);
        addRegion(builder, recipe, layout.outputs(), false);
        addLevelRequirementSlots(builder, layout, recipe, textWidth);
        addTransferSlots(builder, recipe);
        RecipeDetails.providers().forEach(provider -> provider.index(builder, recipe));
        builder.moveRecipeTransferButton(layout.transferButtonX(), layout.transferButtonY() - 3);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MachineRecipeDisplay recipe, IFocusGroup focuses) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        if (layout.arrow() != null) {
            addRecipeArrow(builder, guiHelper, layout.arrow());
        }
        if (!layout.metadataPages().isEmpty()) {
            var slots = builder.getRecipeSlots().getSlots().stream()
                    .filter(slot -> slot.getSlotName().filter(name -> name.startsWith("mmcr_details_")).isPresent())
                    .toList();
            var widget = new MachineRecipeMetadataWidget(this, recipe, layout, slots);
            builder.addSlottedWidget(widget, slots);
            builder.addGuiEventListener(widget);
        }
    }

    static void addRecipeArrow(IRecipeExtrasBuilder builder, IGuiHelper guiHelper, RecipeSlotLayout.Arrow arrow) {
        boolean vertical = arrow.height() > arrow.width();
        IDrawable drawable = new RecipeArrowDrawable(guiHelper.createAnimatedRecipeArrow(200), vertical);
        builder.addDrawableWidget(drawable).setPosition(arrow.x(), arrow.y());
    }

    @Override
    public void draw(MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        drawTextEntries(recipe, layout.inputs(), true, guiGraphics);
        drawTextEntries(recipe, layout.outputs(), false, guiGraphics);
        if (layout.metadataPages().isEmpty()) drawMetadata(recipe, layout, guiGraphics);
    }

    void drawMetadata(MachineRecipeDisplay recipe, MachineRecipeLayout layout, GuiGraphics guiGraphics) {
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        int textX = (int) (layout.durationTextX() / TEXT_SCALE);
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.mmcr.machine_recipe.duration", recipe.durationTicks(), seconds(recipe.durationTicks())),
                textX, (int) (layout.durationTextY() / TEXT_SCALE), 0xFF404040, false);

        int y = layout.durationTextY() + TEXT_LINE_SPACING;
        for (EnergyIngredient energy : recipe.energyInputs()) {
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_in", ReadableNumber.format(energy.fePerTick()),
                            ReadableNumber.format(saturatedEnergyTotal(energy.fePerTick(), recipe.durationTicks()))),
                    textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        for (EnergyIngredient energy : recipe.energyOutputs()) {
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("jei.mmcr.machine_recipe.energy_out", ReadableNumber.format(energy.fePerTick())),
                    textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        int width = (int) ((getWidth() - layout.durationTextX() * 2) / TEXT_SCALE);
        for (var row : layout.detailRows()) {
            drawDetail(row, guiGraphics, textX, (int) (y / TEXT_SCALE), width);
            y += TEXT_LINE_SPACING;
        }
        for (var row : layout.manaRows()) {
            var detail = JeiIngredientAdapterRegistry.get(row.entry().typeId()).orElseThrow().detailRow(row.entry());
            drawDetail(detail, guiGraphics, textX, (int) (row.y() / TEXT_SCALE), width);
        }
        Component hostRequirement = hostRequirementComponent(recipe, gameTime);
        if (!hostRequirement.getString().isEmpty()) {
            y = layout.hostRequirementTextY();
            guiGraphics.drawString(Minecraft.getInstance().font, hostRequirement, textX,
                    (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
        drawLevelRequirementLabels(recipe, layout, guiGraphics, textX);
        drawStageRequirementLabels(recipe, layout, guiGraphics, textX);
        y = layout.smartInterfaceTextY(recipe);
        guiGraphics.pose().popPose();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(SMART_INTERFACE_TEXT_SCALE, SMART_INTERFACE_TEXT_SCALE, 1.0F);
        textX = (int) (layout.durationTextX() / SMART_INTERFACE_TEXT_SCALE);
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceInputs()) {
            guiGraphics.drawString(Minecraft.getInstance().font, smartInterface.label(),
                    textX, (int) (y / SMART_INTERFACE_TEXT_SCALE), 0xFF404040, false);
            y += SMART_INTERFACE_LINE_SPACING;
        }
        for (MachineRecipeDisplay.SmartInterfaceDisplay smartInterface : recipe.smartInterfaceOutputs()) {
            guiGraphics.drawString(Minecraft.getInstance().font, smartInterface.label(),
                    textX, (int) (y / SMART_INTERFACE_TEXT_SCALE), 0xFF404040, false);
            y += SMART_INTERFACE_LINE_SPACING;
        }
        guiGraphics.pose().popPose();
        drawSourceTextLines(layout, guiGraphics);
        drawRecipeInformation(recipe, layout, guiGraphics);
    }

    private void drawDetail(RecipeDetails.Row row, GuiGraphics guiGraphics, int x, int y, int width) {
        if (row.renderer() != null) {
            row.renderer().draw(guiGraphics, x, y, width);
            return;
        }
        var font = Minecraft.getInstance().font;
        var icon = row.icon() == null ? null : detailIcons.computeIfAbsent(row.icon(), key ->
                BuiltInRegistries.ITEM.containsKey(key) ? guiHelper.createDrawableItemLike(BuiltInRegistries.ITEM.get(key)) : null);
        if (icon != null) {
            float scale = (float) font.lineHeight / icon.getHeight();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(x, y, 0F);
            guiGraphics.pose().scale(scale, scale, 1F);
            icon.draw(guiGraphics, 0, 0);
            guiGraphics.pose().popPose();
            x += (int) (icon.getWidth() * scale) + 2;
        }
        guiGraphics.drawString(font, row.label(), x, y, 0xFF404040, false);
    }

    private void drawRecipeInformation(MachineRecipeDisplay recipe, MachineRecipeLayout layout,
                                       GuiGraphics guiGraphics) {
        List<Component> information = RecipeInformationRegistry.componentsFor(
                recipe.recipePoolId(), recipe.recipeId());
        int lineCount = Math.min(information.size(), layout.informationLineCapacity(recipe, getHeight()));
        if (lineCount == 0) return;

        var font = Minecraft.getInstance().font;
        int margin = layout.durationTextX();
        int maxTextWidth = (int) ((getWidth() - margin * 2) / TEXT_SCALE);
        int textX = (int) (margin / TEXT_SCALE);
        int y = layout.informationTextY(recipe);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        for (int index = 0; index < lineCount; index++) {
            var lines = font.split(information.get(index), maxTextWidth);
            if (!lines.isEmpty()) {
                guiGraphics.drawString(font, lines.getFirst(), textX, (int) (y / TEXT_SCALE), 0xFF404040, false);
            }
            y += TEXT_LINE_SPACING;
        }
        guiGraphics.pose().popPose();
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        MachineRecipeLayout layout = MachineRecipeLayout.forDisplay(recipe);
        if (layout.metadataPages().isEmpty()) {
            metadataTooltip(tooltip, recipe, layout, mouseX, mouseY);
        }
    }

    void metadataTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay recipe, MachineRecipeLayout layout,
                         double mouseX, double mouseY) {
        if (mouseX >= layout.durationTextX() && mouseX < MachineRecipeLayout.CATEGORY_WIDTH - layout.durationTextX()) {
            double offset = mouseY - layout.stressTextY(recipe);
            if (offset >= 0 && offset < layout.detailRows().size() * TEXT_LINE_SPACING) {
                var lines = layout.detailRows().get((int) offset / TEXT_LINE_SPACING).tooltip();
                if (!lines.isEmpty()) { tooltip.addAll(lines); return; }
            }
        }
        for (var row : layout.manaRows()) {
            if (mouseX >= row.x() && mouseX < row.x() + row.width() && mouseY >= row.y() && mouseY < row.y() + row.height()) {
                var detail = JeiIngredientAdapterRegistry.get(row.entry().typeId()).orElseThrow().detailRow(row.entry());
                if (!detail.tooltip().isEmpty()) { tooltip.addAll(detail.tooltip()); return; }
            }
        }
        sourceTooltip(layout, mouseX, mouseY)
                .or(() -> smartInterfaceTooltip(recipe, layout, mouseX, mouseY)).ifPresent(tooltip::add);
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(MachineRecipeDisplay recipe) {
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

    static Component levelLabel(LevelRequirement requirement) {
        var type = MachineLevelRegistry.getType(requirement.typeId());
        return type == null ? Component.empty() : type.displayName().copy().append(Component.literal(": "));
    }

    static List<ItemStack> levelCandidates(LevelRequirement requirement) {
        MachineLevel required = MachineLevelRegistry.getLevel(requirement.levelId());
        if (required == null) return List.of();
        return MachineLevelRegistry.levelsForType(requirement.typeId()).stream()
                .filter(level -> level.priority() >= required.priority())
                .sorted(Comparator.comparingInt(MachineLevel::priority)
                        .thenComparing(level -> level.id().toString()))
                .map(MachineRecipeCategory::levelDisplayStack)
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    private static ItemStack levelDisplayStack(MachineLevel level) {
        ItemStack representative = level.representative();
        if (!representative.isEmpty()) {
            return new ItemStack(representative.getItem().builtInRegistryHolder(), 1, representative.getComponentsPatch());
        }
        return level.statePredicate().preferredState()
                .map(state -> new ItemStack(state.getBlock().asItem()))
                .map(MachineRecipeCategory::jeiItemStack)
                .orElse(ItemStack.EMPTY);
    }

    static ItemStack levelCandidate(LevelRequirement requirement, long gameTime) {
        List<ItemStack> candidates = levelCandidates(requirement);
        return candidates.isEmpty() ? ItemStack.EMPTY
                : candidates.get((int) ((gameTime / LEVEL_ITEM_CYCLE_TICKS) % candidates.size()));
    }

    private static boolean isMinimumLevelCandidate(LevelRequirement requirement) {
        List<ItemStack> candidates = levelCandidates(requirement);
        if (candidates.isEmpty()) return false;
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return ItemStack.isSameItemSameComponents(levelCandidate(requirement, gameTime), candidates.getFirst());
    }

    static Component minimumLevelTooltip(LevelRequirement requirement) {
        return MachineLevelRegistry.getLevel(requirement.levelId()) == null
                ? Component.empty()
                : Component.translatable("jei.mmcr.machine_recipe.minimum_level");
    }

    static void addLevelRequirementSlots(IRecipeLayoutBuilder builder, MachineRecipeLayout layout,
            MachineRecipeDisplay recipe, ToIntFunction<Component> labelWidth) {
        int index = 0;
        for (LevelRequirement requirement : sortedLevelRequirements(recipe.recipe())) {
            Component label = levelLabel(requirement);
            int slotX = levelSlotX(layout.durationTextX(), labelWidth.applyAsInt(label));
            int slotY = layout.levelRequirementSlotY(recipe, index);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.RENDER_ONLY, slotX, slotY);
            slot.setSlotName("mmcr_details_level_" + index++);
            slot.setStandardSlotBackground();
            slot.setCustomRenderer(VanillaTypes.ITEM_STACK, levelItemRenderer(requirement));
            List<ItemStack> candidates = levelCandidates(requirement);
            if (!candidates.isEmpty()) {
                slot.addItemStack(candidates.getFirst());
            }
            slot.addRichTooltipCallback((view, tooltip) -> {
                if (isMinimumLevelCandidate(requirement)) {
                    tooltip.add(minimumLevelTooltip(requirement));
                }
            });
        }
    }

    private static int levelSlotX(int textX, int labelWidth) {
        return textX + labelWidth + LEVEL_LABEL_SLOT_GAP;
    }

    private static IIngredientRenderer<ItemStack> levelItemRenderer(LevelRequirement requirement) {
        return new IIngredientRenderer<>() {
            @Override
            public void render(GuiGraphics guiGraphics, ItemStack ingredient) {
                Minecraft minecraft = Minecraft.getInstance();
                long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
                ItemStack candidate = levelCandidate(requirement, gameTime);
                if (!candidate.isEmpty()) {
                    guiGraphics.renderFakeItem(candidate, 0, 0);
                    guiGraphics.renderItemDecorations(minecraft.font, candidate, 0, 0);
                }
            }


            @Override
            @Deprecated(forRemoval = true)
            public List<Component> getTooltip(@NotNull ItemStack ingredient, @NotNull TooltipFlag tooltipFlag) {
                Minecraft minecraft = Minecraft.getInstance();
                return getTooltip(ingredient, Item.TooltipContext.of(minecraft.level), minecraft.player, tooltipFlag);
            }

            @Override
            public @NotNull List<Component> getTooltip(@NotNull ItemStack ingredient, Item.@NotNull TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
                Minecraft minecraft = Minecraft.getInstance();
                ItemStack candidate = levelCandidate(requirement,
                        minecraft.level == null ? 0L : minecraft.level.getGameTime());
                return candidate.isEmpty()
                        ? List.of()
                        : candidate.getTooltipLines(tooltipContext, player, tooltipFlag);
            }
        };
    }

    private static void drawLevelRequirementLabels(MachineRecipeDisplay recipe, MachineRecipeLayout layout,
            GuiGraphics guiGraphics, int textX) {
        int index = 0;
        for (LevelRequirement requirement : sortedLevelRequirements(recipe.recipe())) {
            int slotY = layout.levelRequirementSlotY(recipe, index++);
            int labelY = slotY + (JEI_SLOT_SIZE - Minecraft.getInstance().font.lineHeight) / 2 + 2;
            guiGraphics.drawString(Minecraft.getInstance().font, levelLabel(requirement), textX,
                    (int) (labelY / TEXT_SCALE), 0xFF404040, false);
        }
    }

    private static void drawStageRequirementLabels(MachineRecipeDisplay recipe, MachineRecipeLayout layout,
            GuiGraphics guiGraphics, int textX) {
        int y = layout.stageRequirementTextY(recipe);
        for (StageRequirement requirement : recipe.recipe().stageRequirements()) {
            guiGraphics.drawString(Minecraft.getInstance().font, stageRequirementLabel(requirement), textX,
                    (int) (y / TEXT_SCALE), 0xFF404040, false);
            y += TEXT_LINE_SPACING;
        }
    }

    static Component stageRequirementLabel(StageRequirement requirement) {
        return Component.translatable("jei.mmcr.machine_recipe.minimum_stage", requirement.minStage());
    }

    static Component hostRequirementComponent(MachineRecipeDisplay recipe, long gameTime) {
        if (recipe.requiredHostIds().isEmpty()) return Component.empty();
        List<ResourceLocation> hostIds = List.copyOf(recipe.requiredHostIds());
        int index = (int) ((gameTime / 20) % hostIds.size());
        ResourceLocation hostId = hostIds.get(index);
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

    private void addRegion(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.RegionPlan region, boolean input) {
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            addEntry(builder, recipe, slot, input);
        }
    }

    private void addEntry(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe,
            MachineRecipeLayout.SlotPlan slot, boolean input) {
        Optional<JeiDisplayEntry> entry = displayEntry(recipe, slot.entry(), input);
        var style = entry.flatMap(value -> JeiIngredientAdapterRegistry.get(value.typeId())).map(JeiIngredientAdapter::slotStyle).orElse(null);
        IRecipeSlotBuilder jeiSlot = builder.addSlot(style != null && style.indexed() ? entry.orElseThrow().role()
                : RecipeIngredientRole.RENDER_ONLY, slot.x(), slot.y());
        if (style == null || style.background() == null) jeiSlot.setStandardSlotBackground();
        else jeiSlot.setBackground(slotBackgrounds.computeIfAbsent(style.background(),
                key -> guiHelper.drawableBuilder(key, 0, 0, 18, 18).setTextureSize(18, 18).build()), -1, -1);
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
            case CHEMICAL -> null;
            case GENERIC, TEXT -> null;
        };
        return recipe.entries().stream()
                .filter(entry -> entry.role() == (input ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT))
                .filter(entry -> plan.kind() == MachineRecipeLayout.Kind.CHEMICAL
                        ? entry.typeId().equals(MekanismRecipeTypes.CHEMICAL) : entry.ingredientType() == type)
                .skip(plan.index())
                .findFirst();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void addGeneric(IRecipeSlotBuilder slot, JeiDisplayEntry entry) {
        if (entry.isTextOnly()) {
            slot.addRichTooltipCallback((view, tooltip) -> tooltip.add((Component) entry.ingredient()));
            return;
        }
        var adapter = JeiIngredientAdapterRegistry.get(entry.typeId()).orElse(null);
        String quantity = adapter == null ? null : adapter.quantityOverlay(entry);
        if (quantity != null) {
            boolean input = entry.role() == RecipeIngredientRole.INPUT;
            setItemOverlay(slot, input ? inputOverlayText(entry.chance(), selectedLanguage()) : outputOverlayText(entry.chance()), quantity);
            slot.addRichTooltipCallback((view, tooltip) -> {
                adapter.quantityTooltip(entry).ifPresent(tooltip::add);
                if (input) appendConsumeChanceTooltip(tooltip, entry.chance(), true);
                else appendOutputChanceTooltip(tooltip, entry.chance());
            });
        } else if (entry.ingredientType() == VanillaTypes.ITEM_STACK) {
            setItemOverlay(slot, entry.role() == RecipeIngredientRole.INPUT ? inputOverlayText(entry.chance(), selectedLanguage())
                    : outputOverlayText(entry.chance()), itemQuantityText(entry.exactAmount()));
        } else {
            setQuantityOverlay(slot, entry.ingredientType() == NeoForgeTypes.FLUID_STACK
                    ? fluidQuantityText(entry.count()) : itemQuantityText(entry.exactAmount()));
        }
        if (entry.renderer() != null) {
            slot.setCustomRenderer((IIngredientType) entry.ingredientType(), entry.renderer());
        } else if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) {
            slot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER);
        }
        if (entry.ingredient() instanceof List<?> ingredients) {
            slot.addIngredients((IIngredientType) entry.ingredientType(), ingredients);
            return;
        }
        slot.addIngredient((IIngredientType) entry.ingredientType(), entry.ingredient());
    }

    private static void drawTextEntries(MachineRecipeDisplay recipe, MachineRecipeLayout.RegionPlan region,
            boolean input, GuiGraphics guiGraphics) {
        for (MachineRecipeLayout.SlotPlan slot : region.slots()) {
            if (slot.entry().kind() != MachineRecipeLayout.Kind.TEXT) continue;
            displayEntry(recipe, slot.entry(), input).ifPresent(entry -> guiGraphics.drawString(Minecraft.getInstance().font,
                    (Component) entry.ingredient(), slot.x(), slot.y() + 4, 0xFF404040, false));
        }
    }

    private static void drawSourceTextLines(MachineRecipeLayout layout, GuiGraphics guiGraphics) {
        var font = Minecraft.getInstance().font;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        for (MachineRecipeLayout.TextPlan line : layout.sourceTextLines()) {
            guiGraphics.drawString(font, Language.getInstance().getVisualOrder(sourceTextLine(line, font.getSplitter())),
                    (int) (line.x() / TEXT_SCALE), (int) (line.y() / TEXT_SCALE), 0xFF404040, false);
        }
        guiGraphics.pose().popPose();
    }

    static FormattedText sourceTextLine(MachineRecipeLayout.TextPlan line, StringSplitter splitter) {
        return splitter.headByWidth((Component) line.entry().ingredient(), (int) (line.width() / TEXT_SCALE), Style.EMPTY);
    }

    static Optional<Component> sourceTooltip(MachineRecipeLayout layout, double mouseX, double mouseY) {
        return layout.sourceTextLines().stream().filter(line -> line.contains(mouseX, mouseY))
                .map(line -> (Component) line.entry().ingredient()).findFirst();
    }

    private static void addTransferSlots(IRecipeLayoutBuilder builder, MachineRecipeDisplay recipe) {
        for (MachineRecipeDisplay.FluidInputDisplay fluidDisplay : recipe.fluidInputs()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            if (fluidDisplay.ingredient() == null) continue;
            int amount = fluidDisplay.amount();
            for (FluidStack fluid : fluidDisplay.ingredient().getStacks()) {
                slot.addFluidStack(fluid.getFluid(), amount);
            }
        }
        for (MachineRecipeDisplay.ItemInputDisplay item : recipe.itemInputs()) {
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            addActualItem(slot, item);
        }
        for (MachineRecipeDisplay.FluidOutputDisplay output : recipe.fluidOutputs()) {
            FluidStack fluid = output.stack();
            builder.addOutputSlot(-1000, -1000)
                    .addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        }
        for (MachineRecipeDisplay.ItemOutputDisplay output : recipe.itemOutputs()) {
            builder.addOutputSlot(-1000, -1000).addItemStack(output.stack());
        }
        for (JeiDisplayEntry entry : recipe.entries()) {
            if (entry.typeId().equals(ItemRequirement.TYPE.id())
                    || entry.typeId().equals(FluidRequirement.TYPE.id())) continue;
            if (JeiIngredientAdapterRegistry.get(entry.typeId()).map(adapter -> adapter.addTransferSlots(builder, entry)).orElse(false)) continue;
            if (entry.role() != RecipeIngredientRole.INPUT || !entry.transferable()) continue;
            IRecipeSlotBuilder slot = builder.addInputSlot(-1000, -1000);
            addGeneric(slot, entry);
        }
    }

    private static void addActualItem(IRecipeSlotBuilder slot, MachineRecipeDisplay.ItemInputDisplay item) {
        List<ItemStack> stacks = item.stacks();
        if (stacks.isEmpty() && item.ingredient() != null) {
            slot.addIngredients(item.ingredient());
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
                jeiSlot.addIngredients(item.ingredient());
            } else {
                jeiSlot.addItemStacks(stacks);
            }
        } else {
            MachineRecipeDisplay.ItemOutputDisplay output = recipe.itemOutputs().get(entry.index());
            ItemStack stack = output.stack();
            String overlayText = outputOverlayText(output.chance());
            setItemOverlay(jeiSlot, overlayText, itemQuantityText(stack.getCount()));
            jeiSlot.addRichTooltipCallback((view, tooltip) -> appendOutputTooltip(tooltip, output));
            jeiSlot.addItemStack(jeiItemStack(stack));
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
            MachineRecipeDisplay.FluidInputDisplay fluidDisplay = recipe.fluidInputs().get(entry.index());
            if (!(display.ingredient() instanceof FluidStack fluid) || fluid.isEmpty()) return;
            setItemOverlay(jeiSlot, inputOverlayText(fluidDisplay.consumeChance(), selectedLanguage()),
                    fluidQuantityText(fluidDisplay.amount()));
            jeiSlot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER)
                    .addFluidStack(fluid.getFluid(), FLUID_SLOT_CAPACITY, fluid.getComponentsPatch());
            jeiSlot.addRichTooltipCallback((view, tooltip) -> {
                appendFluidQuantityTooltip(tooltip, fluidDisplay.amount());
                appendConsumeChanceTooltip(tooltip, fluidDisplay.consumeChance(), true);
            });
        } else {
            var output = recipe.fluidOutputs().get(entry.index());
            var stack = output.stack();
            setItemOverlay(jeiSlot, outputOverlayText(output.chance()), fluidQuantityText(stack.getAmount()));
            jeiSlot.setCustomRenderer(NeoForgeTypes.FLUID_STACK, FULL_FLUID_RENDERER)
                    .addFluidStack(stack.getFluid(), FLUID_SLOT_CAPACITY, stack.getComponentsPatch());
            jeiSlot.addRichTooltipCallback((view, tooltip) -> {
                appendFluidQuantityTooltip(tooltip, stack.getAmount());
                appendOutputChanceTooltip(tooltip, output.chance());
            });
        }
    }

    private static String selectedLanguage() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getLanguageManager() == null
                ? "en_us" : minecraft.getLanguageManager().getSelected();
    }

    static void setItemOverlay(IRecipeSlotBuilder jeiSlot, String chanceText, String quantityText) {
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

    static long saturatedEnergyTotal(long fePerTick, int durationTicks) {
        return SaturatingLong.multiply(fePerTick, durationTicks);
    }

    static String fluidQuantityText(int amount) {
        return amount <= 10
                ? ReadableNumber.formatForSlot(amount, 0, "mB")
                : ReadableNumber.formatForSlot(amount, 3, "B");
    }

    static String chemicalQuantityText(long amount) {
        return ReadableNumber.formatForSlot(amount, 3, "B");
    }

    static List<Component> fluidTooltip(FluidStack fluid) {
        return List.of(fluid.getHoverName());
    }

    static String itemTooltipQuantity(int count) {
        return count > 1 ? ReadableNumber.formatExact(count) : "";
    }

    static String fluidTooltipQuantity(int amount) {
        return amount <= 10
                ? ReadableNumber.formatExact(amount) + "mB"
                : fluidBucketText(amount, 3);
    }

    static String chemicalTooltipQuantity(long amount) {
        return BigDecimal.valueOf(amount, 3)
                .setScale(3, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString() + "B";
    }

    private static String fluidBucketText(int amount, int decimalPlaces) {
        return BigDecimal.valueOf(amount, 3)
                .setScale(decimalPlaces, RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString() + "B";
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
        appendOutputChanceTooltip(tooltip, output.chance());
    }

    private static void appendOutputChanceTooltip(ITooltipBuilder tooltip, float chance) {
        if (chance < 1F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.output_chance",
                    Math.round(chance * 100F) + "%"));
        }
    }

    private static void appendFluidQuantityTooltip(ITooltipBuilder tooltip, int amount) {
        String quantity = fluidTooltipQuantity(amount);
        if (!quantity.isEmpty()) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.fluid_amount", quantity));
        }
    }

    static void appendConsumeChanceTooltip(ITooltipBuilder tooltip, float consumeChance, boolean input) {
        if (!input) return;
        if (consumeChance == 0F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.keep"));
        } else if (consumeChance < 1F) {
            tooltip.add(Component.translatable("jei.mmcr.machine_recipe.consume_chance",
                    Math.round(consumeChance * 100F) + "%"));
        }
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

    static Optional<Component> lightningTooltip(MachineRecipeDisplay recipe, MachineRecipeLayout layout,
                                               double mouseX, double mouseY) {
        if (mouseX < layout.durationTextX() || mouseX >= MachineRecipeLayout.CATEGORY_WIDTH - layout.durationTextX()) {
            return Optional.empty();
        }
        int y = layout.lightningTextY(recipe);
        for (LightningRequirement lightning : LightningJeiDisplay.requirements(recipe)) {
            if (mouseY >= y && mouseY < y + TEXT_LINE_SPACING && !lightning.io().isInput()) {
                return Optional.of(Component.translatable("jei.mmcr.machine_recipe.output_chance",
                        Math.round(lightning.chance() * 100F) + "%"));
            }
            y += TEXT_LINE_SPACING;
        }
        return Optional.empty();
    }

    static boolean appendAirTooltip(ITooltipBuilder tooltip, MachineRecipeDisplay recipe, MachineRecipeLayout layout,
                                    double mouseX, double mouseY) {
        if (mouseX < layout.durationTextX() || mouseX >= MachineRecipeLayout.CATEGORY_WIDTH - layout.durationTextX()) return false;
        int y = layout.airTextY(recipe);
        for (MachineRecipeDisplay.AirDisplay air : recipe.airInputs()) {
            if (mouseY >= y && mouseY < y + TEXT_LINE_SPACING) {
                tooltip.addAll(air.tooltip(true));
                return true;
            }
            y += TEXT_LINE_SPACING;
        }
        for (MachineRecipeDisplay.AirDisplay air : recipe.airOutputs()) {
            if (mouseY >= y && mouseY < y + TEXT_LINE_SPACING) {
                tooltip.addAll(air.tooltip(false));
                return true;
            }
            y += TEXT_LINE_SPACING;
        }
        return false;
    }

}

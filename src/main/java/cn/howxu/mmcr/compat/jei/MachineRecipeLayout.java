package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.client.recipe.RecipeDetailsLayout;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing;
import cn.howxu.mmcr.client.recipe.RecipeDisplayDrawing.WrappedText;
import cn.howxu.mmcr.client.recipe.RecipeMetadataRow;
import cn.howxu.mmcr.client.recipe.RecipeSlotLayout;
import cn.howxu.mmcr.client.recipe.RecipeSlotLayout.Arrow;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.botania.client.ManaJeiDisplay;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.internal.client.RecipeInformationRegistry;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Deterministic slot layout for MMCR machine recipes in JEI.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeLayout(
        int width,
        int height,
        RegionPlan inputs,
        RegionPlan outputs,
        @Nullable Arrow arrow,
        List<TextPlan> sourceTextLines,
        List<ManaRowPlan> manaRows,
        List<MetadataPage> metadataPages,
        int metadataViewportY,
        int metadataViewportHeight,
        int durationTextX,
        int hostRequirementTextY,
        int durationTextY,
        int transferButtonX,
        int transferButtonY,
        List<DetailRow> details
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 150;
    static final int CATEGORY_WIDTH = 168;

    private static final int SLOT_SIZE = 18;
    static final int TEXT_LINE_SPACING = 10;
    static final int PAGE_FOOTER_HEIGHT = 12;

    public MachineRecipeLayout(int width, int height, RegionPlan inputs, RegionPlan outputs, @Nullable Arrow arrow,
                               List<TextPlan> sourceTextLines, List<ManaRowPlan> manaRows, List<MetadataPage> metadataPages,
                               int metadataViewportY, int metadataViewportHeight, int durationTextX,
                               int hostRequirementTextY, int durationTextY, int transferButtonX, int transferButtonY) {
        this(width, height, inputs, outputs, arrow, sourceTextLines, manaRows, metadataPages, metadataViewportY,
                metadataViewportHeight, durationTextX, hostRequirementTextY, durationTextY, transferButtonX,
                transferButtonY, List.of());
    }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return forDisplay(display, (int) Minecraft.getInstance().getWindow().getGuiScale(), Minecraft.getInstance().font::split);
    }

    /** One cached measurement per actual JEI widget, invalidated by notifications or display context.
     * @author howxu <dev@howxu.cn>
     */
    static final class Live {
        private final MachineRecipeDisplay display;
        private final Supplier<Context> context;
        private final LongSupplier revision;
        private Context measuredContext;
        private long measuredRevision;
        private MachineRecipeLayout measured;

        Live(MachineRecipeDisplay display) {
            this(display, () -> {
                var minecraft = Minecraft.getInstance();
                var font = minecraft.font;
                return new Context((int) minecraft.getWindow().getGuiScale(), font, Language.getInstance(),
                        font::split, font::width);
            }, JeiRuntimeReloader::layoutRevision);
        }

        Live(MachineRecipeDisplay display, Supplier<Context> context, LongSupplier revision) {
            this.display = display;
            this.context = context;
            this.revision = revision;
        }

        MachineRecipeLayout current() {
            Context next = context.get();
            long version = revision.getAsLong();
            if (measured == null || measuredRevision != version || measuredContext.guiScale() != next.guiScale()
                    || measuredContext.font() != next.font() || measuredContext.language() != next.language()) {
                measured = forDisplay(display, next.guiScale(), next.splitter());
                measuredContext = next;
                measuredRevision = version;
            }
            return measured;
        }

        int textWidth(Component text) { return measuredContext.textWidth().applyAsInt(text); }
    }

    /** Identity keys include the language instance replaced on client resource reload.
     * @author howxu <dev@howxu.cn>
     */
    record Context(int guiScale, Object font, Language language,
                   BiFunction<Component, Integer, List<FormattedCharSequence>> splitter,
                   ToIntFunction<Component> textWidth) { }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale) {
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.font != null) return forDisplay(display, guiScale, minecraft.font::split);
        // Headless geometry callers have no font atlas; still split complete text at the measured width.
        var splitter = new StringSplitter((codePoint, style) -> 1F);
        return forDisplay(display, guiScale, (text, width) ->
                Language.getInstance().getVisualOrder(splitter.splitLines(text, width, Style.EMPTY)));
    }

    static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale,
                                          BiFunction<Component, Integer, List<FormattedCharSequence>> splitter) {
        List<JeiDisplayEntry> entries = display.entries();
        Map<JeiDisplayEntry, RecipeDisplayEntry> resources = new IdentityHashMap<>();
        // The JEI shell emits one adapted entry (including fallbacks) per neutral resource.
        List<RecipeDisplayEntry> neutralEntries = display.data().entries();
        for (int index = 0; index < entries.size(); index++) resources.put(entries.get(index), neutralEntries.get(index));
        List<EntryPlan> inputEntries = entries(entries, RecipeIngredientRole.INPUT, resources);
        List<EntryPlan> outputEntries = entries(entries, RecipeIngredientRole.OUTPUT, resources);
        RecipeSlotLayout slots = RecipeSlotLayout.forCounts(inputEntries.size(), outputEntries.size());
        int metadataY = slots.bottom() + 4;
        int height = categoryHeight(guiScale);
        List<DetailRow> details = detailRows(display, entries, metadataY, splitter);
        List<Integer> rowHeights = details.stream().map(DetailRow::height).toList();
        int metadataHeight = rowHeights.stream().mapToInt(Integer::intValue).sum();
        int viewportY = metadataY;
        int viewportHeight = height - viewportY - 2;
        // If the IO grid leaves no usable details area, page the complete body instead.
        // Its logical slot/arrow geometry is unchanged and the grid itself becomes scrollable.
        boolean needsPages = metadataHeight > viewportHeight;
        boolean bodyPages = needsPages && viewportHeight < SLOT_SIZE + PAGE_FOOTER_HEIGHT + 2;
        if (bodyPages) {
            viewportY = 0;
            viewportHeight = height - 2;
            var bodyHeights = new ArrayList<Integer>();
            bodyHeights.add(metadataY);
            bodyHeights.addAll(rowHeights);
            rowHeights = bodyHeights;
        }
        List<MetadataPage> pages = bodyPages || needsPages
                ? metadataPages(rowHeights, viewportY, viewportHeight - PAGE_FOOTER_HEIGHT - 2) : List.of();
        List<JeiDisplayEntry> sourceEntries = orderedEntries(entries, true);
        List<TextPlan> sourceLines = new ArrayList<>();
        List<JeiDisplayEntry> manaEntries = orderedEntries(entries, false);
        List<ManaRowPlan> manaRows = new ArrayList<>();
        for (var row : details) {
            if (row.kind() == DetailKind.SOURCE) sourceLines.add(new TextPlan(sourceEntries.get(row.index()),
                    row.x(), row.y(), row.width(), row.height()));
            if (row.kind() == DetailKind.MANA) manaRows.add(new ManaRowPlan(manaEntries.get(row.index()),
                    row.x(), row.y(), row.width(), row.height()));
        }
        int durationY = sectionY(details, DetailKind.DURATION, 0);
        return new MachineRecipeLayout(
                WIDTH,
                height,
                bind(inputEntries, slots.inputs()),
                bind(outputEntries, slots.outputs()),
                slots.arrow(),
                List.copyOf(sourceLines),
                List.copyOf(manaRows),
                pages,
                viewportY,
                viewportHeight,
                8,
                sectionY(details, DetailKind.HOST, 0),
                durationY,
                transferButtonXForGuiScale(guiScale),
                transferButtonYForGuiScale(guiScale),
                details
        );
    }

    private static int transferButtonXForGuiScale(int guiScale) {
        return switch (guiScale) {
            case 1 -> 152;
            case 2 -> 152;
            case 3 -> 152;
            default -> 152;
        };
    }

    private static int transferButtonYForGuiScale(int guiScale) {
        return switch (guiScale) {
            case 1 -> 285;
            case 2 -> 265;
            case 3 -> 205;
            default -> 135;
        };
    }
    static int categoryHeight(int guiScale) {
        return switch (guiScale) {
            case 1 -> 300;
            case 2 -> 280;
            case 3 -> 220;
            default -> HEIGHT;
        };
    }

    private static List<DetailRow> detailRows(MachineRecipeDisplay display, List<JeiDisplayEntry> entries, int startY,
                                              BiFunction<Component, Integer, List<FormattedCharSequence>> splitter) {
        List<DetailRow> rows = new ArrayList<>();
        for (var entry : orderedEntries(entries, true)) addDetail(rows, DetailKind.SOURCE,
                new RecipeMetadataRow((Component) entry.ingredient(), List.of((Component) entry.ingredient()), List.of()), splitter);
        addDetail(rows, DetailKind.DURATION, display.data().metadata().getFirst(), splitter);
        for (var energy : display.energyInputs()) addDetail(rows, DetailKind.ENERGY, plainRow(Component.translatable(
                "jei.mmcr.machine_recipe.energy_in", ReadableNumber.format(energy.fePerTick()),
                ReadableNumber.format(MachineRecipeCategory.saturatedEnergyTotal(energy.fePerTick(), display.durationTicks())))), splitter);
        for (var energy : display.energyOutputs()) addDetail(rows, DetailKind.ENERGY, plainRow(Component.translatable(
                "jei.mmcr.machine_recipe.energy_out", ReadableNumber.format(energy.fePerTick()))), splitter);
        var cogwheel = ResourceLocation.parse("create:cogwheel");
        for (var stress : display.stressInputs()) addDetail(rows, DetailKind.STRESS,
                iconRow(stress.label(true), stress.tooltip(true), cogwheel), splitter);
        for (var stress : display.stressOutputs()) addDetail(rows, DetailKind.STRESS,
                iconRow(stress.label(false), stress.tooltip(false), cogwheel), splitter);
        for (var air : display.airInputs()) addDetail(rows, DetailKind.AIR,
                iconRow(air.label(true), air.tooltip(true), PneumaticIds.ADVANCED_PRESSURE_TUBE), splitter);
        for (var air : display.airOutputs()) addDetail(rows, DetailKind.AIR,
                iconRow(air.label(false), air.tooltip(false), PneumaticIds.ADVANCED_PRESSURE_TUBE), splitter);
        var heatIcon = ResourceLocation.parse("mekanism:superheating_element");
        display.minimumTemperature().ifPresent(value -> addDetail(rows, DetailKind.HEAT,
                iconRow(MachineRecipeDisplay.minimumTemperatureLabel(value), List.of(), heatIcon), splitter));
        display.outputHeat().ifPresent(value -> addDetail(rows, DetailKind.HEAT,
                iconRow(MachineRecipeDisplay.outputHeatLabel(value), List.of(), heatIcon), splitter));
        for (var entry : orderedEntries(entries, false)) addDetail(rows, DetailKind.MANA,
                iconRow(((ManaJeiDisplay) entry.ingredient()).label(), List.of(), BotaniaManaIds.CREATIVE_POOL), splitter);
        if (!display.requiredHostIds().isEmpty()) addDetail(rows, DetailKind.HOST,
                plainRow(MachineRecipeCategory.allHostRequirementComponent(display)), splitter);
        for (var level : MachineRecipeCategory.sortedLevelRequirements(display.recipe())) addDetail(rows, DetailKind.LEVEL,
                plainRow(MachineRecipeCategory.levelLabel(level)), splitter);
        for (var stage : display.recipe().stageRequirements()) addDetail(rows, DetailKind.STAGE,
                plainRow(MachineRecipeCategory.stageRequirementLabel(stage)), splitter);
        for (var smart : display.smartInterfaceInputs()) addDetail(rows, DetailKind.SMART,
                new RecipeMetadataRow(smart.label(), List.of(smart.tooltip()), List.of()), splitter);
        for (var smart : display.smartInterfaceOutputs()) addDetail(rows, DetailKind.SMART,
                new RecipeMetadataRow(smart.label(), List.of(smart.tooltip()), List.of()), splitter);
        for (var information : RecipeInformationRegistry.componentsFor(display.recipePoolId(), display.recipeId())) {
            addDetail(rows, DetailKind.INFORMATION, plainRow(information), splitter);
        }
        List<DetailRow> positioned = new ArrayList<>();
        int y = startY;
        for (var row : rows) {
            positioned.add(new DetailRow(row.kind(), row.index(), row.metadata(), row.wrapped(), row.x(), y, row.width(), row.height()));
            y += row.height();
        }
        return List.copyOf(positioned);
    }

    private static RecipeMetadataRow plainRow(Component text) {
        return new RecipeMetadataRow(text, List.of(), List.of());
    }

    private static RecipeMetadataRow iconRow(Component text, List<Component> tooltip, ResourceLocation icon) {
        return new RecipeMetadataRow(text, tooltip, BuiltInRegistries.ITEM.containsKey(icon)
                ? List.of(new ItemStack(BuiltInRegistries.ITEM.get(icon))) : List.of());
    }

    private static void addDetail(List<DetailRow> rows, DetailKind kind, RecipeMetadataRow metadata,
                                  BiFunction<Component, Integer, List<FormattedCharSequence>> splitter) {
        int width = CATEGORY_WIDTH - 16 - (kind == DetailKind.LEVEL ? SLOT_SIZE + 2 : 0);
        var wrapped = RecipeDisplayDrawing.wrapMetadata(metadata, width, splitter);
        int height = kind == DetailKind.LEVEL ? Math.max(SLOT_SIZE, wrapped.height() + 5) : wrapped.height();
        int index = (int) rows.stream().filter(row -> row.kind() == kind).count();
        rows.add(new DetailRow(kind, index, metadata, wrapped, 8, 0, width, height));
    }

    private static List<MetadataPage> metadataPages(List<Integer> rowHeights, int startY, int capacity) {
        List<MetadataPage> pages = new ArrayList<>();
        int y = startY;
        for (var page : RecipeDetailsLayout.pages(rowHeights, capacity)) {
            pages.add(new MetadataPage(y, y + page.contentHeight()));
            y += page.contentHeight();
        }
        return List.copyOf(pages);
    }

    private static List<JeiDisplayEntry> orderedEntries(List<JeiDisplayEntry> entries, boolean source) {
        return entries.stream().filter(source ? MachineRecipeLayout::isSourceText : MachineRecipeLayout::isMana)
                .sorted(Comparator.comparingInt(entry -> entry.role() == RecipeIngredientRole.INPUT ? 0 : 1)).toList();
    }

    private static boolean isMana(JeiDisplayEntry entry) {
        return entry.typeId().equals(BotaniaManaIds.MANA);
    }

    private static boolean isSourceText(JeiDisplayEntry entry) {
        return entry.isTextOnly() && entry.typeId().equals(ArsSourceIds.SOURCE);
    }

    private static List<EntryPlan> entries(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role) {
        return entries(displayEntries, role, Map.of());
    }

    private static List<EntryPlan> entries(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role,
                                           Map<JeiDisplayEntry, RecipeDisplayEntry> resources) {
        List<EntryPlan> entries = new ArrayList<>();
        int itemIndex = 0;
        int fluidIndex = 0;
        int customItemIndex = 0;
        int customFluidIndex = 0;
        int chemicalIndex = 0;
        int textIndex = 0;
        for (JeiDisplayEntry entry : displayEntries.stream()
                .filter(candidate -> candidate.role() == role && !isSourceText(candidate) && !isMana(candidate))
                .sorted(Comparator.comparingInt(MachineRecipeLayout::kindOrder))
                .toList()) {
            if (entry.ingredientType() == VanillaTypes.ITEM_STACK) {
                entries.add(new EntryPlan(Kind.ITEM,
                        entry.typeId().equals(ItemRequirement.TYPE.id()) ? itemIndex++ : customItemIndex++, entry, resources.get(entry)));
            } else if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) {
                entries.add(new EntryPlan(Kind.FLUID,
                        entry.typeId().equals(FluidRequirement.TYPE.id()) ? fluidIndex++ : customFluidIndex++, entry, resources.get(entry)));
            } else if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) {
                entries.add(new EntryPlan(Kind.CHEMICAL, chemicalIndex++, entry, resources.get(entry)));
            } else if (!entry.isTextOnly()) {
                entries.add(new EntryPlan(Kind.GENERIC, textIndex++, entry, resources.get(entry)));
            } else {
                entries.add(new EntryPlan(Kind.TEXT, textIndex++, entry, resources.get(entry)));
            }
        }

        return List.copyOf(entries);
    }

    private static RegionPlan bind(List<EntryPlan> entries, List<RecipeSlotLayout.Position> positions) {
        List<SlotPlan> slots = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            RecipeSlotLayout.Position position = positions.get(index);
            slots.add(new SlotPlan(entries.get(index), position.x(), position.y()));
        }
        return new RegionPlan(List.copyOf(slots));
    }

    static RegionPlan regionForEntries(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role, int guiScale) {
        List<EntryPlan> entries = entries(displayEntries, role);
        return bind(entries, RecipeSlotLayout.forCounts(entries.size(), 0).inputs());
    }

    private static int kindOrder(JeiDisplayEntry entry) {
        RecipeSlotLayout.Kind kind;
        if (entry.typeId().equals(ArsSourceIds.SOURCE)) kind = RecipeSlotLayout.Kind.SOURCE;
        else if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) kind = RecipeSlotLayout.Kind.FLUID;
        else if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) kind = RecipeSlotLayout.Kind.CHEMICAL;
        else if (entry.ingredientType() == VanillaTypes.ITEM_STACK) kind = RecipeSlotLayout.Kind.ITEM;
        else kind = RecipeSlotLayout.Kind.OTHER;
        return RecipeSlotLayout.kindOrder(kind);
    }

    public int levelRequirementY(MachineRecipeDisplay display, int index) {
        return levelRequirementSlotY(display, index);
    }

    public int levelRequirementSlotY(MachineRecipeDisplay display, int index) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.LEVEL, index);
        int metadataY = display.requiredHostIds().isEmpty()
                ? hostRequirementTextY
                : hostRequirementTextY + TEXT_LINE_SPACING;
        return metadataY + SLOT_SIZE * index;
    }

    public int smartInterfaceTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.SMART, 0);
        return stageRequirementTextY(display) + TEXT_LINE_SPACING * display.recipe().stageRequirements().size();
    }

    public int stressTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.STRESS, 0);
        return durationTextY + TEXT_LINE_SPACING * (1 + display.energyInputs().size() + display.energyOutputs().size());
    }

    public int airTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.AIR, 0);
        return stressTextY(display) + TEXT_LINE_SPACING * (display.stressInputs().size() + display.stressOutputs().size());
    }

    public int stageRequirementTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.STAGE, 0);
        int levelCount = display.recipe().levelRequirements().size();
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1) + SLOT_SIZE;
        }
        return hostRequirementTextY + TEXT_LINE_SPACING * (display.requiredHostIds().isEmpty() ? 0 : 1);
    }

    public int lastMetadataTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return details.stream().filter(row -> row.kind() != DetailKind.INFORMATION)
                .reduce((first, second) -> second).orElseThrow().y();
        int smartInterfaceCount = display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size();
        if (smartInterfaceCount > 0) {
            return smartInterfaceTextY(display) + TEXT_LINE_SPACING * (smartInterfaceCount - 1);
        }
        int levelCount = display.recipe().levelRequirements().size();
        int stageCount = display.recipe().stageRequirements().size();
        if (stageCount > 0) {
            return stageRequirementTextY(display) + TEXT_LINE_SPACING * (stageCount - 1);
        }
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1);
        }
        return hostRequirementTextY + TEXT_LINE_SPACING * (display.requiredHostIds().isEmpty() ? -1 : 0);
    }

    public int informationTextY(MachineRecipeDisplay display) {
        if (!details.isEmpty()) return sectionY(details, DetailKind.INFORMATION, 0);
        int smartInterfaceCount = display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size();
        return smartInterfaceTextY(display) + TEXT_LINE_SPACING * smartInterfaceCount;
    }

    public int informationLineCapacity(MachineRecipeDisplay display, int categoryHeight) {
        int availableHeight = categoryHeight - informationTextY(display);
        return Math.max(0, availableHeight / TEXT_LINE_SPACING);
    }

    public boolean pagesIncludeIngredients() {
        return !metadataPages.isEmpty() && metadataViewportY == 0;
    }

    private static int sectionY(List<DetailRow> rows, DetailKind kind, int index) {
        var matching = rows.stream().filter(row -> row.kind() == kind).toList();
        if (index < matching.size()) return matching.get(index).y();
        int start = rows.stream().filter(row -> row.kind().ordinal() > kind.ordinal()).findFirst()
                .map(DetailRow::y).orElseGet(() -> rows.getLast().y() + rows.getLast().height());
        return start + Math.max(0, index - matching.size()) * (kind == DetailKind.LEVEL ? SLOT_SIZE : TEXT_LINE_SPACING);
    }

    /** JEI presentation order, independent of requirement declaration order.
     * @author howxu <dev@howxu.cn>
     */
    public enum DetailKind { SOURCE, DURATION, ENERGY, STRESS, AIR, HEAT, MANA, HOST, LEVEL, STAGE, SMART, INFORMATION }

    /** Complete measured row; drawing uses this exact wrapped text and tooltip bounds.
     * @author howxu <dev@howxu.cn>
     */
    public record DetailRow(DetailKind kind, int index, RecipeMetadataRow metadata, WrappedText wrapped,
                            int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public enum Kind { ITEM, FLUID, CHEMICAL, GENERIC, TEXT }

    public record EntryPlan(Kind kind, int index, @Nullable JeiDisplayEntry displayEntry,
                            @Nullable RecipeDisplayEntry resource) {
        public EntryPlan(Kind kind, int index, @Nullable JeiDisplayEntry displayEntry) {
            this(kind, index, displayEntry, null);
        }

        public EntryPlan(Kind kind, int index) {
            this(kind, index, null, null);
        }
    }

    public record SlotPlan(EntryPlan entry, int x, int y) {}

    /** Logical, whole-row page of the bounded recipe details widget. @author howxu <dev@howxu.cn> */
    public record MetadataPage(int startY, int endY) {}

    /** Drawing bounds for a plain mana icon-and-text row, not an interactive JEI slot.
     * @author howxu <dev@howxu.cn>
     */
    public record ManaRowPlan(JeiDisplayEntry entry, int x, int y, int width, int height) {
    }

    /**
     * A reserved, bounded text row with a full-row tooltip hit area.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record TextPlan(JeiDisplayEntry entry, int x, int y, int width, int height) {
        public TextPlan(JeiDisplayEntry entry, int x, int y, int width) {
            this(entry, x, y, width, TEXT_LINE_SPACING);
        }
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public record RegionPlan(List<SlotPlan> slots) {}
}

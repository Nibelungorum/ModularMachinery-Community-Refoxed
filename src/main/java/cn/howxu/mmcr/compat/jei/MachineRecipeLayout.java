package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.Minecraft;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<TextPlan> sourceTextLines,
        List<ManaRowPlan> manaRows,
        List<MetadataPage> metadataPages,
        int metadataViewportY,
        int metadataViewportHeight,
        int durationTextX,
        int hostRequirementTextY,
        int durationTextY,
        int transferButtonX,
        int transferButtonY
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 150;
    static final int CATEGORY_WIDTH = 168;

    private static final int COLUMNS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_START_Y = 8;
    private static final int TEXT_OFFSET_Y = 4;
    static final int TEXT_LINE_SPACING = 10;
    static final int PAGE_FOOTER_HEIGHT = 12;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return forDisplay(display, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale) {
        List<JeiDisplayEntry> entries = display.entries();
        List<Integer> metadataRows = metadataRowHeights(display, entries);
        int metadataHeight = metadataRows.stream().mapToInt(Integer::intValue).sum();
        // Preserve legacy grids while reserving complete details for Air and Mana recipes.
        boolean reserveMetadata = !display.airInputs().isEmpty() || !display.airOutputs().isEmpty()
                || entries.stream().anyMatch(MachineRecipeLayout::isMana);
        int availableRows = reserveMetadata
                ? (categoryHeight(guiScale) - SLOT_START_Y - TEXT_OFFSET_Y - metadataHeight) / SLOT_SIZE
                : rows(guiScale);
        int gridRows = Math.max(1, Math.min(rows(guiScale), availableRows));
        int metadataY = baseMetadataTextY(entries, gridRows);
        int viewportHeight = categoryHeight(guiScale) - metadataY - 2;
        List<MetadataPage> pages = availableRows < 1
                ? metadataPages(metadataRows, metadataY, viewportHeight - PAGE_FOOTER_HEIGHT - 2) : List.of();
        List<TextPlan> sourceLines = sourceTextLines(entries, metadataY);
        int durationY = metadataY + sourceLines.size() * TEXT_LINE_SPACING;
        List<ManaRowPlan> manaRows = manaRows(entries, durationY + TEXT_LINE_SPACING * metadataLineCount(display));
        return new MachineRecipeLayout(
                WIDTH,
                categoryHeight(guiScale),
                region(entries, RecipeIngredientRole.INPUT, 12, false, gridRows),
                region(entries, RecipeIngredientRole.OUTPUT, 102, true, gridRows),
                sourceLines,
                manaRows,
                pages,
                metadataY,
                viewportHeight,
                8,
                durationY + TEXT_LINE_SPACING * (metadataLineCount(display) + manaRows.size()),
                durationY,
                transferButtonXForGuiScale(guiScale),
                transferButtonYForGuiScale(guiScale)
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
    private static int rows(int guiScale) {
        return switch (guiScale) {
            case 1 -> 14;
            case 2 -> 11;
            case 3 -> 8;
            default -> 5;
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

    private static List<Integer> metadataRowHeights(MachineRecipeDisplay display, List<JeiDisplayEntry> entries) {
        List<Integer> heights = new ArrayList<>();
        entries.stream().filter(MachineRecipeLayout::isSourceText).forEach(entry -> heights.add(TEXT_LINE_SPACING));
        for (int index = 0; index < metadataLineCount(display); index++) heights.add(TEXT_LINE_SPACING);
        entries.stream().filter(MachineRecipeLayout::isMana).forEach(entry -> heights.add(TEXT_LINE_SPACING));
        if (!display.requiredHostIds().isEmpty()) heights.add(TEXT_LINE_SPACING);
        int levels = display.recipe().levelRequirements().size();
        for (int index = 0; index < levels; index++) {
            heights.add(SLOT_SIZE + (index == levels - 1 ? TEXT_LINE_SPACING : 0));
        }
        display.recipe().stageRequirements().forEach(entry -> heights.add(TEXT_LINE_SPACING));
        display.smartInterfaceInputs().forEach(entry -> heights.add(TEXT_LINE_SPACING));
        display.smartInterfaceOutputs().forEach(entry -> heights.add(TEXT_LINE_SPACING));
        return heights;
    }

    private static List<MetadataPage> metadataPages(List<Integer> rowHeights, int startY, int capacity) {
        List<MetadataPage> pages = new ArrayList<>();
        int pageStart = startY;
        int y = startY;
        for (int height : rowHeights) {
            if (y + height - pageStart > capacity) {
                pages.add(new MetadataPage(pageStart, y));
                pageStart = y;
            }
            y += height;
        }
        pages.add(new MetadataPage(pageStart, y));
        return List.copyOf(pages);
    }

    private static List<ManaRowPlan> manaRows(List<JeiDisplayEntry> entries, int startY) {
        List<ManaRowPlan> lines = new ArrayList<>();
        int y = startY;
        for (JeiDisplayEntry entry : entries.stream().filter(MachineRecipeLayout::isMana)
                .sorted(Comparator.comparingInt(entry -> entry.role() == RecipeIngredientRole.INPUT ? 0 : 1)).toList()) {
            lines.add(new ManaRowPlan(entry, 8, y, CATEGORY_WIDTH - 16, TEXT_LINE_SPACING));
            y += TEXT_LINE_SPACING;
        }
        return List.copyOf(lines);
    }

    private static boolean isMana(JeiDisplayEntry entry) {
        return entry.typeId().equals(BotaniaManaIds.MANA);
    }

    private static List<TextPlan> sourceTextLines(List<JeiDisplayEntry> entries, int metadataY) {
        List<TextPlan> lines = new ArrayList<>();
        int y = metadataY;
        for (JeiDisplayEntry entry : entries.stream()
                .filter(MachineRecipeLayout::isSourceText)
                .sorted(Comparator.comparingInt(entry -> entry.role() == RecipeIngredientRole.INPUT ? 0 : 1))
                .toList()) {
            lines.add(new TextPlan(entry, 8, y, CATEGORY_WIDTH - 16));
            y += TEXT_LINE_SPACING;
        }
        return List.copyOf(lines);
    }

    private static boolean isSourceText(JeiDisplayEntry entry) {
        return entry.isTextOnly() && entry.typeId().equals(ArsSourceIds.SOURCE);
    }

    private static int baseMetadataTextY(List<JeiDisplayEntry> entries, int gridRows) {
        int inputRows = visibleRows(entryCount(entries, RecipeIngredientRole.INPUT), gridRows);
        int outputRows = visibleRows(entryCount(entries, RecipeIngredientRole.OUTPUT), gridRows);
        int rowCount = Math.max(1, Math.max(inputRows, outputRows));
        return SLOT_START_Y + rowCount * SLOT_SIZE + TEXT_OFFSET_Y;
    }

    private static int visibleRows(int entryCount, int gridRows) {
        return Math.min(gridRows, (entryCount + COLUMNS - 1) / COLUMNS);
    }

    private static int entryCount(List<JeiDisplayEntry> entries, RecipeIngredientRole role) {
        return (int) entries.stream()
                .filter(entry -> entry.role() == role && !isSourceText(entry) && !isMana(entry)).count();
    }

    private static RegionPlan region(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role,
                                     int startX, boolean rightAlign, int gridRows) {
        List<EntryPlan> entries = new ArrayList<>();
        int itemIndex = 0;
        int fluidIndex = 0;
        int chemicalIndex = 0;
        int textIndex = 0;
        for (JeiDisplayEntry entry : displayEntries.stream()
                .filter(candidate -> candidate.role() == role && !isSourceText(candidate) && !isMana(candidate))
                .sorted(Comparator.comparingInt(MachineRecipeLayout::kindOrder))
                .toList()) {
            if (entry.ingredientType() == VanillaTypes.ITEM_STACK) {
                entries.add(new EntryPlan(Kind.ITEM, itemIndex++, entry));
            } else if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) {
                entries.add(new EntryPlan(Kind.FLUID, fluidIndex++, entry));
            } else if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) {
                entries.add(new EntryPlan(Kind.CHEMICAL, chemicalIndex++, entry));
            } else if (!entry.isTextOnly()) {
                entries.add(new EntryPlan(Kind.GENERIC, textIndex++, entry));
            } else {
                entries.add(new EntryPlan(Kind.TEXT, textIndex++, entry));
            }
        }

        int maxVisible = COLUMNS * gridRows;
        boolean overflowing = entries.size() > maxVisible;
        int visibleCount = Math.min(entries.size(), overflowing ? maxVisible - 1 : maxVisible);
        List<SlotPlan> slots = new ArrayList<>(visibleCount);
        for (int cell = 0; cell < visibleCount; cell++) {
            EntryPlan entry = entries.get(cell);
            int column = cell % COLUMNS;
            int row = cell / COLUMNS;
            if (rightAlign && !(overflowing && row == (maxVisible - 1) / COLUMNS)) {
                int entriesInRow = Math.min(COLUMNS, visibleCount - row * COLUMNS);
                column += COLUMNS - entriesInRow;
            }
            slots.add(new SlotPlan(entry, startX + column * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE));
        }
        List<EntryPlan> hiddenEntries = overflowing
                ? entries.subList(maxVisible - 1, entries.size())
                : List.of();
        OverflowSlotPlan overflowSlot = overflowing ? overflowSlot(startX, rightAlign, maxVisible) : null;
        return new RegionPlan(List.copyOf(slots), overflowSlot, List.copyOf(hiddenEntries));
    }

    static RegionPlan regionForEntries(List<JeiDisplayEntry> entries, RecipeIngredientRole role, int guiScale) {
        return region(entries, role, 12, false, rows(guiScale));
    }

    private static int kindOrder(JeiDisplayEntry entry) {
        if (entry.typeId().equals(ArsSourceIds.SOURCE)) return -1;
        if (entry.ingredientType() == NeoForgeTypes.FLUID_STACK) return 0;
        if (entry.typeId().equals(MekanismRecipeTypes.CHEMICAL)) return 1;
        if (entry.ingredientType() == VanillaTypes.ITEM_STACK) return 2;
        return 3;
    }

    private static OverflowSlotPlan overflowSlot(int startX, boolean rightAlign, int maxVisible) {
        int column = (maxVisible - 1) % COLUMNS;
        int row = (maxVisible - 1) / COLUMNS;
        if (rightAlign) {
            column += COLUMNS - Math.min(COLUMNS, maxVisible - row * COLUMNS);
        }
        return new OverflowSlotPlan(startX + column * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE);
    }

    public boolean hasInputOverflow() {
        return !inputs.hiddenEntries().isEmpty();
    }

    public boolean hasOutputOverflow() {
        return !outputs.hiddenEntries().isEmpty();
    }

    public int levelRequirementY(MachineRecipeDisplay display, int index) {
        return levelRequirementSlotY(display, index);
    }

    public int levelRequirementSlotY(MachineRecipeDisplay display, int index) {
        int metadataY = display.requiredHostIds().isEmpty()
                ? hostRequirementTextY
                : hostRequirementTextY + TEXT_LINE_SPACING;
        return metadataY + SLOT_SIZE * index;
    }

    public int smartInterfaceTextY(MachineRecipeDisplay display) {
        return stageRequirementTextY(display) + TEXT_LINE_SPACING * display.recipe().stageRequirements().size();
    }

    public int stressTextY(MachineRecipeDisplay display) {
        return durationTextY + TEXT_LINE_SPACING * (1 + display.energyInputs().size() + display.energyOutputs().size());
    }

    public int airTextY(MachineRecipeDisplay display) {
        return stressTextY(display) + TEXT_LINE_SPACING * (display.stressInputs().size() + display.stressOutputs().size());
    }

    public int stageRequirementTextY(MachineRecipeDisplay display) {
        int levelCount = display.recipe().levelRequirements().size();
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1) + SLOT_SIZE + TEXT_LINE_SPACING;
        }
        return hostRequirementTextY + TEXT_LINE_SPACING * (display.requiredHostIds().isEmpty() ? 0 : 1);
    }

    public int lastMetadataTextY(MachineRecipeDisplay display) {
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
        int smartInterfaceCount = display.smartInterfaceInputs().size() + display.smartInterfaceOutputs().size();
        return smartInterfaceTextY(display) + TEXT_LINE_SPACING * smartInterfaceCount;
    }

    public int informationLineCapacity(MachineRecipeDisplay display, int categoryHeight) {
        int availableHeight = categoryHeight - informationTextY(display);
        return Math.max(0, availableHeight / TEXT_LINE_SPACING);
    }

    private static int metadataLineCount(MachineRecipeDisplay display) {
        return 1 + display.energyInputs().size() + display.energyOutputs().size()
                + display.stressInputs().size() + display.stressOutputs().size()
                + display.airInputs().size() + display.airOutputs().size()
                + (display.minimumTemperature().isPresent() ? 1 : 0)
                + (display.outputHeat().isPresent() ? 1 : 0);
    }

    public enum Kind { ITEM, FLUID, CHEMICAL, GENERIC, TEXT }

    public record EntryPlan(Kind kind, int index, @Nullable JeiDisplayEntry displayEntry) {
        public EntryPlan(Kind kind, int index) {
            this(kind, index, null);
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
    public record TextPlan(JeiDisplayEntry entry, int x, int y, int width) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TEXT_LINE_SPACING;
        }
    }

    public record OverflowSlotPlan(int x, int y) {}

    public record RegionPlan(List<SlotPlan> slots, @Nullable OverflowSlotPlan overflowSlot, List<EntryPlan> hiddenEntries) {}
}

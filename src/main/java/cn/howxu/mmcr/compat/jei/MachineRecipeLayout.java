package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.Minecraft;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
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

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return forDisplay(display, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                region(display.entries(), RecipeIngredientRole.INPUT, 12, false, guiScale),
                region(display.entries(), RecipeIngredientRole.OUTPUT, 102, true, guiScale),
                sourceTextLines(display, guiScale),
                8,
                hostRequirementTextY(display, guiScale),
                durationTextY(display, guiScale),
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

    private static int maxVisible(int guiScale) {
        return COLUMNS * rows(guiScale);
    }

    private static int hostRequirementTextY(MachineRecipeDisplay display, int guiScale) {
        return durationTextY(display, guiScale) + TEXT_LINE_SPACING * metadataLineCount(display);
    }

    private static int durationTextY(MachineRecipeDisplay display, int guiScale) {
        return baseMetadataTextY(display, guiScale) + sourceTextLines(display, guiScale).size() * TEXT_LINE_SPACING;
    }

    private static List<TextPlan> sourceTextLines(MachineRecipeDisplay display, int guiScale) {
        List<TextPlan> lines = new ArrayList<>();
        int y = baseMetadataTextY(display, guiScale);
        for (JeiDisplayEntry entry : display.entries().stream()
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

    private static int baseMetadataTextY(MachineRecipeDisplay display, int guiScale) {
        int inputRows = visibleRows(entryCount(display, RecipeIngredientRole.INPUT), guiScale);
        int outputRows = visibleRows(entryCount(display, RecipeIngredientRole.OUTPUT), guiScale);
        int rowCount = Math.max(1, Math.max(inputRows, outputRows));
        return SLOT_START_Y + rowCount * SLOT_SIZE + TEXT_OFFSET_Y;
    }

    private static int visibleRows(int entryCount, int guiScale) {
        return Math.min(rows(guiScale), (entryCount + COLUMNS - 1) / COLUMNS);
    }

    private static int entryCount(MachineRecipeDisplay display, RecipeIngredientRole role) {
        return (int) display.entries().stream().filter(entry -> entry.role() == role && !isSourceText(entry)).count();
    }

    private static RegionPlan region(List<JeiDisplayEntry> displayEntries, RecipeIngredientRole role,
                                     int startX, boolean rightAlign, int guiScale) {
        List<EntryPlan> entries = new ArrayList<>();
        int itemIndex = 0;
        int fluidIndex = 0;
        int chemicalIndex = 0;
        int textIndex = 0;
        for (JeiDisplayEntry entry : displayEntries.stream()
                .filter(candidate -> candidate.role() == role && !isSourceText(candidate))
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

        int maxVisible = maxVisible(guiScale);
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
        return region(entries, role, 12, false, guiScale);
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
                ? durationTextY + TEXT_LINE_SPACING * metadataLineCount(display)
                : hostRequirementTextY + TEXT_LINE_SPACING;
        return metadataY + SLOT_SIZE * index;
    }

    public int smartInterfaceTextY(MachineRecipeDisplay display) {
        return stageRequirementTextY(display) + TEXT_LINE_SPACING * display.recipe().stageRequirements().size();
    }

    public int stressTextY(MachineRecipeDisplay display) {
        return durationTextY + TEXT_LINE_SPACING * (1 + display.energyInputs().size() + display.energyOutputs().size());
    }

    public int stageRequirementTextY(MachineRecipeDisplay display) {
        int levelCount = display.recipe().levelRequirements().size();
        if (levelCount > 0) {
            return levelRequirementSlotY(display, levelCount - 1) + SLOT_SIZE + TEXT_LINE_SPACING;
        }
        return durationTextY + TEXT_LINE_SPACING * (metadataLineCount(display)
                + (display.requiredHostIds().isEmpty() ? 0 : 1));
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
        return durationTextY + TEXT_LINE_SPACING * (metadataLineCount(display) - 1
                + (display.requiredHostIds().isEmpty() ? 0 : 1));
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

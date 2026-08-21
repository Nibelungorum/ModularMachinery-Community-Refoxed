package cn.howxu.mmcr.compat.jei;

import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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
        int durationTextX,
        int hostRequirementTextY,
        int durationTextY,
        int transferButtonX,
        int transferButtonY
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 150;

    private static final int COLUMNS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_START_Y = 8;
    private static final int TEXT_OFFSET_Y = 4;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return forDisplay(display, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display, int guiScale) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                region(display.fluidInputs().size(), display.itemInputs().size(), 12, false, guiScale),
                region(display.fluidOutputs().size(), display.itemOutputs().size(), 102, true, guiScale),
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
        return durationTextY(display, guiScale) + 10 * (1 + display.energyInputs().size() + display.energyOutputs().size());
    }

    private static int durationTextY(MachineRecipeDisplay display, int guiScale) {
        return baseMetadataTextY(display, guiScale);
    }

    private static int baseMetadataTextY(MachineRecipeDisplay display, int guiScale) {
        int inputRows = visibleRows(display.fluidInputs().size() + display.itemInputs().size(), guiScale);
        int outputRows = visibleRows(display.fluidOutputs().size() + display.itemOutputs().size(), guiScale);
        int rowCount = Math.max(1, Math.max(inputRows, outputRows));
        return SLOT_START_Y + rowCount * SLOT_SIZE + TEXT_OFFSET_Y;
    }

    private static int visibleRows(int entryCount, int guiScale) {
        return Math.min(rows(guiScale), (entryCount + COLUMNS - 1) / COLUMNS);
    }

    private static RegionPlan region(int fluidCount, int itemCount, int startX, boolean rightAlign, int guiScale) {
        List<EntryPlan> entries = new ArrayList<>(fluidCount + itemCount);
        for (int index = 0; index < fluidCount; index++) {
            entries.add(new EntryPlan(Kind.FLUID, index));
        }
        for (int index = 0; index < itemCount; index++) {
            entries.add(new EntryPlan(Kind.ITEM, index));
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
        int metadataY = display.requiredHostIds().isEmpty()
                ? durationTextY + 10 * (1 + display.energyInputs().size() + display.energyOutputs().size())
                : hostRequirementTextY + 10;
        return metadataY + 10 * index;
    }

    public int lastMetadataTextY(MachineRecipeDisplay display) {
        return durationTextY + 10 * (display.energyInputs().size() + display.energyOutputs().size()
                + (display.requiredHostIds().isEmpty() ? 0 : 1)
                + display.recipe().levelRequirements().size() + display.smartInterfaceInputs().size()
                + display.smartInterfaceOutputs().size() + display.smartInterfaceModifiers().size());
    }

    public enum Kind { ITEM, FLUID }

    public record EntryPlan(Kind kind, int index) {}

    public record SlotPlan(EntryPlan entry, int x, int y) {}

    public record OverflowSlotPlan(int x, int y) {}

    public record RegionPlan(List<SlotPlan> slots, @Nullable OverflowSlotPlan overflowSlot, List<EntryPlan> hiddenEntries) {}
}

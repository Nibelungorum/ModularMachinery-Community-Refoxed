package cn.howxu.mmcr.compat.jei;

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
        int durationTextY
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 150;

    private static final int COLUMNS = 3;
    private static final int ROWS = 6;
    private static final int MAX_VISIBLE = COLUMNS * ROWS;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_START_Y = 8;
    private static final int TEXT_OFFSET_Y = 4;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                region(display.fluidInputs().size(), display.itemInputs().size(), 8),
                region(display.fluidOutputs().size(), display.itemOutputs().size(), 91, true),
                8,
                durationTextY(display)
        );
    }

    private static int durationTextY(MachineRecipeDisplay display) {
        int inputRows = visibleRows(display.fluidInputs().size() + display.itemInputs().size());
        int outputRows = visibleRows(display.fluidOutputs().size() + display.itemOutputs().size());
        return SLOT_START_Y + Math.max(inputRows, outputRows) * SLOT_SIZE + TEXT_OFFSET_Y;
    }

    private static int visibleRows(int entryCount) {
        return Math.min(ROWS, (entryCount + COLUMNS - 1) / COLUMNS);
    }

    private static RegionPlan region(int fluidCount, int itemCount, int startX) {
        return region(fluidCount, itemCount, startX, false);
    }

    private static RegionPlan region(int fluidCount, int itemCount, int startX, boolean rightAlign) {
        List<EntryPlan> entries = new ArrayList<>(fluidCount + itemCount);
        for (int index = 0; index < fluidCount; index++) {
            entries.add(new EntryPlan(Kind.FLUID, index));
        }
        for (int index = 0; index < itemCount; index++) {
            entries.add(new EntryPlan(Kind.ITEM, index));
        }

        boolean overflowing = entries.size() > MAX_VISIBLE;
        int visibleCount = Math.min(entries.size(), overflowing ? MAX_VISIBLE - 1 : MAX_VISIBLE);
        List<SlotPlan> slots = new ArrayList<>(visibleCount);
        for (int cell = 0; cell < visibleCount; cell++) {
            EntryPlan entry = entries.get(cell);
            int column = cell % COLUMNS;
            int row = cell / COLUMNS;
            if (rightAlign && !(overflowing && row == (MAX_VISIBLE - 1) / COLUMNS)) {
                int entriesInRow = Math.min(COLUMNS, visibleCount - row * COLUMNS);
                column += COLUMNS - entriesInRow;
            }
            slots.add(new SlotPlan(entry, startX + column * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE));
        }
        List<EntryPlan> hiddenEntries = overflowing
                ? entries.subList(MAX_VISIBLE - 1, entries.size())
                : List.of();
        OverflowSlotPlan overflowSlot = overflowing ? overflowSlot(startX, rightAlign) : null;
        return new RegionPlan(List.copyOf(slots), overflowSlot, List.copyOf(hiddenEntries));
    }

    private static OverflowSlotPlan overflowSlot(int startX, boolean rightAlign) {
        int column = (MAX_VISIBLE - 1) % COLUMNS;
        int row = (MAX_VISIBLE - 1) / COLUMNS;
        if (rightAlign) {
            column += COLUMNS - Math.min(COLUMNS, MAX_VISIBLE - row * COLUMNS);
        }
        return new OverflowSlotPlan(startX + column * SLOT_SIZE, SLOT_START_Y + row * SLOT_SIZE);
    }

    public boolean hasInputOverflow() {
        return !inputs.hiddenEntries().isEmpty();
    }

    public boolean hasOutputOverflow() {
        return !outputs.hiddenEntries().isEmpty();
    }

    public enum Kind { ITEM, FLUID }

    public record EntryPlan(Kind kind, int index) {}

    public record SlotPlan(EntryPlan entry, int x, int y) {}

    public record OverflowSlotPlan(int x, int y) {}

    public record RegionPlan(List<SlotPlan> slots, @Nullable OverflowSlotPlan overflowSlot, List<EntryPlan> hiddenEntries) {}
}

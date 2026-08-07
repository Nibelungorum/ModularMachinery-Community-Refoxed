package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
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
    private static final int ROWS = 7;
    private static final int MAX_VISIBLE = COLUMNS * ROWS;
    private static final int SLOT_SIZE = 18;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                region(display.fluidInputs().size(), display.itemInputs().size(), 8),
                region(display.fluidOutputs().size(), display.itemOutputs().size(), 91, true),
                8,
                132
        );
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
            if (rightAlign) {
                int entriesInRow = Math.min(COLUMNS, visibleCount - row * COLUMNS);
                column += COLUMNS - entriesInRow;
            }
            slots.add(new SlotPlan(entry, startX + column * SLOT_SIZE, 3 + row * SLOT_SIZE));
        }
        List<EntryPlan> hiddenEntries = overflowing
                ? entries.subList(MAX_VISIBLE - 1, entries.size())
                : List.of();
        OverflowSlotPlan overflowSlot = overflowing ? overflowSlot(startX, rightAlign) : null;
        if (MMCR.LOG.isDebugEnabled()) {
            MMCR.LOG.debug("JEI machine recipe layout region: fluidCount={}, itemCount={}, totalEntries={}, maxVisible={}, visibleSlots={}, hiddenEntries={}, overflowSlot={}, startX={}, rightAlign={}",
                    fluidCount, itemCount, entries.size(), MAX_VISIBLE, slots.size(), hiddenEntries.size(), overflowSlot, startX, rightAlign);
            for (int i = 0; i < slots.size(); i++) {
                SlotPlan slot = slots.get(i);
                MMCR.LOG.debug("JEI machine recipe layout slot: slotIndex={}, entry={}, x={}, y={}", i, slot.entry(), slot.x(), slot.y());
            }
            for (int i = 0; i < hiddenEntries.size(); i++) {
                MMCR.LOG.debug("JEI machine recipe layout hidden entry: hiddenIndex={}, entry={}", i, hiddenEntries.get(i));
            }
        }
        return new RegionPlan(List.copyOf(slots), overflowSlot, List.copyOf(hiddenEntries));
    }

    private static OverflowSlotPlan overflowSlot(int startX, boolean rightAlign) {
        int column = (MAX_VISIBLE - 1) % COLUMNS;
        int row = (MAX_VISIBLE - 1) / COLUMNS;
        if (rightAlign) {
            column += COLUMNS - Math.min(COLUMNS, MAX_VISIBLE - row * COLUMNS);
        }
        return new OverflowSlotPlan(startX + column * SLOT_SIZE, 3 + row * SLOT_SIZE);
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

package cn.howxu.mmcr.compat.jei;

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

    public static final int WIDTH = 214;
    public static final int HEIGHT = 168;

    private static final int COLUMNS = 4;
    private static final int ROWS = 8;
    private static final int MAX_VISIBLE = COLUMNS * ROWS;
    private static final int SLOT_SIZE = 18;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                region(display.fluidInputs().size(), display.itemInputs().size(), 8),
                region(display.fluidOutputs().size(), display.itemOutputs().size(), 134),
                84,
                42
        );
    }

    private static RegionPlan region(int fluidCount, int itemCount, int startX) {
        List<EntryPlan> entries = new ArrayList<>(fluidCount + itemCount);
        for (int index = 0; index < fluidCount; index++) {
            entries.add(new EntryPlan(Kind.FLUID, index));
        }
        for (int index = 0; index < itemCount; index++) {
            entries.add(new EntryPlan(Kind.ITEM, index));
        }

        int visibleCount = Math.min(entries.size(), MAX_VISIBLE);
        List<SlotPlan> slots = new ArrayList<>(visibleCount);
        for (int cell = 0; cell < visibleCount; cell++) {
            EntryPlan entry = entries.size() > MAX_VISIBLE && cell == MAX_VISIBLE - 1 ? null : entries.get(cell);
            slots.add(new SlotPlan(entry, startX + (cell % COLUMNS) * SLOT_SIZE, 18 + (cell / COLUMNS) * SLOT_SIZE));
        }
        List<EntryPlan> hiddenEntries = entries.size() > MAX_VISIBLE
                ? entries.subList(MAX_VISIBLE - 1, entries.size())
                : List.of();
        return new RegionPlan(List.copyOf(slots), List.copyOf(hiddenEntries));
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

    public record RegionPlan(List<SlotPlan> slots, List<EntryPlan> hiddenEntries) {}
}

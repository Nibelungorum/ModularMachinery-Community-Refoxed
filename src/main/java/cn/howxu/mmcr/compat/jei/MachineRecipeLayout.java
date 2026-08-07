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
        List<SlotPlan> itemInputs,
        List<SlotPlan> itemOutputs,
        List<SlotPlan> fluidInputs,
        List<SlotPlan> fluidOutputs,
        List<SlotPlan> energyInputs,
        List<SlotPlan> energyOutputs,
        int durationTextX,
        int durationTextY
) {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 78;

    private static final int ITEM_INPUTS_PER_ROW = 4;
    private static final int ITEM_OUTPUTS_PER_ROW = 1;
    private static final int FLUID_PER_ROW = 1;
    private static final int ENERGY_PER_ROW = 1;

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                grid(display.itemInputs().size(), Kind.ITEM, Role.INPUT, 8, 18, ITEM_INPUTS_PER_ROW),
                grid(display.itemOutputs().size(), Kind.ITEM, Role.OUTPUT, 124, 18, ITEM_OUTPUTS_PER_ROW),
                grid(display.fluidInputs().size(), Kind.FLUID, Role.INPUT, 82, 18, FLUID_PER_ROW),
                grid(display.fluidOutputs().size(), Kind.FLUID, Role.OUTPUT, 102, 18, FLUID_PER_ROW),
                grid(display.energyInputs().size(), Kind.ENERGY, Role.INPUT, 56, 18, ENERGY_PER_ROW),
                grid(display.energyOutputs().size(), Kind.ENERGY, Role.OUTPUT, 78, 18, ENERGY_PER_ROW),
                8,
                60
        );
    }

    private static List<SlotPlan> grid(int count, Kind kind, Role role, int x, int y, int maxPerRow) {
        List<SlotPlan> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int row = i / maxPerRow;
            int col = i % maxPerRow;
            slots.add(new SlotPlan(kind, role, i, x + col * 18, y + row * 18));
        }
        return List.copyOf(slots);
    }

    public enum Kind { ITEM, FLUID, ENERGY }

    public enum Role { INPUT, OUTPUT }

    public record SlotPlan(Kind kind, Role role, int index, int x, int y) {
    }
}

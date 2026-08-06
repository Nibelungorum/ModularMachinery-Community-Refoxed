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

    public static MachineRecipeLayout forDisplay(MachineRecipeDisplay display) {
        return new MachineRecipeLayout(
                WIDTH,
                HEIGHT,
                line(display.itemInputs().size(), Kind.ITEM, Role.INPUT, 8, 18),
                line(display.itemOutputs().size(), Kind.ITEM, Role.OUTPUT, 124, 18),
                line(display.fluidInputs().size(), Kind.FLUID, Role.INPUT, 32, 18),
                line(display.fluidOutputs().size(), Kind.FLUID, Role.OUTPUT, 100, 18),
                line(display.energyInputs().size(), Kind.ENERGY, Role.INPUT, 56, 18),
                line(display.energyOutputs().size(), Kind.ENERGY, Role.OUTPUT, 78, 18),
                8,
                60
        );
    }

    private static List<SlotPlan> line(int count, Kind kind, Role role, int x, int y) {
        List<SlotPlan> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(new SlotPlan(kind, role, i, x, y + i * 18));
        }
        return List.copyOf(slots);
    }

    public enum Kind { ITEM, FLUID, ENERGY }

    public enum Role { INPUT, OUTPUT }

    public record SlotPlan(Kind kind, Role role, int index, int x, int y) {
    }
}

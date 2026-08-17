package cn.howxu.mmcr.api.recipe.helper;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Centralizes per-tick recipe energy IO so all input hatches are simulated before mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyRecipeIo {

    private EnergyRecipeIo() {
    }

    public static boolean consumeInputs(List<? extends IEnergyStorage> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            available += input.extractEnergy(required - available, true);
            if (available >= required) break;
        }
        if (available < required) return false;

        int remaining = required;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            remaining -= input.extractEnergy(remaining, false);
            if (remaining <= 0) return true;
        }
        return false;
    }

    public static boolean canConsumeInputs(List<? extends IEnergyStorage> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        for (IEnergyStorage input : inputs) {
            if (input == null) continue;
            available += input.extractEnergy(required - available, true);
            if (available >= required) return true;
        }
        return false;
    }

    public static boolean produceOutputs(List<? extends IEnergyStorage> outputs, int producedFe, int multiplier) {
        if (!canProduceOutputs(outputs, producedFe, multiplier)) return false;
        int remaining = totalRequired(producedFe, multiplier);
        for (IEnergyStorage output : outputs) {
            if (output == null) continue;
            remaining -= output.receiveEnergy(remaining, false);
            if (remaining <= 0) return true;
        }
        return remaining <= 0;
    }

    public static boolean canProduceOutputs(List<? extends IEnergyStorage> outputs, int producedFe, int multiplier) {
        int produced = totalRequired(producedFe, multiplier);
        if (produced <= 0) return true;
        if (outputs == null || outputs.isEmpty()) return false;

        int available = 0;
        for (IEnergyStorage output : outputs) {
            if (output == null) continue;
            available += output.receiveEnergy(produced - available, true);
            if (available >= produced) return true;
        }
        return false;
    }

    private static int totalRequired(int requiredFe, int multiplier) {
        if (requiredFe <= 0 || multiplier <= 0) return 0;
        long required = (long) requiredFe * multiplier;
        return required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
    }
}

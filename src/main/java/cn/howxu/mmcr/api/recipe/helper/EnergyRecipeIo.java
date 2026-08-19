package cn.howxu.mmcr.api.recipe.helper;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;

/**
 * Centralizes per-tick recipe energy IO so all input hatches are simulated before mutation.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyRecipeIo {

    private EnergyRecipeIo() {
    }

    public static boolean consumeInputs(List<? extends EnergyHandler> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyHandler input : inputs) {
                if (input == null) continue;
                available += input.extract(required - available, tx);
                if (available >= required) break;
            }
        }
        if (available < required) return false;

        try (Transaction tx = Transaction.openRoot()) {
            int remaining = required;
            for (EnergyHandler input : inputs) {
                if (input == null) continue;
                remaining -= input.extract(remaining, tx);
                if (remaining <= 0) {
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canConsumeInputs(List<? extends EnergyHandler> inputs, int requiredFe, int multiplier) {
        int required = totalRequired(requiredFe, multiplier);
        if (required <= 0) return true;
        if (inputs == null || inputs.isEmpty()) return false;

        int available = 0;
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyHandler input : inputs) {
                if (input == null) continue;
                available += input.extract(required - available, tx);
                if (available >= required) return true;
            }
        }
        return false;
    }

    public static boolean produceOutputs(List<? extends EnergyHandler> outputs, int producedFe, int multiplier) {
        if (!canProduceOutputs(outputs, producedFe, multiplier)) return false;
        int remaining = totalRequired(producedFe, multiplier);
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyHandler output : outputs) {
                if (output == null) continue;
                remaining -= output.insert(remaining, tx);
                if (remaining <= 0) {
                    tx.commit();
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canProduceOutputs(List<? extends EnergyHandler> outputs, int producedFe, int multiplier) {
        int produced = totalRequired(producedFe, multiplier);
        if (produced <= 0) return true;
        if (outputs == null || outputs.isEmpty()) return false;

        int available = 0;
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyHandler output : outputs) {
                if (output == null) continue;
                available += output.insert(produced - available, tx);
                if (available >= produced) return true;
            }
        }
        return false;
    }

    private static int totalRequired(int requiredFe, int multiplier) {
        if (requiredFe <= 0 || multiplier <= 0) return 0;
        long required = (long) requiredFe * multiplier;
        return required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
    }
}

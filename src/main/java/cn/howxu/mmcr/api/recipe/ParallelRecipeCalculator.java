package cn.howxu.mmcr.api.recipe;

/**
 * Calculates the largest parallel craft amount supported by current inputs and outputs.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ParallelRecipeCalculator {
    private ParallelRecipeCalculator() {
    }

    public static int maxStartableParallelism(RecipeCraftingContext context, MachineRecipe recipe, int parallelLimit) {
        if (context == null) throw new IllegalArgumentException("context null");
        if (recipe == null) return 0;
        int limit = Math.max(1, parallelLimit);
        if (!recipe.isParallelized() || limit == 1) {
            return safeSimulateInputs(context, recipe, 1) && safeSimulateOutputs(context, recipe, 1) ? 1 : 0;
        }
        if (!safeSimulateInputs(context, recipe, 1) || !safeSimulateOutputs(context, recipe, 1)) return 0;

        int inputLimit = context.maxInputParallelism(recipe, limit);
        if (inputLimit < 0) inputLimit = maxByInput(context, recipe, limit);
        return inputLimit <= 0 ? 0 : limitByOutput(context, recipe, inputLimit);
    }

    private static int maxByInput(RecipeCraftingContext context, MachineRecipe recipe, int limit) {
        int low = 1;
        int high = limit;
        int best = 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (safeSimulateInputs(context, recipe, mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static int limitByOutput(RecipeCraftingContext context, MachineRecipe recipe, int inputLimit) {
        int low = 1;
        int high = inputLimit;
        int best = 0;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (safeSimulateOutputs(context, recipe, mid)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static boolean safeSimulateInputs(RecipeCraftingContext context, MachineRecipe recipe, int parallelism) {
        try {
            return context.simulateInputs(recipe, parallelism);
        } catch (IllegalArgumentException overflow) {
            return false;
        }
    }

    private static boolean safeSimulateOutputs(RecipeCraftingContext context, MachineRecipe recipe, int parallelism) {
        try {
            return context.simulateOutputs(recipe, parallelism);
        } catch (IllegalArgumentException overflow) {
            return false;
        }
    }
}

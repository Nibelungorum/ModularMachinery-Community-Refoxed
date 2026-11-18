package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.modifier.ModifierTarget;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;

/** Pure resolver for one source recipe and one immutable controller modifier snapshot.
 * @author howxu <dev@howxu.cn>
 */
public final class EffectiveRecipeResolver {

    public EffectiveRecipe resolve(MachineRecipe recipe, ControllerRuntimeSnapshot snapshot,
                                   List<MachineModifier> modifiers) {
        return resolve(recipe, snapshot, modifiers, ignored -> {});
    }

    public EffectiveRecipe resolve(MachineRecipe recipe, ControllerRuntimeSnapshot snapshot,
                                   List<MachineModifier> modifiers, Consumer<MachineRequirement> dependency) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(snapshot, "snapshot");
        List<MachineModifier> effectiveModifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        List<RecipeModifier> recipeModifiers = MachineModifier.recipeModifiers(effectiveModifiers);
        List<RecipeModifier> durationModifiers = new ArrayList<>(recipe.modifiers());
        durationModifiers.addAll(recipeModifiers);

        int duration = boundedInt(IntegrationTypeHelper.applyDuration(durationModifiers,
                recipe.getRecipeTotalTickTime()), 1, Integer.MAX_VALUE);
        boolean parallelized = resolveParallelized(recipe.isParallelized(), effectiveModifiers);
        long parallelism = parallelized
                ? Math.max(1L, snapshot.maxParallelism())
                : 1L;
        int factoryThreads = Math.max(1, snapshot.factory().laneLimit());
        int sourceRecipeThreads = recipe.maxThreads() <= 0 ? Integer.MAX_VALUE : recipe.maxThreads();
        int recipeThreads = boundedInt(MachineModifier.apply(effectiveModifiers, ModifierTarget.RECIPE_THREADS,
                sourceRecipeThreads, false), 1, Integer.MAX_VALUE);

        return new EffectiveRecipe(recipe, recipe.deriveRequirements(recipeModifiers, 1D, 1D, dependency),
                recipe.runtimeMachineOutputs(recipeModifiers), duration, parallelism, factoryThreads, recipeThreads,
                parallelized, snapshot.structure().version(), snapshot.capabilityVersion(), snapshot.modifierVersion(),
                snapshot.stateVersion());
    }

    private static boolean resolveParallelized(boolean recipeValue, List<MachineModifier> modifiers) {
        boolean enabled = recipeValue;
        for (MachineModifier modifier : modifiers) {
            if (modifier instanceof MachineModifier.Parallelized parallelized) {
                if (!parallelized.value()) return false;
                enabled = true;
            }
        }
        return enabled;
    }

    private static int boundedInt(double value, int minimum, int maximum) {
        if (value <= minimum) return minimum;
        if (value >= maximum) return maximum;
        return (int) Math.round(value);
    }

    private static long boundedLong(double value, long minimum, long maximum) {
        if (value <= minimum) return minimum;
        if (value >= maximum) return maximum;
        return Math.round(value);
    }
}

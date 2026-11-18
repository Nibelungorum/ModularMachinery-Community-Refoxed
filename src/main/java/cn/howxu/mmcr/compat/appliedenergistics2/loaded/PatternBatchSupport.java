package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-thread inspection only; profiles contain no live runtime, reservation or material view.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchSupport {
    public enum Safety { PROVEN_LINEAR, UNPROVEN }

    /** @author howxu <dev@howxu.cn> */
    public record Profile(PatternBatchBinding binding, long maxMultiplier, Safety safety) {
        public Profile {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(safety, "safety");
            if (maxMultiplier < 1L) throw new IllegalArgumentException("Multiplier must be positive");
        }
    }

    private PatternBatchSupport() { }

    public static List<Profile> inspect(PatternInterfaceBlockEntity host, IPatternDetails original) {
        if (host == null || host.isRemoved() || host.getLevel() == null
                || host.getLevel().isClientSide() || !(original instanceof AEProcessingPattern)) return List.of();
        Map<PatternBatchBinding, Profile> profiles = new LinkedHashMap<>();
        for (var position : host.linkedControllerPositions()) {
            if (!host.getLevel().hasChunkAt(position)) continue;
            if (!(host.getLevel().getBlockEntity(position) instanceof MachineControllerBlockEntity controller)
                    || controller.isRemoved()
                    || !controller.currentRuntimeSnapshot().linkedPortPositions().contains(host.getBlockPos())) continue;
            for (MachineRecipe recipe : controller.recipesForMachine()) {
                if (!controller.supportsPatternRecipe(recipe)) continue;
                EffectiveRecipe effective = controller.effectivePatternRecipe(recipe);
                if (effective == null) continue;
                Profile profile = inspectRecipe(recipe, effective, original);
                if (profile == null) continue;
                Profile next = profile;
                profiles.merge(next.binding(), next, (first, second) -> new Profile(first.binding(),
                        Math.max(first.maxMultiplier(), second.maxMultiplier()),
                        first.safety() == Safety.UNPROVEN || second.safety() == Safety.UNPROVEN
                                ? Safety.UNPROVEN : Safety.PROVEN_LINEAR));
            }
        }
        return List.copyOf(profiles.values());
    }

    /** Pure counterpart used by tests and by execution revalidation. */
    public static Profile inspectRecipe(MachineRecipe recipe, EffectiveRecipe effective, IPatternDetails original) {
        if (recipe == null || effective == null || original == null || effective.source() != recipe) return null;
        try {
            Map<AEKey, Long> outputs = outputTotals(effective.outputs());
            Map<AEKey, Long> promised = stackTotals(original.getOutputs());
            if (outputs.isEmpty() || !outputs.keySet().equals(promised.keySet())) return null;
            long crafts = 0L;
            for (var output : outputs.entrySet()) {
                long amount = promised.get(output.getKey());
                if (amount % output.getValue() != 0L) return null;
                long ratio = amount / output.getValue();
                if (ratio < 1L || crafts != 0L && ratio != crafts) return null;
                crafts = ratio;
            }
            if (!inputsMatch(effective.requirements(), original)) return null;
            long maximum = effective.parallelismLimit() / crafts;
            for (long amount : promised.values()) maximum = Math.min(maximum, Long.MAX_VALUE / amount);
            Map<AEKey, Long> inputAmounts = new LinkedHashMap<>();
            for (var input : original.getInputs()) {
                // Alternatives are conservatively all counted; nothing is expanded per craft.
                for (GenericStack stack : input.getPossibleInputs()) {
                    inputAmounts.merge(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()), Math::addExact);
                }
            }
            for (long amount : inputAmounts.values()) {
                if (amount <= 0L) return null;
                maximum = Math.min(maximum, Long.MAX_VALUE / amount);
            }
            // This is bulk input delivery, not permission to bypass the recipe runtime.
            // Conditions, custom requirements and callbacks are checked by actual start planning.
            boolean linear = supportsBatchInputs(effective.requirements());
            for (MachineRequirement requirement : effective.requirements()) {
                var quantity = requirement.batchAmount(effective.duration());
                if (quantity.isEmpty()) continue;
                long amount = quantity.getAsLong();
                if (amount <= 0L) return null;
                maximum = Math.min(maximum, Long.MAX_VALUE / Math.multiplyExact(amount, crafts));
            }
            if (maximum < 1L) return null;
            return new Profile(new PatternBatchBinding(recipe.recipePoolId(), recipe.id(), crafts), maximum,
                    linear ? Safety.PROVEN_LINEAR : Safety.UNPROVEN);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return null;
        }
    }

    public static boolean matches(PatternBatchBinding binding, MachineRecipe recipe, EffectiveRecipe effective,
                                  IPatternDetails original) {
        if (binding == null || recipe == null || !binding.recipePoolId().equals(recipe.recipePoolId())
                || !binding.recipeId().equals(recipe.id())) return false;
        Profile profile = inspectRecipe(recipe, effective, original);
        return profile != null && profile.binding().equals(binding);
    }

    /** Applied both during publication and after beforeStart has supplied the actual requirements. */
    public static boolean supportsBatchInputs(List<MachineRequirement> requirements) {
        for (MachineRequirement requirement : requirements) {
            if (requirement.io() != IOType.INPUT) continue;
            float consumeChance = requirement.consumeChance();
            if (consumeChance != 0F && consumeChance != 1F) return false;
        }
        return true;
    }

    private static boolean inputsMatch(List<MachineRequirement> requirements, IPatternDetails original) {
        for (MachineRequirement requirement : requirements) {
            if (requirement.io() != IOType.INPUT) continue;
            for (var family : AE2ResourceFamilies.all()) {
                if (!inputMatches(family.adapter(), requirement, original)) return false;
            }
        }
        return true;
    }

    private static Map<AEKey, Long> stackTotals(List<GenericStack> stacks) {
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() < 1L) throw new IllegalArgumentException("Invalid pattern output");
            totals.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return totals;
    }

    private static Map<AEKey, Long> outputTotals(List<MachineOutput> outputs) {
        Map<AEKey, Long> totals = new LinkedHashMap<>();
        for (MachineOutput output : outputs) {
            AEKey key = AE2ResourceFamilies.patternKey(output).orElseThrow(() -> new IllegalArgumentException("Unencodable pattern output"));
            long amount = MachineOutput.scaledAmount(output);
            if (key == null || amount <= 0L) throw new IllegalArgumentException("Invalid output");
            totals.merge(key, amount, Math::addExact);
        }
        return totals;
    }

    private static <R> boolean inputMatches(AE2KeyAdapter<R> adapter, MachineRequirement requirement, IPatternDetails original) {
        var input = adapter.patternInput(requirement);
        if (input == null) return true; // Other port inputs and conditions are checked at actual start.
        for (var slot : original.getInputs()) {
            for (var candidate : slot.getPossibleInputs()) {
                if (adapter.toResource(candidate.what()).filter(input).isPresent()) return true;
            }
        }
        return false;
    }
}

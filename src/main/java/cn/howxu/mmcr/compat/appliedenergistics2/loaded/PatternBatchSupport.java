package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.EffectiveRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningBridge;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.AirRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchBinding;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Server-thread inspection only; profiles contain no live runtime, reservation or material view.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchSupport {
    /** @author howxu <dev@howxu.cn> */
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

    /** Pure recipe analysis; foreign AE resource keys are resolved only through optional bridges. */
    public static Profile inspectRecipe(MachineRecipe recipe, EffectiveRecipe effective, IPatternDetails original) {
        if (recipe == null || effective == null || original == null || effective.source() != recipe) return null;
        try {
            Map<Object, Long> outputs = outputTotals(effective.outputs());
            Map<Object, Long> promised = stackTotals(original.getOutputs());
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
                long amount;
                if (requirement instanceof ItemRequirement item) {
                    amount = item.io() == IOType.INPUT ? item.count() : item.stack().getCount();
                } else if (requirement instanceof FluidRequirement fluid) {
                    amount = fluid.io() == IOType.INPUT ? fluid.amount() : fluid.stack().getAmount();
                } else if (requirement instanceof EnergyRequirement energy) {
                    amount = Math.multiplyExact(energy.fePerTick(), (long) effective.duration());
                } else if (requirement instanceof LightningRequirement lightning) {
                    amount = lightning.amount();
                } else if (requirement instanceof LoadedChemicalRequirement chemical) {
                    amount = chemical.ingredient().amount();
                } else {
                    continue;
                }
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
            float consumeChance = switch (requirement) {
                case ItemRequirement item -> item.consumeChance();
                case FluidRequirement fluid -> fluid.consumeChance();
                case LoadedChemicalRequirement chemical -> chemical.consumeChance();
                case LightningRequirement lightning -> lightning.chance(); // Lightning inputs currently require 1.
                case SourceRequirement source -> source.consumeChance();
                case ManaRequirement mana -> mana.consumeChance();
                case AirRequirement air -> air.consumeChance();
                default -> 1F;
            };
            if (consumeChance != 0F && consumeChance != 1F) return false;
        }
        return true;
    }

    private static boolean inputsMatch(List<MachineRequirement> requirements, IPatternDetails original) {
        for (MachineRequirement requirement : requirements) {
            if (requirement.io() != IOType.INPUT) continue;
            if (!(requirement instanceof ItemRequirement) && !(requirement instanceof FluidRequirement)
                    && !(requirement instanceof LightningRequirement)
                    && !(requirement instanceof LoadedChemicalRequirement)) continue;
            // Chemical tags are resolved by the actual start planner, not by this publication lookup.
            if (requirement instanceof LoadedChemicalRequirement chemical
                    && chemical.ingredient().kind() != ChemicalIngredient.Kind.CHEMICAL) continue;
            boolean matched = false;
            for (var input : original.getInputs()) {
                for (GenericStack candidate : input.getPossibleInputs()) {
                    if (requirement instanceof ItemRequirement item && candidate.what() instanceof AEItemKey key
                            && item.item() != null && item.item().test(key.toStack())
                            && item.components().matches(key.toStack())) matched = true;
                    if (requirement instanceof FluidRequirement fluid && candidate.what() instanceof AEFluidKey key
                            && fluid.fluid() != null && fluid.fluid().test(key.toStack(1))) matched = true;
                    if (requirement instanceof LightningRequirement lightning) {
                        MachineOutput output = LightningBridge.get().patternOutput(candidate.what(), 1L).orElse(null);
                        if (output instanceof LightningOutput value && value.tier().equals(lightning.tier())) matched = true;
                    }
                    if (requirement instanceof LoadedChemicalRequirement chemical) {
                        MachineOutput output = AppMekBridge.get().patternOutput(candidate.what(), 1L).orElse(null);
                        if (output instanceof LoadedChemicalOutput value && value.id().equals(chemical.ingredient().id())) matched = true;
                    }
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static Map<Object, Long> stackTotals(List<GenericStack> stacks) {
        Map<Object, Long> totals = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() < 1L) throw new IllegalArgumentException("Invalid pattern output");
            Object identity;
            if (stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey) identity = stack.what();
            else {
                MachineOutput output = AppMekBridge.get().patternOutput(stack.what(), 1L).orElse(null);
                if (output == null) output = LightningBridge.get().patternOutput(stack.what(), 1L).orElse(null);
                if (output == null) throw new IllegalArgumentException("Unknown pattern output family");
                identity = outputIdentity(output);
            }
            totals.merge(identity, stack.amount(), Math::addExact);
        }
        return totals;
    }

    private static Map<Object, Long> outputTotals(List<MachineOutput> outputs) {
        Map<Object, Long> totals = new LinkedHashMap<>();
        for (MachineOutput output : outputs) {
            Object identity = outputIdentity(output);
            long amount = MachineOutput.scaledAmount(output);
            if (identity == null || amount <= 0L) throw new IllegalArgumentException("Invalid output");
            totals.merge(identity, amount, Math::addExact);
        }
        return totals;
    }

    private static Object outputIdentity(MachineOutput output) {
        if (output instanceof MachineOutput.ItemOutput item) {
            if (item.components().hasNonExactValues()) throw new IllegalArgumentException("Non-exact output components");
            return AEItemKey.of(item.resolvedStack());
        }
        if (output instanceof MachineOutput.FluidOutput fluid) return AEFluidKey.of(fluid.stack());
        if (output instanceof LightningOutput lightning) return lightning.tier();
        if (output instanceof LoadedChemicalOutput chemical) return new ChemicalIdentity(chemical.id());
        throw new IllegalArgumentException("Unknown output family");
    }

    /** Chemical IDs must never share MachineOutput's generic empty aggregation key.
     * @author howxu <dev@howxu.cn>
     */
    private record ChemicalIdentity(ResourceLocation id) { }
}

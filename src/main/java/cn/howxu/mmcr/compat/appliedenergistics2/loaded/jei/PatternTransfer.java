package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.integrations.jei.GenericEntryStackHelper;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapterRegistry;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.compat.jei.JeiRecipeLine;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Restores provider-declared amounts when a JEI ingredient carries only a key.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternTransfer {
    private PatternTransfer() {}

    public static Transfer prepare(MachineRecipeDisplay display, List<List<GenericStack>> nativeInputs,
                                   List<GenericStack> nativeOutputs) {
        Map<AEKey, Long> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
        BitSet claimedInputs = new BitSet(), claimedOutputs = new BitSet();
        var outputSlots = nativeOutputs.stream().map(List::of).toList();
        for (MachineRequirement requirement : display.recipe().runtimeRequirements()) {
            GenericStack exact = null;
            long limit = Long.MAX_VALUE;
            for (var family : AE2ResourceFamilies.all()) {
                var stack = family.adapter().exactJeiStack(requirement);
                if (stack == null) continue;
                if (exact != null || stack.amount() <= 0L || AE2ResourceFamilies.forKey(stack.what()).orElse(null) != family) {
                    return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
                }
                exact = stack;
                limit = family.adapter().maxPatternTotal();
            }
            boolean input = requirement.io().isInput();
            if (exact == null) {
                var entry = MachineRecipeDisplay.entry(requirement);
                if (!requirement.paysAtStart() && (input || requirement.perTickOutput() || entry.isEmpty() && !JeiRecipeLine.Registry.contains(requirement))) continue;
                if (entry.isEmpty() || !entry.get().hasNativePatternSlot() || !claimNativeSlot(requirement, entry.get(),
                        input ? nativeInputs : outputSlots, input ? claimedInputs : claimedOutputs)) {
                    return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
                }
                continue;
            }
            var totals = input ? inputs : outputs;
            long before = totals.getOrDefault(exact.what(), 0L);
            if (exact.amount() > limit - before) return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
            totals.put(exact.what(), before + exact.amount());
        }
        if (inputs.isEmpty() && outputs.isEmpty()) return new Transfer(nativeInputs, nativeOutputs, null);
        List<List<GenericStack>> actualInputs = new ArrayList<>();
        for (int i = 0; i < nativeInputs.size(); i++) {
            var slot = nativeInputs.get(i);
            var remaining = claimedInputs.get(i) ? slot : slot.stream().filter(stack -> !inputs.containsKey(stack.what())).toList();
            if (!remaining.isEmpty()) actualInputs.add(remaining);
        }
        inputs.forEach((key, amount) -> actualInputs.add(List.of(new GenericStack(key, amount))));
        List<GenericStack> actualOutputs = new ArrayList<>();
        for (int i = 0; i < nativeOutputs.size(); i++) {
            var stack = nativeOutputs.get(i);
            if (claimedOutputs.get(i) || !outputs.containsKey(stack.what())) actualOutputs.add(stack);
        }
        outputs.forEach((key, amount) -> actualOutputs.add(new GenericStack(key, amount)));
        return new Transfer(List.copyOf(actualInputs), List.copyOf(actualOutputs), null);
    }

    private static boolean claimNativeSlot(MachineRequirement requirement, JeiDisplayEntry entry,
                                           List<List<GenericStack>> slots, BitSet claimed) {
        var actual = JeiIngredientAdapterRegistry.get(entry.typeId()).orElseThrow().actualIngredients(entry);
        Predicate<AEKey> matches;
        if (!actual.isEmpty()) {
            var keys = actual.stream().map(value -> nativeStack(entry.ingredientType(), value))
                    .filter(Objects::nonNull).filter(stack -> stack.amount() == entry.count()).map(GenericStack::what).toList();
            matches = keys::contains;
        } else if (requirement.io().isInput()) {
            matches = key -> AE2ResourceFamilies.forKey(key).map(family -> matchesInput(family.adapter(), requirement, key)).orElse(false);
        } else {
            var output = OutputRegistry.fromRequirement(requirement);
            if (output == null) return false;
            var expected = AE2ResourceFamilies.patternKey(output).orElse(null);
            matches = key -> key.equals(expected);
        }
        for (int i = claimed.nextClearBit(0); i < slots.size(); i = claimed.nextClearBit(i + 1)) {
            var slot = slots.get(i);
            if (!slot.isEmpty() && slot.stream().allMatch(stack -> stack.amount() == entry.count() && matches.test(stack.what()))) {
                claimed.set(i);
                return true;
            }
        }
        return false;
    }

    private static <T> @Nullable GenericStack nativeStack(IIngredientType<T> type, Object value) {
        return type.castIngredient(value).map(ingredient -> GenericEntryStackHelper.ingredientToStack(type, ingredient)).orElse(null);
    }

    private static <R> boolean matchesInput(AE2KeyAdapter<R> adapter, MachineRequirement requirement, AEKey key) {
        var predicate = adapter.patternInput(requirement);
        return predicate != null && adapter.toResource(key).filter(predicate).isPresent();
    }

    public record Transfer(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable Identifier unsupported) {
        public boolean fits(int inputSlots, int outputSlots) {
            return unsupported == null && inputs.size() <= inputSlots && outputs.size() <= outputSlots;
        }
    }
}

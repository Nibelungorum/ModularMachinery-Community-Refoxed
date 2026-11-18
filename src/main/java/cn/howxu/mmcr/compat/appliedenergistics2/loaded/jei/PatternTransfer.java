package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Restores provider-declared amounts when a JEI ingredient carries only a key.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternTransfer {
    private PatternTransfer() {}

    public static Transfer prepare(MachineRecipeDisplay display, List<List<GenericStack>> nativeInputs,
                                   List<GenericStack> nativeOutputs) {
        Map<AEKey, Long> inputs = new LinkedHashMap<>(), outputs = new LinkedHashMap<>();
        Map<Long, Long> inputSlots = new HashMap<>(), outputSlots = new HashMap<>();
        for (var slot : nativeInputs) {
            if (!slot.isEmpty() && slot.stream().allMatch(stack -> stack.amount() == slot.getFirst().amount()))
                inputSlots.merge(slot.getFirst().amount(), 1L, Long::sum);
        }
        for (var slot : nativeOutputs) outputSlots.merge(slot.amount(), 1L, Long::sum);
        for (MachineRequirement requirement : display.recipe().runtimeRequirements()) {
            GenericStack exact = null;
            for (var family : AE2ResourceFamilies.all()) {
                var stack = family.adapter().exactJeiStack(requirement);
                if (stack == null) continue;
                if (exact != null || stack.amount() <= 0L || AE2ResourceFamilies.forKey(stack.what()).orElse(null) != family) {
                    return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
                }
                exact = stack;
            }
            if (exact == null) {
                // Conditions are not pattern slots. Other resources need a real native slot, not a display sample.
                var entry = MachineRecipeDisplay.entry(requirement);
                if (!requirement.paysAtStart() && (requirement.io().isInput() || requirement.perTickOutput() || entry.isEmpty())) continue;
                if (entry.isEmpty() || !entry.get().hasNativePatternSlot()) return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
                var slots = requirement.io().isInput() ? inputSlots : outputSlots;
                if (slots.merge(entry.get().count(), -1L, Long::sum) < 0L)
                    return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
                continue;
            }
            var totals = requirement.io().isInput() ? inputs : outputs;
            long before = totals.getOrDefault(exact.what(), 0L);
            long limit = AE2ResourceFamilies.forKey(exact.what()).orElseThrow().adapter().maxPatternTotal();
            if (exact.amount() > limit - before) return new Transfer(nativeInputs, nativeOutputs, requirement.type().id());
            totals.put(exact.what(), before + exact.amount());
        }
        if (inputs.isEmpty() && outputs.isEmpty()) return new Transfer(nativeInputs, nativeOutputs, null);
        List<List<GenericStack>> actualInputs = new ArrayList<>();
        for (var alternatives : nativeInputs) {
            var remaining = alternatives.stream().filter(stack -> !inputs.containsKey(stack.what())).toList();
            if (!remaining.isEmpty()) actualInputs.add(remaining);
        }
        inputs.forEach((key, amount) -> actualInputs.add(List.of(new GenericStack(key, amount))));
        List<GenericStack> actualOutputs = new ArrayList<>(nativeOutputs.stream()
                .filter(stack -> !outputs.containsKey(stack.what())).toList());
        outputs.forEach((key, amount) -> actualOutputs.add(new GenericStack(key, amount)));
        return new Transfer(List.copyOf(actualInputs), List.copyOf(actualOutputs), null);
    }

    public record Transfer(List<List<GenericStack>> inputs, List<GenericStack> outputs, Identifier unsupported) {
        public boolean fits(int inputSlots, int outputSlots) {
            return unsupported == null && inputs.size() <= inputSlots && outputs.size() <= outputSlots;
        }
    }
}

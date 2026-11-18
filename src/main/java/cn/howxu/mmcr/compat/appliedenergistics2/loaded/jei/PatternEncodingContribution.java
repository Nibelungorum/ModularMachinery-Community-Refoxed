package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jei;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/** Corrects key-only JEI samples before both terminal slot admission and encoding.
 * Providers own the entire nominal amount for each contributed key and direction.
 * @author howxu <dev@howxu.cn>
 */
public interface PatternEncodingContribution {
    record Slot(boolean output, GenericStack exact) {
        public Slot { if (exact == null || exact.amount() <= 0L) throw new IllegalArgumentException("Invalid pattern slot"); }
    }
    List<Slot> slots(MachineRecipeDisplay display);

    record Transfer(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        public boolean fits(int inputSlots, int outputSlots) { return inputs.size() <= inputSlots && outputs.size() <= outputSlots; }
    }

    static List<Slot> collect(MachineRecipeDisplay display) {
        return Registry.PROVIDERS.stream().flatMap(provider -> provider.slots(display).stream()).toList();
    }

    static Transfer prepare(List<Slot> slots, List<List<GenericStack>> nativeInputs, List<GenericStack> nativeOutputs) {
        if (slots.isEmpty()) return new Transfer(nativeInputs, nativeOutputs);
        Map<AEKey, Long> exactInputs = new LinkedHashMap<>(), exactOutputs = new LinkedHashMap<>();
        for (var slot : slots) (slot.output() ? exactOutputs : exactInputs)
                .merge(slot.exact().what(), slot.exact().amount(), Math::addExact);
        List<List<GenericStack>> inputs = new ArrayList<>();
        for (var alternatives : nativeInputs) {
            var kept = alternatives.stream().filter(stack -> !exactInputs.containsKey(stack.what())).toList();
            if (!kept.isEmpty()) inputs.add(kept);
        }
        exactInputs.forEach((key, amount) -> inputs.add(List.of(new GenericStack(key, amount))));
        List<GenericStack> outputs = new ArrayList<>(nativeOutputs.stream().filter(stack -> !exactOutputs.containsKey(stack.what())).toList());
        exactOutputs.forEach((key, amount) -> outputs.add(new GenericStack(key, amount)));
        return new Transfer(List.copyOf(inputs), List.copyOf(outputs));
    }

    final class Registry {
        private Registry() {}
        private static final List<PatternEncodingContribution> PROVIDERS = ServiceLoader.load(PatternEncodingContribution.class,
                PatternEncodingContribution.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();
    }
}

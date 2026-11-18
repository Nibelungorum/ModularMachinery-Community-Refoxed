package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reuses source-only requirement views for current native candidates, never dynamic wakeup predicates.
 *
 * @author howxu <dev@howxu.cn>
 */
final class FailureRequirementCache {
    private final Map<MachineRecipe, Entry> requirements = new IdentityHashMap<>();
    private long catalogVersion = Long.MIN_VALUE;

    void prepare(List<MachineRecipe> candidates, long version) {
        if (catalogVersion != version) {
            requirements.clear();
            catalogVersion = version;
        }
        Set<MachineRecipe> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.addAll(candidates);
        requirements.keySet().removeIf(recipe -> !retained.contains(recipe));
    }

    List<MachineRequirement> resolve(MachineRecipe recipe) {
        Entry cached = requirements.get(recipe);
        if (cached != null && cached.proofs.stream().allMatch(Proof::current)) return cached.requirements;
        requirements.remove(recipe);
        var derived = recipe.runtimeRequirements();
        var proofs = new ArrayList<Proof>();
        for (var requirement : Stream.concat(recipe.requirements().stream(), derived.stream()).toList()) {
            if (RequirementHandlerRegistry.handlerFor(requirement.type()) != requirement.type().handler()) return derived;
            Object version = requirement.derivationVersion();
            if (version == null) return derived;
            proofs.add(() -> RequirementHandlerRegistry.handlerFor(requirement.type()) == requirement.type().handler()
                    && version == requirement.derivationVersion());
        }
        for (var output : recipe.machineOutputs()) {
            Object version = output.conversionVersion();
            if (version == null || !OutputRegistry.isCanonical(output)) return derived;
            proofs.add(() -> OutputRegistry.isCanonical(output) && version == output.conversionVersion());
        }
        requirements.put(recipe, new Entry(derived, List.copyOf(proofs)));
        return derived;
    }

    private interface Proof { boolean current(); }
    private record Entry(List<MachineRequirement> requirements, List<Proof> proofs) {}

    void clear() {
        requirements.clear();
        catalogVersion = Long.MIN_VALUE;
    }
}

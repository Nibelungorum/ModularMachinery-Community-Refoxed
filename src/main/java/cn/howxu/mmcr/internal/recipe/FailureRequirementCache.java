package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses source-only requirement views while every handler and converter still vouches for them.
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
        if (cached != null && cached.evidence().current()) return cached.requirements();
        requirements.remove(recipe);

        var evidence = new DerivationEvidence(recipe);
        var derived = recipe.deriveRequirements(List.of(), 1D, 1D, evidence::record);
        if (evidence.current()) requirements.put(recipe, new Entry(derived, evidence));
        return derived;
    }

    private record Entry(List<MachineRequirement> requirements, DerivationEvidence evidence) {}

    void clear() {
        requirements.clear();
        catalogVersion = Long.MIN_VALUE;
    }
}

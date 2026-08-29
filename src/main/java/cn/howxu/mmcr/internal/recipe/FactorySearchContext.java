package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;

import java.util.List;

/**
 * Immutable inputs shared by factory recipe searches during one runtime pass.
 *
 * @author howxu <dev@howxu.cn>
 */
public record FactorySearchContext(
        ControllerRuntimeSnapshot snapshot,
        List<MachineRecipe> orderedCandidates,
        List<MachineCapability> capabilities,
        List<RecipeModifier> modifiers,
        long catalogVersion,
        long resourceAvailabilityEpoch,
        long maxParallelism,
        long gameTime) {
    public FactorySearchContext {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        orderedCandidates = List.copyOf(orderedCandidates == null ? List.of() : orderedCandidates);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        maxParallelism = Math.max(1L, maxParallelism);
    }
}

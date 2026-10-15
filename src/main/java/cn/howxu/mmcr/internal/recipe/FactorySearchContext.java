package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
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
        CapabilitySnapshot capabilitySnapshot,
        List<MachineModifier> modifiers,
        long catalogVersion,
        long resourceAvailabilityEpoch,
        long maxParallelism,
        long gameTime) {
    public FactorySearchContext {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        orderedCandidates = List.copyOf(orderedCandidates == null ? List.of() : orderedCandidates);
        if (capabilitySnapshot == null) throw new IllegalArgumentException("capabilitySnapshot must not be null");
        modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
        maxParallelism = Math.max(1L, maxParallelism);
    }

    public FactorySearchContext(ControllerRuntimeSnapshot snapshot, List<MachineRecipe> orderedCandidates,
                                List<MachineCapability> capabilities, List<MachineModifier> modifiers,
                                long catalogVersion, long resourceAvailabilityEpoch, long maxParallelism, long gameTime) {
        this(snapshot, orderedCandidates, new CapabilitySnapshot(capabilities == null ? List.of() : capabilities),
                modifiers, catalogVersion, resourceAvailabilityEpoch, maxParallelism, gameTime);
    }

    public List<MachineCapability> capabilities() { return capabilitySnapshot.capabilities(); }
}

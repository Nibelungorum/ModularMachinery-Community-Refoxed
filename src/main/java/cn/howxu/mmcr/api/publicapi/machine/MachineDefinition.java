package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable machine declaration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineDefinition(
        Identifier id,
        String displayNameKey,
        PatternDefinition pattern,
        ControllerSpec controller,
        AppearanceSpec appearance,
        PortRequirements portRequirements,
        PortTiers portTiers,
        List<StructureStage> structureStages,
        StructureRequirements requirements,
        FactorySpec factory,
        MachineRole role,
        Set<Identifier> acceptedModuleIds,
        int maxParallelism,
        boolean parallelizable,
        RecipeFailureActions failureAction) {

    public MachineDefinition {
        if (id == null) throw new IllegalArgumentException("id null");
        if (pattern == null) throw new IllegalArgumentException("pattern null");
        if (displayNameKey != null && displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey blank");
        }
        displayNameKey = MachineRegistration.defaultDisplayNameKey(id, displayNameKey);
        controller = controller == null ? ControllerSpec.builder().build() : controller;
        appearance = appearance == null ? AppearanceSpec.builder().build() : appearance;
        portRequirements = portRequirements == null ? PortRequirements.none() : portRequirements;
        portTiers = portTiers == null ? PortTiers.none() : portTiers;
        requirements = requirements == null ? StructureRequirements.EMPTY : requirements;
        structureStages = List.copyOf(structureStages == null || structureStages.isEmpty()
                ? List.of(new StructureStage(StructureStage.Kind.FULL, pattern, portRequirements, portTiers, requirements))
                : structureStages);
        factory = factory == null ? FactorySpec.builder().build() : factory;
        role = role == null ? MachineRole.NORMAL : role;
        acceptedModuleIds = copyAcceptedModuleIds(acceptedModuleIds);
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism must be positive");
        failureAction = failureAction == null ? RecipeFailureActions.getDefaultAction() : failureAction;
        if (role != MachineRole.HOST && !acceptedModuleIds.isEmpty()) {
            throw new IllegalStateException("Only HOST machines may accept modules");
        }
        if (role == MachineRole.HOST && acceptedModuleIds.isEmpty()) {
            throw new IllegalStateException("HOST machine must accept at least 1 module");
        }
    }

    private static Set<Identifier> copyAcceptedModuleIds(Set<Identifier> acceptedModuleIds) {
        if (acceptedModuleIds == null || acceptedModuleIds.isEmpty()) return Set.of();
        LinkedHashSet<Identifier> copy = new LinkedHashSet<>();
        for (Identifier id : acceptedModuleIds) {
            if (id == null) throw new IllegalArgumentException("accepted module id null");
            copy.add(id);
        }
        return Collections.unmodifiableSet(copy);
    }
}

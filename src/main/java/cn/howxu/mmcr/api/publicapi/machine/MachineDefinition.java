package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineRegistration;
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
        ControllerSpec controller,
        AppearanceSpec appearance,
        FactorySpec factory,
        MachineRole role,
        Set<Identifier> acceptedModuleIds,
        int maxParallelism,
        boolean parallelizable,
        RecipeFailureActions failureAction,
        boolean allowModifiers,
        boolean allowMultithreading,
        int maxParallelAmount,
        boolean expandableStructure,
        java.util.Map<String, SmartInterfaceType> smartInterfaceTypes,
        boolean shareSmartInterfaces,
        List<SmartInterfaceModifier> smartInterfaceModifiers,
        Identifier runningSoundId,
        Identifier finishSoundId,
        BlockArray pattern,
        MachineBehavior behavior) {

    public MachineDefinition(Identifier id, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role,
            Set<Identifier> acceptedModuleIds, int maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction) {
        this(id, displayNameKey, controller, appearance, factory, role, acceptedModuleIds,
                maxParallelism, parallelizable, failureAction, false, false, 1, false,
                java.util.Map.of(), false, List.of(), null, null, new BlockArray(java.util.Map.of()),
                RecipeBehavior.defaults());
    }

    public MachineDefinition(Identifier id, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role,
            Set<Identifier> acceptedModuleIds, int maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction, boolean allowModifiers, boolean allowMultithreading,
            int maxParallelAmount, boolean expandableStructure,
            java.util.Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            Identifier runningSoundId, Identifier finishSoundId, BlockArray pattern) {
        this(id, displayNameKey, controller, appearance, factory, role, acceptedModuleIds,
                maxParallelism, parallelizable, failureAction, allowModifiers, allowMultithreading,
                maxParallelAmount, expandableStructure, smartInterfaceTypes, shareSmartInterfaces,
                smartInterfaceModifiers, runningSoundId, finishSoundId, pattern, RecipeBehavior.defaults());
    }

    public MachineDefinition {
        if (id == null) throw new IllegalArgumentException("id null");
        if (displayNameKey != null && displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey blank");
        }
        displayNameKey = MachineRegistration.defaultDisplayNameKey(id, displayNameKey);
        controller = controller == null ? ControllerSpec.builder().build() : controller;
        appearance = appearance == null ? AppearanceSpec.builder().build() : appearance;
        factory = factory == null ? FactorySpec.builder().build() : factory;
        role = role == null ? MachineRole.NORMAL : role;
        acceptedModuleIds = copyAcceptedModuleIds(acceptedModuleIds);
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism must be positive");
        if (maxParallelAmount < 1) throw new IllegalArgumentException("maxParallelAmount must be positive");
        smartInterfaceTypes = java.util.Map.copyOf(smartInterfaceTypes == null ? java.util.Map.of() : smartInterfaceTypes);
        smartInterfaceModifiers = List.copyOf(smartInterfaceModifiers == null ? List.of() : smartInterfaceModifiers);
        failureAction = failureAction == null ? RecipeFailureActions.getDefaultAction() : failureAction;
        behavior = java.util.Objects.requireNonNull(behavior, "behavior");
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

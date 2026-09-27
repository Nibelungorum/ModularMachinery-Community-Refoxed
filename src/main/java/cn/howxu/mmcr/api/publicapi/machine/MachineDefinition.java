package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.NetworkInterfaceSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.network.RequestProcess;
import cn.howxu.mmcr.api.publicapi.network.RequestFailed;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
        List<Identifier> recipePoolIds,
        String displayNameKey,
        ControllerSpec controller,
        AppearanceSpec appearance,
        FactorySpec factory,
        MachineRole role,
        Set<Identifier> acceptedModuleIds,
        NetworkInterfaceSpec networkInterface,
        long maxParallelism,
        boolean parallelizable,
        RecipeFailureActions failureAction,
        boolean allowModifiers,
        boolean allowMultithreading,
        int maxParallelAmount,
        boolean expandableStructure,
        Map<String, SmartInterfaceType> smartInterfaceTypes,
        boolean shareSmartInterfaces,
        List<SmartInterfaceModifier> smartInterfaceModifiers,
        Identifier runningSoundId,
        Identifier finishSoundId,
        BlockArray pattern,
        MachineBehavior behavior,
        Map<Identifier, RequestProcess> requestProcessors,
        Map<Identifier, RequestFailed> requestFailures) {

    public MachineDefinition(Identifier id, Identifier recipePoolId, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role, Set<Identifier> acceptedModuleIds,
            NetworkInterfaceSpec networkInterface, long maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction, boolean allowModifiers, boolean allowMultithreading,
            int maxParallelAmount, boolean expandableStructure,
            Map<String, SmartInterfaceType> smartInterfaceTypes, boolean shareSmartInterfaces,
            List<SmartInterfaceModifier> smartInterfaceModifiers, Identifier runningSoundId, Identifier finishSoundId,
            BlockArray pattern, MachineBehavior behavior) {
        this(id, singletonPool(recipePoolId), displayNameKey, controller, appearance, factory, role, acceptedModuleIds, networkInterface,
                maxParallelism, parallelizable, failureAction, allowModifiers, allowMultithreading, maxParallelAmount,
                expandableStructure, smartInterfaceTypes, shareSmartInterfaces, smartInterfaceModifiers, runningSoundId,
                finishSoundId, pattern, behavior, Map.of(), Map.of());
    }

    public MachineDefinition(Identifier id, Identifier recipePoolId, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role,
            Set<Identifier> acceptedModuleIds, long maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction) {
        this(id, recipePoolId, displayNameKey, controller, appearance, factory, role, acceptedModuleIds,
                maxParallelism, parallelizable, failureAction, false, false, 1, false,
                Map.of(), false, List.of(), null, null, new BlockArray(Map.of()),
                RecipeBehavior.defaults());
    }

    public MachineDefinition(Identifier id, Identifier recipePoolId, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role,
            Set<Identifier> acceptedModuleIds, long maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction, boolean allowModifiers, boolean allowMultithreading,
            int maxParallelAmount, boolean expandableStructure,
            Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            Identifier runningSoundId, Identifier finishSoundId, BlockArray pattern,
            MachineBehavior behavior) {
        this(id, recipePoolId, displayNameKey, controller, appearance, factory, role, acceptedModuleIds,
                NetworkInterfaceSpec.disabled(), maxParallelism, parallelizable, failureAction,
                allowModifiers, allowMultithreading, maxParallelAmount, expandableStructure,
                smartInterfaceTypes, shareSmartInterfaces, smartInterfaceModifiers, runningSoundId,
                finishSoundId, pattern, behavior);
    }

    public MachineDefinition(Identifier id, Identifier recipePoolId, String displayNameKey, ControllerSpec controller,
            AppearanceSpec appearance, FactorySpec factory, MachineRole role,
            Set<Identifier> acceptedModuleIds, long maxParallelism, boolean parallelizable,
            RecipeFailureActions failureAction, boolean allowModifiers, boolean allowMultithreading,
            int maxParallelAmount, boolean expandableStructure,
            Map<String, SmartInterfaceType> smartInterfaceTypes,
            boolean shareSmartInterfaces, List<SmartInterfaceModifier> smartInterfaceModifiers,
            Identifier runningSoundId, Identifier finishSoundId, BlockArray pattern) {
        this(id, recipePoolId, displayNameKey, controller, appearance, factory, role, acceptedModuleIds,
                maxParallelism, parallelizable, failureAction, allowModifiers, allowMultithreading,
                maxParallelAmount, expandableStructure, smartInterfaceTypes, shareSmartInterfaces,
                smartInterfaceModifiers, runningSoundId, finishSoundId, pattern, RecipeBehavior.defaults());
    }

    public MachineDefinition {
        if (id == null) throw new IllegalArgumentException("id null");
        recipePoolIds = MachineRegistration.copyRecipePoolIds(id, recipePoolIds);
        if (displayNameKey != null && displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey blank");
        }
        displayNameKey = MachineRegistration.defaultDisplayNameKey(id, displayNameKey);
        controller = controller == null ? ControllerSpec.builder().build() : controller;
        appearance = appearance == null ? AppearanceSpec.builder().build() : appearance;
        factory = factory == null ? FactorySpec.builder().build() : factory;
        role = role == null ? MachineRole.NORMAL : role;
        acceptedModuleIds = copyAcceptedModuleIds(acceptedModuleIds);
        networkInterface = networkInterface == null ? NetworkInterfaceSpec.disabled() : networkInterface;
        if (maxParallelism < 1L) throw new IllegalArgumentException("maxParallelism must be positive");
        if (maxParallelAmount < 1) throw new IllegalArgumentException("maxParallelAmount must be positive");
        smartInterfaceTypes = Map.copyOf(smartInterfaceTypes == null ? Map.of() : smartInterfaceTypes);
        smartInterfaceModifiers = List.copyOf(smartInterfaceModifiers == null ? List.of() : smartInterfaceModifiers);
        failureAction = failureAction == null ? RecipeFailureActions.getDefaultAction() : failureAction;
        behavior = Objects.requireNonNull(behavior, "behavior");
        requestProcessors = Collections.unmodifiableMap(new LinkedHashMap<>(
                requestProcessors == null ? Map.of() : requestProcessors));
        requestFailures = Collections.unmodifiableMap(new LinkedHashMap<>(
                requestFailures == null ? Map.of() : requestFailures));
        if (role != MachineRole.HOST && !acceptedModuleIds.isEmpty()) {
            throw new IllegalStateException("Only HOST machines may accept modules");
        }
        if (role == MachineRole.HOST && acceptedModuleIds.isEmpty()) {
            throw new IllegalStateException("HOST machine must accept at least 1 module");
        }
    }

    public Identifier recipePoolId() {
        return recipePoolIds.getFirst();
    }

    private static List<Identifier> singletonPool(Identifier recipePoolId) {
        return recipePoolId == null ? null : List.of(recipePoolId);
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

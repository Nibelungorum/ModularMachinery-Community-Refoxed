package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Machine {
    Identifier registryName();

    default String displayNameKey() {
        return MachineRegistration.defaultDisplayNameKey(registryName());
    }

    default Component displayName() {
        return Component.translatable(displayNameKey());
    }

    @Deprecated(forRemoval = true)
    default String localizedName() {
        return displayNameKey();
    }

    BlockArray pattern();

    MachineControllerSpec controller();

    default MachineAppearanceSpec appearance() {
        return MachineAppearanceSpec.defaults();
    }

    default PortRequirementSpec portRequirements() {
        return PortRequirementSpec.none();
    }

    default PortTierRequirementSpec portTierRequirements() {
        return PortTierRequirementSpec.none();
    }

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }

    default List<DynamicPatternSpec> dynamicPatterns() {
        return List.of();
    }

    default List<MachineStructureStage> structureStages() {
        return List.of(new MachineStructureStage(1, pattern(), portRequirements(), portTierRequirements(),
                dynamicPatterns(), MachineStructureRequirements.EMPTY));
    }

    default long maxParallelism() {
        return 1L;
    }

    default boolean parallelizable() {
        return false;
    }

    default boolean hasFactory() {
        return false;
    }

    default int factoryThreadLimit() {
        return 1;
    }

    default List<FactoryThreadSpec> factoryThreads() {
        return List.of();
    }

    default MachineRole role() {
        return MachineRole.NORMAL;
    }

    default Set<Identifier> acceptedModuleIds() {
        return Set.of();
    }

    default NetworkInterfaceSpec networkInterface() {
        return NetworkInterfaceSpec.disabled();
    }

    default boolean isHost() {
        return role() == MachineRole.HOST;
    }

    default boolean isModule() {
        return role() == MachineRole.MODULE;
    }

    default MachineBehavior behavior() {
        return RecipeBehavior.defaults();
    }
}

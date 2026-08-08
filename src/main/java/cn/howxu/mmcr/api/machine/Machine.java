package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.List;

public interface Machine {
    Identifier registryName();

    String localizedName();

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

    default int maxParallelism() {
        return 1;
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
}

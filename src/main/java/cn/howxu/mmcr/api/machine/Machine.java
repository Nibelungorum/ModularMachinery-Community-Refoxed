package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.List;

public sealed interface Machine permits DynamicMachine {
    Identifier registryName();

    String localizedName();

    BlockArray pattern();

    MachineControllerSpec controller();

    default PortRequirementSpec portRequirements() {
        return PortRequirementSpec.none();
    }

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }

    default List<DynamicPatternSpec> dynamicPatterns() {
        return List.of();
    }
}

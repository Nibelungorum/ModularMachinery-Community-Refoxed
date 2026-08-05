package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

public sealed interface Machine permits DynamicMachine {
    Identifier registryName();

    String localizedName();

    BlockArray pattern();

    MachineControllerSpec controller();

    default RecipeFailureActions failureAction() {
        return RecipeFailureActions.getDefaultAction();
    }
}

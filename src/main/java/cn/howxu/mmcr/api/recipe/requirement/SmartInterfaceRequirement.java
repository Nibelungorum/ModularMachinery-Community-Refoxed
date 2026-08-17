package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

/**
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceRequirement(RecipeModifier.IOType io, String interfaceType, float minValue, float maxValue)
        implements MachineRequirement {

    public SmartInterfaceRequirement {
        if (io == null) throw new IllegalArgumentException("io null");
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue) || minValue > maxValue) {
            throw new IllegalArgumentException("invalid smart interface value range");
        }
    }

    public static SmartInterfaceRequirement input(String type, float value) {
        return input(type, value, value);
    }

    public static SmartInterfaceRequirement input(String type, float minValue, float maxValue) {
        return new SmartInterfaceRequirement(RecipeModifier.IOType.INPUT, type, minValue, maxValue);
    }

    public static SmartInterfaceRequirement output(String type, float value) {
        return new SmartInterfaceRequirement(RecipeModifier.IOType.OUTPUT, type, value, value);
    }

    @Override
    public String type() {
        return "smart_interface";
    }

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        if (io == RecipeModifier.IOType.INPUT) {
            boolean matches = context.smartInterfaceValue(interfaceType)
                    .filter(value -> value >= minValue && value <= maxValue)
                    .isPresent();
            if (!matches) context.setRequirementFailure(context.smartInterfaceFailureMessage(interfaceType), null);
            return matches;
        }
        return context.hasSmartInterface(interfaceType);
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT || context.setSmartInterfaceValue(interfaceType, minValue);
    }
}

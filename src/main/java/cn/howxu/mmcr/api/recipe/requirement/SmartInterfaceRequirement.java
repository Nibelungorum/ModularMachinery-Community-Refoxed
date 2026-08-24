package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

/**
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceRequirement(RecipeModifier.IOType io, String interfaceType, float minValue, float maxValue)
        implements MachineRequirement {
    public static final RequirementType<SmartInterfaceRequirement> TYPE = new RequirementType<>(MMCR.id("smart_interface"));

    public SmartInterfaceRequirement {
        if (io == null) throw new IllegalArgumentException("io null");
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue) || minValue > maxValue) {
            throw new IllegalArgumentException("invalid smart interface value range");
        }
    }

    @Override
    public RequirementType<SmartInterfaceRequirement> type() {
        return TYPE;
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

}

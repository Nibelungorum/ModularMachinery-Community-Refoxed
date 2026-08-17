package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

/**
 * Declarative linear mapping from a smart-interface value to a recipe modifier.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceModifier(String interfaceType, String target, RecipeModifier.IOType io,
        boolean affectsChance, float minValue, float maxValue, float atMin, float atMax,
        RecipeModifier.Operation operation) {
    public SmartInterfaceModifier {
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target blank");
        io = io == null ? RecipeModifier.IOType.INPUT : io;
        operation = operation == null ? RecipeModifier.Operation.MULTIPLY : operation;
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue)
                || !Float.isFinite(atMin) || !Float.isFinite(atMax)) {
            throw new IllegalArgumentException("smart interface modifier values must be finite");
        }
    }

    public static SmartInterfaceModifier duration(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_DURATION, RecipeModifier.IOType.INPUT,
                false, min, max, atMin, atMax, operation);
    }

    public static SmartInterfaceModifier energy(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_ENERGY, RecipeModifier.IOType.INPUT,
                false, min, max, atMin, atMax, operation);
    }

    public static SmartInterfaceModifier item(String type, RecipeModifier.IOType io, boolean chance, float min,
            float max, float atMin, float atMax, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_ITEM, io, chance, min, max, atMin, atMax,
                operation);
    }

    public static SmartInterfaceModifier fluid(String type, RecipeModifier.IOType io, boolean chance, float min,
            float max, float atMin, float atMax, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_FLUID, io, chance, min, max, atMin,
                atMax, operation);
    }

    public RecipeModifier toRecipeModifier(float value) {
        return new RecipeModifier(target, io, mappedValue(value), operation, affectsChance);
    }

    public float mappedValue(float value) {
        if (minValue == maxValue) return value <= minValue ? atMin : atMax;
        float t = (value - minValue) / (maxValue - minValue);
        t = Math.clamp(t, 0F, 1F);
        return atMin + (atMax - atMin) * t;
    }
}

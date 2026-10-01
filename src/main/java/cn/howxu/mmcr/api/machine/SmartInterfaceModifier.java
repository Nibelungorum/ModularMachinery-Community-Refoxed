package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

/**
 * Declarative linear mapping from a smart-interface value to a recipe modifier.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceModifier(String interfaceType, String target, String scope,
        boolean affectsChance, float minValue, float maxValue, float atMin, float atMax,
        RecipeModifier.Operation operation) {
    public SmartInterfaceModifier {
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target blank");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope blank");
        operation = operation == null ? RecipeModifier.Operation.MULTIPLY : operation;
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue)
                || !Float.isFinite(atMin) || !Float.isFinite(atMax)) {
            throw new IllegalArgumentException("smart interface modifier values must be finite");
        }
        MachineModifier.numeric(normalizedTarget(target, scope), scope, atMin, operation.name(), affectsChance);
        MachineModifier.numeric(normalizedTarget(target, scope), scope, atMax, operation.name(), affectsChance);
    }

    public SmartInterfaceModifier(String interfaceType, String target, RecipeModifier.IOType io,
            boolean affectsChance, float minValue, float maxValue, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        this(interfaceType, normalizedTarget(target, io == null ? "input" : io.getKey()),
                io == null ? "input" : io.getKey(), affectsChance, minValue, maxValue, atMin, atMax, operation);
    }

    public static SmartInterfaceModifier numeric(String type, String target, String scope, float min, float max,
            float atMin, float atMax, String operation, boolean affectsChance) {
        return new SmartInterfaceModifier(type, normalizedTarget(target, scope), scope, affectsChance,
                min, max, atMin, atMax, RecipeModifier.Operation.valueOf(operation.toUpperCase(java.util.Locale.ROOT)));
    }

    public static SmartInterfaceModifier duration(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_DURATION, "input",
                false, min, max, atMin, atMax, operation);
    }

    public static SmartInterfaceModifier energy(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_ENERGY, "input",
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

    public static SmartInterfaceModifier chemical(String type, RecipeModifier.IOType io, boolean chance, float min,
            float max, float atMin, float atMax, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_CHEMICAL, io, chance, min, max, atMin,
                atMax, operation);
    }

    public static SmartInterfaceModifier heat(String type, RecipeModifier.IOType io, float min, float max,
            float atMin, float atMax, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_HEAT, io, false, min, max, atMin,
                atMax, operation);
    }

    public MachineModifier.Numeric toModifier(float value) {
        return MachineModifier.numeric(target, scope, mappedValue(value), operation.name(), affectsChance);
    }

    public float mappedValue(float value) {
        if (minValue == maxValue) return value <= minValue ? atMin : atMax;
        double t = ((double) value - minValue) / ((double) maxValue - minValue);
        t = Math.clamp(t, 0D, 1D);
        return (float) (atMin + ((double) atMax - atMin) * t);
    }

    public RecipeModifier.IOType io() {
        return "output".equals(scope) ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT;
    }

    private static String normalizedTarget(String target, String scope) {
        if ((IntegrationTypeHelper.TARGET_ITEM.equals(target) || IntegrationTypeHelper.TARGET_FLUID.equals(target)
                || IntegrationTypeHelper.TARGET_CHEMICAL.equals(target) || IntegrationTypeHelper.TARGET_HEAT.equals(target))
                && "output".equalsIgnoreCase(scope)) return "output";
        return target;
    }
}

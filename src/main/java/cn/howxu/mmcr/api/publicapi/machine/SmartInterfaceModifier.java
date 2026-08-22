package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.publicapi.recipe.modifier.RecipeModifier;

/** Public mapping from a smart-interface value to a recipe modifier.
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceModifier(String interfaceType, String target, RecipeModifier.IOType io,
        boolean affectsChance, float minValue, float maxValue, float atMin, float atMax,
        RecipeModifier.Operation operation) {
    public SmartInterfaceModifier {
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target blank");
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue)
                || !Float.isFinite(atMin) || !Float.isFinite(atMax)) {
            throw new IllegalArgumentException("smart interface modifier values must be finite");
        }
        io = io == null ? RecipeModifier.IOType.INPUT : io;
        operation = operation == null ? RecipeModifier.Operation.MULTIPLY : operation;
    }

    public static SmartInterfaceModifier duration(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, "duration", RecipeModifier.IOType.INPUT, false,
                min, max, atMin, atMax, operation);
    }

    public static SmartInterfaceModifier energy(String type, float min, float max, float atMin, float atMax,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, "energy", RecipeModifier.IOType.INPUT, false,
                min, max, atMin, atMax, operation);
    }
}

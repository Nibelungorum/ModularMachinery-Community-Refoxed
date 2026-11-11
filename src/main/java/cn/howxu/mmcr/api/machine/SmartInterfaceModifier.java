package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;

import java.util.Locale;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

/**
 * Maps a smart-interface value to a numeric modifier using a pure function.
 * The function is evaluated when modifiers are refreshed, not necessarily every tick.
 *
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceModifier(String interfaceType, String target, String scope,
        boolean affectsChance, DoubleUnaryOperator mapping, RecipeModifier.Operation operation) {
    public SmartInterfaceModifier {
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target blank");
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope blank");
        operation = operation == null ? RecipeModifier.Operation.MULTIPLY : operation;
        Objects.requireNonNull(mapping, "mapping");
        MachineModifier.numeric(normalizedTarget(target, scope), scope, 0D, operation.name(), affectsChance);
    }

    public SmartInterfaceModifier(String interfaceType, String target, RecipeModifier.IOType io,
            boolean affectsChance, DoubleUnaryOperator mapping, RecipeModifier.Operation operation) {
        this(interfaceType, normalizedTarget(target, io == null ? "input" : io.getKey()),
                io == null ? "input" : io.getKey(), affectsChance, mapping, operation);
    }

    public static SmartInterfaceModifier numeric(String type, String target, String scope, DoubleUnaryOperator mapping,
            String operation, boolean affectsChance) {
        return new SmartInterfaceModifier(type, normalizedTarget(target, scope), scope, affectsChance,
                mapping, RecipeModifier.Operation.valueOf(operation.toUpperCase(Locale.ROOT)));
    }

    public static SmartInterfaceModifier duration(String type, DoubleUnaryOperator mapping,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_DURATION, "input", false, mapping, operation);
    }

    public static SmartInterfaceModifier energy(String type, DoubleUnaryOperator mapping,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_ENERGY, "input", false, mapping, operation);
    }

    public static SmartInterfaceModifier item(String type, RecipeModifier.IOType io, boolean chance,
            DoubleUnaryOperator mapping, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_ITEM, io, chance, mapping, operation);
    }

    public static SmartInterfaceModifier fluid(String type, RecipeModifier.IOType io, boolean chance,
            DoubleUnaryOperator mapping, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_FLUID, io, chance, mapping, operation);
    }

    public static SmartInterfaceModifier chemical(String type, RecipeModifier.IOType io, boolean chance,
            DoubleUnaryOperator mapping, RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_CHEMICAL, io, chance, mapping, operation);
    }

    public static SmartInterfaceModifier heat(String type, RecipeModifier.IOType io, DoubleUnaryOperator mapping,
            RecipeModifier.Operation operation) {
        return new SmartInterfaceModifier(type, IntegrationTypeHelper.TARGET_HEAT, io, false, mapping, operation);
    }

    public MachineModifier.Numeric toModifier(float value) {
        return MachineModifier.numeric(target, scope, mappedValue(value), operation.name(), affectsChance);
    }

    public double mappedValue(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Smart interface input must be finite: " + value);
        }
        double result = mapping.applyAsDouble(value);
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Smart interface modifier result must be finite: " + result);
        }
        return result;
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

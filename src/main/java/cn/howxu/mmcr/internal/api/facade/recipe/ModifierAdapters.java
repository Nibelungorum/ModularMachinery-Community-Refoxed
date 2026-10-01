package cn.howxu.mmcr.internal.api.facade.recipe;

import cn.howxu.mmcr.api.machine.SmartInterfaceModifier;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.modifier.*;
import java.util.Collection;
import java.util.List;

/** Typed modifier adapters; no duplicated modifier computation. @author howxu <dev@howxu.cn> */
public final class ModifierAdapters {
    private ModifierAdapters() {}
    public static RecipeModifier.IOType io(IoDirection value) { return value == null ? null : switch (value) { case INPUT -> RecipeModifier.IOType.INPUT; case OUTPUT -> RecipeModifier.IOType.OUTPUT; }; }
    public static IoDirection io(RecipeModifier.IOType value) { return value == null ? null : switch (value) { case INPUT -> IoDirection.INPUT; case OUTPUT -> IoDirection.OUTPUT; }; }
    public static RecipeModifier.Operation operation(ModifierOperation value) { return value == null ? null : switch (value) { case ADD -> RecipeModifier.Operation.ADD; case MULTIPLY -> RecipeModifier.Operation.MULTIPLY; case SUBTRACT -> RecipeModifier.Operation.SUBTRACT; case DIVIDE -> RecipeModifier.Operation.DIVIDE; }; }
    public static ModifierOperation operation(RecipeModifier.Operation value) { return switch (value) { case ADD -> ModifierOperation.ADD; case MULTIPLY -> ModifierOperation.MULTIPLY; case SUBTRACT -> ModifierOperation.SUBTRACT; case DIVIDE -> ModifierOperation.DIVIDE; }; }
    public static ModifierSpec wrap(MachineModifier value) { return switch (value) { case MachineModifier.Numeric v -> new Numeric(v); case MachineModifier.Parallelized v -> new Flag(v); }; }
    public static MachineModifier unwrap(ModifierSpec value) {
        if (value instanceof Numeric v) return v.delegate;
        if (value instanceof Flag v) return v.delegate;
        throw new IllegalArgumentException("Modifier must be library-produced");
    }
    public static ModifierBundle wrap(ModifierDefinition value) { return new Bundle(value); }
    public static ModifierDefinition unwrap(ModifierBundle value) { if (value instanceof Bundle v) return v.delegate; throw new IllegalArgumentException("Bundle must be library-produced"); }
    public static SmartModifierSpec wrap(SmartInterfaceModifier value) { return new Smart(value); }
    public static SmartInterfaceModifier unwrap(SmartModifierSpec value) { if (value instanceof Smart v) return v.delegate; throw new IllegalArgumentException("Smart modifier must be library-produced"); }
    public static RecipeAdjustmentSpec wrap(RecipeModifier value) { return new Adjustment(value); }
    public static RecipeModifier unwrap(RecipeAdjustmentSpec value) { if (value instanceof Adjustment v) return v.delegate; throw new IllegalArgumentException("Adjustment must be library-produced"); }
    public static NumericModifierSpec numeric(String target, ModifierScope scope, double value, ModifierOperation op, boolean chance) { return (NumericModifierSpec) wrap(MachineModifier.numeric(target, scopeKey(scope), value, operationKey(op), chance)); }
    public static ParallelizationModifierSpec parallelized(boolean value) { return (ParallelizationModifierSpec) wrap(MachineModifier.parallelized(value)); }
    public static ModifierBundle bundle(List<ModifierSpec> values) { return wrap(new ModifierDefinition(values == null ? null : values.stream().map(ModifierAdapters::unwrap).toList())); }
    public static ModifierBundle combine(ModifierBundle... values) { return wrap(ModifierDefinition.combine(java.util.Arrays.stream(values).map(ModifierAdapters::unwrap).toArray(ModifierDefinition[]::new))); }
    public static RecipeAdjustmentSpec recipe(String target, IoDirection io, float value, ModifierOperation op, boolean chance) { return wrap(new RecipeModifier(target, io(io), value, operation(op), chance)); }
    public static float apply(Collection<RecipeAdjustmentSpec> modifiers, String target, IoDirection io, float value, boolean chance) { return RecipeModifier.applyModifiers(adjustments(modifiers), target, io(io), value, chance); }
    public static double apply(Collection<RecipeAdjustmentSpec> modifiers, String target, IoDirection io, double value, boolean chance) { return RecipeModifier.applyModifiers(adjustments(modifiers), target, io(io), value, chance); }
    public static List<RecipeModifier> adjustments(Collection<RecipeAdjustmentSpec> values) { return values == null ? null : values.stream().map(ModifierAdapters::unwrap).toList(); }
    public static List<RecipeAdjustmentSpec> recipeModifiers(Collection<ModifierSpec> values) { return MachineModifier.recipeModifiers(values == null ? null : values.stream().map(ModifierAdapters::unwrap).toList()).stream().map(ModifierAdapters::wrap).toList(); }
    public static SmartModifierSpec smart(String type, String target, ModifierScope scope, boolean chance, float min, float max, float atMin, float atMax, ModifierOperation op) { return wrap(new SmartInterfaceModifier(type, target, scopeKey(scope), chance, min, max, atMin, atMax, operation(op))); }
    private static String scopeKey(ModifierScope value) {
        return switch (value) { case INPUT -> "input"; case OUTPUT -> "output"; case MACHINE -> "machine"; case RECIPE -> "recipe"; };
    }
    private static ModifierScope scope(String value) {
        return switch (value) {
            case "input" -> ModifierScope.INPUT; case "output" -> ModifierScope.OUTPUT;
            case "machine" -> ModifierScope.MACHINE; case "recipe" -> ModifierScope.RECIPE;
            default -> throw new IllegalArgumentException("Unknown modifier scope: " + value);
        };
    }
    private static String operationKey(ModifierOperation value) {
        return switch (value) { case ADD -> "add"; case MULTIPLY -> "multiply"; case SUBTRACT -> "subtract"; case DIVIDE -> "divide"; };
    }
    public static SmartModifierSpec smart(String type, String target, IoDirection io, boolean chance, float min, float max, float atMin, float atMax, ModifierOperation op) { return wrap(new SmartInterfaceModifier(type, target, io(io), chance, min, max, atMin, atMax, operation(op))); }
    private static ModifierScope scope(MachineModifier.Numeric value) {
        return switch (value.target()) {
            case DURATION, ENERGY, CHEMICAL, HEAT -> ModifierScope.INPUT;
            case OUTPUT -> ModifierScope.OUTPUT;
            case PARALLELISM, FACTORY_THREADS -> ModifierScope.MACHINE;
            case RECIPE_THREADS, PARALLELIZED -> ModifierScope.RECIPE;
        };
    }
    private record Numeric(MachineModifier.Numeric delegate) implements NumericModifierSpec {
        public String target() { return delegate.target().key(); }
        public ModifierScope scope() { return ModifierAdapters.scope(delegate); }
        public double value() { return delegate.value(); }
        public ModifierOperation operation() { return ModifierAdapters.operation(delegate.operation()); }
        public boolean affectsChance() { return delegate.affectsChance(); }
    }
    private record Flag(MachineModifier.Parallelized delegate) implements ParallelizationModifierSpec { public boolean value() { return delegate.value(); } }
    private record Bundle(ModifierDefinition delegate) implements ModifierBundle { public List<ModifierSpec> modifiers() { return delegate.modifiers().stream().map(ModifierAdapters::wrap).toList(); } }
    private record Adjustment(RecipeModifier delegate) implements RecipeAdjustmentSpec {
        public String target() { return delegate.getTarget(); }
        public IoDirection io() { return ModifierAdapters.io(delegate.getIOTarget()); }
        public float value() { return delegate.getModifier(); }
        public ModifierOperation operation() { return ModifierAdapters.operation(delegate.getOperation()); }
        public boolean affectsChance() { return delegate.affectsChance(); }
        public RecipeAdjustmentSpec multiply(float value) { return wrap(delegate.multiply(value)); }
        public RecipeAdjustmentSpec add(float value) { return wrap(delegate.add(value)); }
    }
    private record Smart(SmartInterfaceModifier delegate) implements SmartModifierSpec {
        public String interfaceType() { return delegate.interfaceType(); }
        public String target() { return delegate.target(); }
        public ModifierScope scope() { return ModifierAdapters.scope(delegate.scope()); }
        public boolean affectsChance() { return delegate.affectsChance(); }
        public float minValue() { return delegate.minValue(); }
        public float maxValue() { return delegate.maxValue(); }
        public float atMin() { return delegate.atMin(); }
        public float atMax() { return delegate.atMax(); }
        public ModifierOperation operation() { return ModifierAdapters.operation(delegate.operation()); }
        public IoDirection io() { return ModifierAdapters.io(delegate.io()); }
        public float mappedValue(float value) { return delegate.mappedValue(value); }
        public NumericModifierSpec toModifier(float value) { return (NumericModifierSpec) wrap(delegate.toModifier(value)); }
    }
}

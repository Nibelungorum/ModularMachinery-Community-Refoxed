package cn.howxu.mmcr.api.recipe.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.PrimitiveCodec;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class RecipeModifier {

    public enum IOType {
        INPUT("input"),
        OUTPUT("output");

        private final String key;

        IOType(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public boolean isInput() {
            return this == INPUT;
        }

        public static IOType byKey(String key) {
            for (IOType t : values()) {
                if (t.key.equals(key)) return t;
            }
            return INPUT;
        }
    }

    public enum Operation {
        ADD(0),
        MULTIPLY(1),
        SUBTRACT(2),
        DIVIDE(3),
        ADD_RATIO(4);

        private final int id;

        Operation(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static Operation byId(int id) {
            return switch (id) {
                case 0 -> ADD;
                case 1 -> MULTIPLY;
                case 2 -> SUBTRACT;
                case 3 -> DIVIDE;
                case 4 -> ADD_RATIO;
                default -> throw new IllegalArgumentException("Unknown modifier operation: " + id);
            };
        }
    }

    public static final String IO_INPUT = "input";
    public static final String IO_OUTPUT = "output";

    public static final Codec<IOType> IO_TYPE_CODEC = Codec.STRING
            .flatXmap(s -> {
                IOType t = IOType.byKey(s);
                return t == null
                        ? DataResult.error(() -> "Unknown IO type: " + s)
                        : DataResult.success(t);
            }, t -> DataResult.success(t.getKey()));

    public static final Codec<Operation> OPERATION_CODEC = Codec.INT
            .flatXmap(id -> {
                try {
                    return DataResult.success(Operation.byId(id));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(e::getMessage);
                }
            }, op -> DataResult.success(op.getId()));

    // Preserve old float encodings; relative operands must survive a double-precision round trip.
    private static final PrimitiveCodec<Number> OPERAND = new PrimitiveCodec<>() {
        @Override public <T> DataResult<Number> read(DynamicOps<T> ops, T input) { return ops.getNumberValue(input); }
        @Override public <T> T write(DynamicOps<T> ops, Number value) {
            return value instanceof Float f ? ops.createFloat(f) : ops.createDouble(value.doubleValue());
        }
    };

    public static final Codec<RecipeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(RecipeModifier::getTarget),
            IO_TYPE_CODEC.fieldOf("io").forGetter(RecipeModifier::getIOTarget),
            OPERAND.fieldOf("multiplier").forGetter(RecipeModifier::encodedOperand),
            OPERATION_CODEC.fieldOf("operation").forGetter(RecipeModifier::getOperation),
            Codec.BOOL.optionalFieldOf("affectChance", false).forGetter(RecipeModifier::affectsChance)
    ).apply(instance, (target, io, v, op, chance) -> new RecipeModifier(target, io, v.doubleValue(), op, chance, null)));

    private final String target;
    private final IOType ioTarget;
    private final double modifier;
    private final Preference condition;
    private final Operation operation;
    private final boolean affectsChance;

    public RecipeModifier(String target, IOType ioTarget, float modifier, Operation operation, boolean affectsChance) {
        this(target, ioTarget, operation == Operation.ADD_RATIO ? Double.parseDouble(Float.toString(modifier)) : modifier,
                operation, affectsChance, null);
    }

    public RecipeModifier(String target, IOType ioTarget, double modifier, Operation operation, boolean affectsChance) {
        this(target, ioTarget, modifier, operation, affectsChance, null);
    }

    private RecipeModifier(String target, IOType ioTarget, double modifier, Operation operation, boolean affectsChance,
                           Preference condition) {
        this.condition = condition;
        this.target = target == null ? "" : target;
        this.ioTarget = ioTarget == null ? IOType.INPUT : ioTarget;
        this.modifier = operation == Operation.ADD_RATIO ? modifier : (float) modifier;
        this.operation = operation == null ? Operation.ADD : operation;
        this.affectsChance = affectsChance;
    }

    public String getTarget() {
        return target;
    }

    public IOType getIOTarget() {
        return ioTarget;
    }

    private Number encodedOperand() {
        if (condition != null) throw new IllegalStateException("Cannot serialize a runtime modifier selection");
        if (operation == Operation.ADD_RATIO) return modifier;
        return (float) modifier;
    }

    public float getModifier() {
        return (float) modifier;
    }

    public Operation getOperation() {
        return operation;
    }

    public boolean affectsChance() {
        return affectsChance;
    }

    public RecipeModifier multiply(float value) {
        return new RecipeModifier(target, ioTarget, product(modifier, value), operation, affectsChance, condition);
    }

    public RecipeModifier add(float value) {
        return new RecipeModifier(target, ioTarget, modifier + value, operation, affectsChance, condition);
    }

    /** Compare with the consumer's precision, not the raw machine doubles.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    private record Preference(List<RecipeModifier> block, List<RecipeModifier> item, boolean lower, boolean useBlock) {
        boolean test(float value) {
            RecipeModifier key = block.getFirst();
            float a = applyModifiers(block, key.target, key.ioTarget, value, key.affectsChance);
            float b = applyModifiers(item, key.target, key.ioTarget, value, key.affectsChance);
            return useBlock == (lower ? a <= b : a >= b);
        }

        boolean test(double value) {
            RecipeModifier key = block.getFirst();
            double a = applyModifiers(block, key.target, key.ioTarget, value, key.affectsChance);
            double b = applyModifiers(item, key.target, key.ioTarget, value, key.affectsChance);
            return useBlock == (lower ? a <= b : a >= b);
        }
    }

    /** Both lists describe the same property of two different sources. */
    public static List<RecipeModifier> bestOf(List<RecipeModifier> block, List<RecipeModifier> item, boolean lower) {
        if (block.isEmpty() || item.isEmpty()) return block.isEmpty() ? item : block;
        var first = new Preference(List.copyOf(block), List.copyOf(item), lower, true);
        var second = new Preference(first.block(), first.item(), lower, false);
        List<RecipeModifier> result = new ArrayList<>();
        for (var group : List.of(first, second)) {
            for (RecipeModifier m : group.useBlock() ? group.block() : group.item()) {
                result.add(new RecipeModifier(m.target, m.ioTarget, m.modifier, m.operation, m.affectsChance, group));
            }
        }
        return List.copyOf(result);
    }

    public static float applyModifiers(Collection<RecipeModifier> modifiers, String target, IOType ioType,
                                       float value, boolean isChance) {
        if (modifiers == null || modifiers.isEmpty()) return value;
        float add = 0F, mul = 1F;
        double ratio = 0D;
        Preference previous = null;
        boolean selected = false;
        for (RecipeModifier mod : modifiers) {
            if (!mod.matches(target, ioType, isChance)) continue;
            if (mod.condition != null) {
                if (previous != mod.condition) selected = (previous = mod.condition).test(value);
                if (!selected) continue;
            }
            switch (mod.operation) {
                case ADD -> add += (float) mod.modifier;
                case SUBTRACT -> add -= (float) mod.modifier;
                case ADD_RATIO -> ratio = ModifierMath.add(ratio, mod.modifier);
                case MULTIPLY -> mul = (float) product(mul, mod.modifier);
                case DIVIDE -> {
                    if (mod.modifier != 0F) mul /= (float) mod.modifier;
                }
            }
        }
        return applyFloat(value, add, ratio, mul);
    }

    public static double applyModifiers(Collection<RecipeModifier> modifiers, String target, IOType ioType,
                                       double value, boolean isChance) {
        if (modifiers == null || modifiers.isEmpty()) return value;
        double add = 0D, mul = 1D;
        double ratio = 0D;
        Preference previous = null;
        boolean selected = false;
        for (RecipeModifier mod : modifiers) {
            if (!mod.matches(target, ioType, isChance)) continue;
            if (mod.condition != null) {
                if (previous != mod.condition) selected = (previous = mod.condition).test(value);
                if (!selected) continue;
            }
            switch (mod.operation) {
                case ADD -> add += mod.modifier;
                case SUBTRACT -> add -= mod.modifier;
                case ADD_RATIO -> ratio = ModifierMath.add(ratio, mod.modifier);
                case MULTIPLY -> mul = product(mul, mod.modifier);
                case DIVIDE -> {
                    if (mod.modifier != 0D) mul /= mod.modifier;
                }
            }
        }
        return product(ModifierMath.relative(value, value + add, ratio), mul);
    }

    private static float applyFloat(float value, float add, double ratio, float mul) {
        double adjusted = ratio == 0D ? value + add : ModifierMath.relative(value, (double) value + add, ratio);
        return (float) product(adjusted, mul);
    }

    private static double product(double left, double right) {
        // Zero cancels even an overflowed factor; leave nonzero overflow for the target's own bounds.
        if ((left == 0D && Double.isInfinite(right)) || (right == 0D && Double.isInfinite(left))) return 0D;
        return left * right;
    }

    private boolean matches(String target, IOType ioType, boolean isChance) {
        if (this.target != null && !this.target.isEmpty() && !this.target.equals(target)) return false;
        if (ioType != null && this.ioTarget != ioType) return false;
        return this.affectsChance == isChance;
    }

    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("target", target);
        tag.putString("ioTarget", ioTarget.getKey());
        tag.putInt("operation", operation.getId());
        if (operation == Operation.ADD_RATIO) tag.putDouble("value", encodedOperand().doubleValue());
        else tag.putFloat("value", encodedOperand().floatValue());
        tag.putBoolean("chance", affectsChance);
        return tag;
    }

    public static RecipeModifier deserializeNbt(CompoundTag tag) {
        String target = tag.getStringOr("target", "");
        IOType io = IOType.byKey(tag.getStringOr("ioTarget", IO_INPUT));
        Operation op = Operation.byId(tag.getIntOr("operation", 0));
        double value = op == Operation.ADD_RATIO ? tag.getDoubleOr("value", 0D) : tag.getFloatOr("value", 0F);
        boolean chance = tag.getBooleanOr("chance", false);
        return new RecipeModifier(target, io, value, op, chance, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeModifier that)) return false;
        return Double.compare(that.modifier, modifier) == 0 && Objects.equals(condition, that.condition)
                && affectsChance == that.affectsChance
                && Objects.equals(target, that.target)
                && ioTarget == that.ioTarget
                && operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, ioTarget, modifier, operation, affectsChance, condition);
    }

    public static class ModifierApplier {
        public static final ModifierApplier DEFAULT_APPLIER = new ModifierApplier();

        public float inputAdd = 0;
        public float inputMul = 1;
        public float outputAdd = 0;
        public float outputMul = 1;

        private double inputRatio, outputRatio;
        private List<RecipeModifier> conditional;

        public float apply(float value, IOType ioType) {
            boolean output = ioType == IOType.OUTPUT;
            float add = output ? outputAdd : inputAdd;
            float mul = output ? outputMul : inputMul;
            double ratio = output ? outputRatio : inputRatio;
            if (conditional != null) {
                var selected = new ModifierApplier();
                selected.inputAdd = add;
                selected.inputMul = mul;
                selected.inputRatio = ratio;
                for (RecipeModifier mod : conditional) {
                    if ((mod.ioTarget == IOType.OUTPUT) == output && mod.condition.test(value)) selected.accept(mod, false);
                }
                return selected.apply(value, IOType.INPUT);
            }
            return applyFloat(value, add, ratio, mul);
        }

        public boolean isDefault() {
            return inputAdd == 0 && inputMul == 1 && outputAdd == 0 && outputMul == 1
                    && inputRatio == 0D && outputRatio == 0D && conditional == null;
        }

        private void accept(RecipeModifier mod, boolean output) {
            switch (mod.operation) {
                case ADD -> { if (output) outputAdd += (float) mod.modifier; else inputAdd += (float) mod.modifier; }
                case SUBTRACT -> { if (output) outputAdd -= (float) mod.modifier; else inputAdd -= (float) mod.modifier; }
                case ADD_RATIO -> {
                    if (output) outputRatio = ModifierMath.add(outputRatio, mod.modifier);
                    else inputRatio = ModifierMath.add(inputRatio, mod.modifier);
                }
                case MULTIPLY -> {
                    if (output) outputMul = (float) product(outputMul, mod.modifier);
                    else inputMul = (float) product(inputMul, mod.modifier);
                }
                case DIVIDE -> {
                    if (mod.modifier != 0F) {
                        if (output) outputMul /= (float) mod.modifier; else inputMul /= (float) mod.modifier;
                    }
                }
            }
        }
    }

    public static void applyValueToApplier(ModifierApplier applier, RecipeModifier mod) {
        if (mod.condition == null) applier.accept(mod, mod.ioTarget == IOType.OUTPUT);
        else {
            if (applier.conditional == null) applier.conditional = new ArrayList<>();
            applier.conditional.add(mod);
        }
    }
}

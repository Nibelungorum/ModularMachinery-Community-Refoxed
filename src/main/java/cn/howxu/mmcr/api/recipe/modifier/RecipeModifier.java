package cn.howxu.mmcr.api.recipe.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.Collection;
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
        DIVIDE(3);

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

    public static final Codec<RecipeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("target", "").forGetter(RecipeModifier::getTarget),
            IO_TYPE_CODEC.fieldOf("io").forGetter(RecipeModifier::getIOTarget),
            Codec.FLOAT.fieldOf("multiplier").forGetter(RecipeModifier::getModifier),
            OPERATION_CODEC.fieldOf("operation").forGetter(RecipeModifier::getOperation),
            Codec.BOOL.optionalFieldOf("affectChance", false).forGetter(RecipeModifier::affectsChance)
    ).apply(instance, RecipeModifier::new));

    private final String target;
    private final IOType ioTarget;
    private final float modifier;
    private final Operation operation;
    private final boolean affectsChance;

    public RecipeModifier(String target, IOType ioTarget, float modifier, Operation operation, boolean affectsChance) {
        this.target = target == null ? "" : target;
        this.ioTarget = ioTarget == null ? IOType.INPUT : ioTarget;
        this.modifier = modifier;
        this.operation = operation == null ? Operation.ADD : operation;
        this.affectsChance = affectsChance;
    }

    public String getTarget() {
        return target;
    }

    public IOType getIOTarget() {
        return ioTarget;
    }

    public float getModifier() {
        return modifier;
    }

    public Operation getOperation() {
        return operation;
    }

    public boolean affectsChance() {
        return affectsChance;
    }

    public RecipeModifier multiply(float value) {
        return new RecipeModifier(target, ioTarget, modifier * value, operation, affectsChance);
    }

    public RecipeModifier add(float value) {
        return new RecipeModifier(target, ioTarget, modifier + value, operation, affectsChance);
    }

    public static float applyModifiers(Collection<RecipeModifier> modifiers, String target, IOType ioType, float value, boolean isChance) {
        if (modifiers == null || modifiers.isEmpty()) return value;
        float add = 0F;
        float mul = 1F;
        for (RecipeModifier mod : modifiers) {
            if (!mod.matches(target, ioType, isChance)) continue;
            switch (mod.operation) {
                case ADD -> add += mod.modifier;
                case SUBTRACT -> add -= mod.modifier;
                case MULTIPLY -> mul *= mod.modifier;
                case DIVIDE -> {
                    if (mod.modifier != 0F) mul /= mod.modifier;
                }
            }
        }
        return (value + add) * mul;
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
        tag.putFloat("value", modifier);
        tag.putBoolean("chance", affectsChance);
        return tag;
    }

    public static RecipeModifier deserializeNbt(CompoundTag tag) {
        String target = tag.getStringOr("target", "");
        IOType io = IOType.byKey(tag.getStringOr("ioTarget", IO_INPUT));
        Operation op = Operation.byId(tag.getIntOr("operation", 0));
        float value = tag.getFloatOr("value", 0F);
        boolean chance = tag.getBooleanOr("chance", false);
        return new RecipeModifier(target, io, value, op, chance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeModifier that)) return false;
        return Float.compare(that.modifier, modifier) == 0
                && affectsChance == that.affectsChance
                && Objects.equals(target, that.target)
                && ioTarget == that.ioTarget
                && operation == that.operation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, ioTarget, modifier, operation, affectsChance);
    }

    public static class ModifierApplier {
        public static final ModifierApplier DEFAULT_APPLIER = new ModifierApplier();

        public float inputAdd = 0;
        public float inputMul = 1;
        public float outputAdd = 0;
        public float outputMul = 1;

        public float apply(float value, IOType ioType) {
            if (ioType == IOType.OUTPUT) {
                return (value + outputAdd) * outputMul;
            }
            return (value + inputAdd) * inputMul;
        }

        public boolean isDefault() {
            return inputAdd == 0 && inputMul == 1 && outputAdd == 0 && outputMul == 1;
        }
    }

    public static void applyValueToApplier(ModifierApplier applier, RecipeModifier mod) {
        switch (mod.operation) {
            case ADD -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputAdd += mod.modifier;
                else applier.inputAdd += mod.modifier;
            }
            case SUBTRACT -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputAdd -= mod.modifier;
                else applier.inputAdd -= mod.modifier;
            }
            case MULTIPLY -> {
                if (mod.ioTarget == IOType.OUTPUT) applier.outputMul *= mod.modifier;
                else applier.inputMul *= mod.modifier;
            }
            case DIVIDE -> {
                if (mod.modifier == 0F) return;
                if (mod.ioTarget == IOType.OUTPUT) applier.outputMul /= mod.modifier;
                else applier.inputMul /= mod.modifier;
            }
        }
    }
}

package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Objects;

/**
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineOutput permits MachineOutput.ItemOutput, MachineOutput.FluidOutput {

    Codec<MachineOutput> CODEC = Codec.of(MachineOutput::encode, MachineOutput::decode);

    String type();

    float chance();

    MachineOutput withChance(float chance);

    MachineOutput applyModifiers(List<RecipeModifier> modifiers);

    static MachineOutput copyOf(MachineOutput output) {
        Objects.requireNonNull(output, "output");
        if (output instanceof ItemOutput item) return new ItemOutput(item.stack(), item.chance());
        if (output instanceof FluidOutput fluid) return new FluidOutput(fluid.stack(), fluid.chance());
        throw new IllegalArgumentException("Unknown machine output: " + output);
    }

    static List<MachineOutput> copyList(List<MachineOutput> outputs) {
        Objects.requireNonNull(outputs, "outputs");
        return outputs.stream().map(MachineOutput::copyOf).toList();
    }

    private static <T> DataResult<T> encode(MachineOutput output, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder()
                .add("type", ops.createString(output.type()))
                .add("chance", ops.createFloat(output.chance()));
        if (output instanceof ItemOutput item) {
            return builder.add("stack", item.stack(), ItemStack.CODEC).build(prefix);
        }
        if (output instanceof FluidOutput fluid) {
            return builder.add("stack", fluid.stack(), FluidStack.CODEC).build(prefix);
        }
        return DataResult.error(() -> "Unknown machine output: " + output);
    }

    private static <T> DataResult<Pair<MachineOutput, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(output -> Pair.of(output, input));
    }

    private static <T> DataResult<MachineOutput> decodeByType(String type, DynamicOps<T> ops, T input) {
        float chance = decodeChance(ops, input);
        return switch (type) {
            case "item" -> ops.get(input, "stack")
                    .flatMap(value -> ItemStack.CODEC.parse(ops, value))
                    .map(stack -> new ItemOutput(stack, chance));
            case "fluid" -> ops.get(input, "stack")
                    .flatMap(value -> FluidStack.CODEC.parse(ops, value))
                    .map(stack -> new FluidOutput(stack, chance));
            default -> DataResult.error(() -> "Unknown output type: " + type);
        };
    }

    private static <T> float decodeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }

    record ItemOutput(ItemStack stack, float chance) implements MachineOutput {
        public ItemOutput {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override
        public String type() {
            return "item";
        }

        @Override
        public ItemOutput withChance(float chance) {
            return new ItemOutput(stack, chance);
        }

        @Override
        public ItemOutput applyModifiers(List<RecipeModifier> modifiers) {
            ItemStack derived = stack.copy();
            derived.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, stack.getCount())));
            return new ItemOutput(derived, IntegrationTypeHelper.applyItemOutputChance(modifiers, chance));
        }
    }

    record FluidOutput(FluidStack stack, float chance) implements MachineOutput {
        public FluidOutput {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override
        public String type() {
            return "fluid";
        }

        @Override
        public FluidOutput withChance(float chance) {
            return new FluidOutput(stack, chance);
        }

        @Override
        public FluidOutput applyModifiers(List<RecipeModifier> modifiers) {
            FluidStack derived = stack.copy();
            derived.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, stack.getAmount())));
            return new FluidOutput(derived, IntegrationTypeHelper.applyFluidOutputChance(modifiers, chance));
        }
    }

    static float clampChance(float chance) {
        if (Float.isNaN(chance)) return 1F;
        if (chance < 0F) return 0F;
        if (chance > 1F) return 1F;
        return chance;
    }
}

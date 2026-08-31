package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Objects;

/**
 * A runtime machine output dispatched through its registered {@link OutputType}.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface MachineOutput {
    Codec<MachineOutput> CODEC = Codec.of(OutputRegistry::encode, OutputRegistry::decode);

    OutputType<? extends MachineOutput> outputType();

    float chance();

    default String type() {
        return outputType().serializedId();
    }

    default MachineOutput withChance(float chance) {
        return withChance(outputType(), this, chance);
    }

    default MachineOutput applyModifiers(List<RecipeModifier> modifiers) {
        return applyModifiers(outputType(), this, modifiers);
    }

    static MachineOutput copyOf(MachineOutput output) {
        Objects.requireNonNull(output, "output");
        OutputType<?> type = OutputRegistry.canonicalType(output.outputType());
        if (type == null) throw new IllegalArgumentException("Output type is not registered canonically: " + output.outputType().id());
        return copy(type, output);
    }

    static List<MachineOutput> copyList(List<MachineOutput> outputs) {
        Objects.requireNonNull(outputs, "outputs");
        return outputs.stream().map(MachineOutput::copyOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput> O withChance(OutputType<?> type, MachineOutput output, float chance) {
        return ((OutputType<O>) type).withChance((O) output, chance);
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput> O applyModifiers(OutputType<?> type, MachineOutput output,
                                                                List<RecipeModifier> modifiers) {
        return ((OutputType<O>) type).applyModifiers((O) output, modifiers);
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput> O copy(OutputType<?> type, MachineOutput output) {
        return ((OutputType<O>) type).copy((O) output);
    }

    record ItemOutput(ItemStack stack, float chance) implements CustomOutput {
        static final OutputType<ItemOutput> TYPE = new OutputType.Definition<>(
                Identifier.fromNamespaceAndPath("mmcr", "item"),
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "item"),
                        ItemStack.CODEC.fieldOf("stack").forGetter(ItemOutput::stack),
                        Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(ItemOutput::chance)
                ).apply(instance, (ignored, stack, chance) -> new ItemOutput(stack, chance))),
                (output, chance) -> new ItemOutput(output.stack(), chance),
                (output, modifiers) -> {
                    ItemStack derived = output.stack().copy();
                    derived.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, output.stack().getCount())));
                    return new ItemOutput(derived, IntegrationTypeHelper.applyItemOutputChance(modifiers, output.chance()));
                },
                output -> new ItemOutput(output.stack(), output.chance()), OutputType.Presentation.defaults(
                Identifier.fromNamespaceAndPath("mmcr", "item")), "item");

        public ItemOutput {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override
        public OutputType<ItemOutput> outputType() {
            return TYPE;
        }

        @Override
        public ItemOutput withChance(float chance) {
            return TYPE.withChance(this, chance);
        }

        @Override
        public ItemOutput applyModifiers(List<RecipeModifier> modifiers) {
            return TYPE.applyModifiers(this, modifiers);
        }
    }

    record FluidOutput(FluidStack stack, float chance) implements CustomOutput {
        static final OutputType<FluidOutput> TYPE = new OutputType.Definition<>(
                Identifier.fromNamespaceAndPath("mmcr", "fluid"),
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "fluid"),
                        FluidStack.CODEC.fieldOf("stack").forGetter(FluidOutput::stack),
                        Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(FluidOutput::chance)
                ).apply(instance, (ignored, stack, chance) -> new FluidOutput(stack, chance))),
                (output, chance) -> new FluidOutput(output.stack(), chance),
                (output, modifiers) -> {
                    FluidStack derived = output.stack().copy();
                    derived.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, output.stack().getAmount())));
                    return new FluidOutput(derived, IntegrationTypeHelper.applyFluidOutputChance(modifiers, output.chance()));
                },
                output -> new FluidOutput(output.stack(), output.chance()), OutputType.Presentation.defaults(
                Identifier.fromNamespaceAndPath("mmcr", "fluid")), "fluid");

        public FluidOutput {
            stack = stack == null ? FluidStack.EMPTY : stack.copy();
            chance = clampChance(chance);
        }

        @Override
        public OutputType<FluidOutput> outputType() {
            return TYPE;
        }

        @Override
        public FluidOutput withChance(float chance) {
            return TYPE.withChance(this, chance);
        }

        @Override
        public FluidOutput applyModifiers(List<RecipeModifier> modifiers) {
            return TYPE.applyModifiers(this, modifiers);
        }
    }

    static float clampChance(float chance) {
        if (Float.isNaN(chance)) return 1F;
        if (chance < 0F) return 0F;
        if (chance > 1F) return 1F;
        return chance;
    }
}

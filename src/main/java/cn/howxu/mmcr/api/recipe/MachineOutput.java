package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.MMCR;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
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
    Codec<ItemStack> RECIPE_ITEM_STACK_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(stack -> (long) stack.getCount()),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(ItemStack::getComponentsPatch)
    ).apply(instance, (holder, count, components) -> new ItemStack(holder, recipeStackAmount(count), components))));
    Codec<FluidStack> RECIPE_FLUID_STACK_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
            FluidStack.FLUID_NON_EMPTY_CODEC.fieldOf("id").forGetter(FluidStack::getFluidHolder),
            Codec.LONG.fieldOf("amount").forGetter(stack -> (long) stack.getAmount()),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(FluidStack::getComponentsPatch)
    ).apply(instance, (holder, amount, components) -> new FluidStack(holder, recipeStackAmount(amount), components))));
    Codec<MachineOutput> CODEC = Codec.of(OutputRegistry::encode, OutputRegistry::decode);

    static int recipeStackAmount(long amount) {
        if (amount < 1L) throw new IllegalArgumentException("Recipe stack amount must be positive");
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }

    static int optionalRecipeStackAmount(long amount) {
        return amount == 0L ? 0 : recipeStackAmount(amount);
    }

    OutputType<? extends MachineOutput> outputType();

    float chance();

    /**
     * Amount represented by this output for controller-side aggregation and presentation.
     */
    default long amount() {
        return 0L;
    }

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
        MachineOutput copy = copy(type, output);
        if (copy == null || copy.getClass() != output.getClass() || copy.outputType() != type) {
            throw new IllegalArgumentException("Copied output does not match registered type: " + type.id());
        }
        return copy;
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
                MMCR.id("item"),
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "item"),
                        RECIPE_ITEM_STACK_CODEC.fieldOf("stack").forGetter(ItemOutput::stack),
                        Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(ItemOutput::chance)
                ).apply(instance, (ignored, stack, chance) -> new ItemOutput(stack, chance))),
                (output, chance) -> new ItemOutput(output.stack(), chance),
                (output, modifiers) -> {
                    ItemStack derived = output.stack().copy();
                    derived.setCount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyItemOutput(modifiers, output.stack().getCount())));
                    return new ItemOutput(derived, IntegrationTypeHelper.applyItemOutputChance(modifiers, output.chance()));
                },
                output -> new ItemOutput(output.stack(), output.chance()), OutputType.Presentation.defaults(
                MMCR.id("item")), "item",
                (output, tags) -> new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        output.stack(), output.chance(), tags),
                requirement -> requirement instanceof ItemRequirement item
                        && item.io() == RecipeModifier.IOType.OUTPUT,
                requirement -> requirement instanceof ItemRequirement item
                        && item.io() == RecipeModifier.IOType.OUTPUT
                        ? new ItemOutput(item.resolvedStack(), item.chance()) : null);

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
                MMCR.id("fluid"),
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(ignored -> "fluid"),
                        RECIPE_FLUID_STACK_CODEC.fieldOf("stack").forGetter(FluidOutput::stack),
                        Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(FluidOutput::chance)
                ).apply(instance, (ignored, stack, chance) -> new FluidOutput(stack, chance))),
                (output, chance) -> new FluidOutput(output.stack(), chance),
                (output, modifiers) -> {
                    FluidStack derived = output.stack().copy();
                    derived.setAmount(IntegrationTypeHelper.asInt(IntegrationTypeHelper.applyFluidOutput(modifiers, output.stack().getAmount())));
                    return new FluidOutput(derived, IntegrationTypeHelper.applyFluidOutputChance(modifiers, output.chance()));
                },
                output -> new FluidOutput(output.stack(), output.chance()), OutputType.Presentation.defaults(
                MMCR.id("fluid")), "fluid",
                (output, tags) -> new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        output.stack(), output.chance(), tags),
                requirement -> requirement instanceof FluidRequirement fluid
                        && fluid.io() == RecipeModifier.IOType.OUTPUT,
                requirement -> requirement instanceof FluidRequirement fluid
                        && fluid.io() == RecipeModifier.IOType.OUTPUT
                        ? new FluidOutput(fluid.stack(), fluid.chance()) : null);

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

    record AggregationKey(@org.jetbrains.annotations.Nullable OutputType<?> type,
                                @org.jetbrains.annotations.Nullable ItemStack itemKey,
                                @org.jetbrains.annotations.Nullable FluidStack fluidKey) {

        public static final AggregationKey EMPTY = new AggregationKey(null, null, null);

        /**
         * Custom equality: Minecraft's {@link ItemStack} and {@link FluidStack} do not
         * override {@code equals}, so two records with semantically identical stacks
         * would otherwise be unequal. Compare content via {@code ItemStack.isSameItemSameComponents}
         * and {@link FluidStack} field-by-field instead.
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AggregationKey(OutputType<?> type1, ItemStack key, FluidStack fluidKey1))) return false;
            if (type != type1) return false;
            if (!Objects.equals(itemKey, key) || !Objects.equals(fluidKey, fluidKey1)) {
                if (itemKey != null && key != null
                        && ItemStack.isSameItemSameComponents(itemKey, key)) {
                    return fluidKey == null && fluidKey1 == null;
                }
                return fluidKey != null && fluidKey1 != null
                        && fluidKey.getFluid() == fluidKey1.getFluid()
                        && Objects.equals(fluidKey.getComponents(), fluidKey1.getComponents());
            }
            return true;
        }

        @Override
        public int hashCode() {
            int h = Objects.hashCode(type);
            if (itemKey != null) {
                h = 31 * h + ItemStack.hashItemAndComponents(itemKey);
            }
            if (fluidKey != null) {
                h = 31 * h + System.identityHashCode(fluidKey.getFluid());
                h = 31 * h + Objects.hashCode(fluidKey.getComponents());
            }
            return h;
        }
    }

    static AggregationKey aggregationKey(MachineOutput output) {
        if (output instanceof ItemOutput item) {
            ItemStack stripped = item.stack().copy();
            stripped.setCount(1);
            return new AggregationKey(item.outputType(), stripped, null);
        }
        if (output instanceof FluidOutput fluid) {
            FluidStack stripped = fluid.stack().copy();
            stripped.setAmount(1);
            return new AggregationKey(fluid.outputType(), null, stripped);
        }
        return AggregationKey.EMPTY;
    }

    static long scaledAmount(MachineOutput output) {
         if (output instanceof ItemOutput item) return item.stack().getCount();
         if (output instanceof FluidOutput fluid) return fluid.stack().getAmount();
         return output.amount();
    }

    static MachineOutput withScaledAmount(MachineOutput template, long amount, float chance) {
         if (template instanceof ItemOutput item) {
             ItemStack stack = item.stack().copy();
             stack.setCount((int) Math.min(amount, Integer.MAX_VALUE));
             return new ItemOutput(stack, chance);
         }
         if (template instanceof FluidOutput fluid) {
             FluidStack stack = fluid.stack().copy();
             stack.setAmount((int) Math.min(amount, Integer.MAX_VALUE));
             return new FluidOutput(stack, chance);
         }
         return template;
    }
}

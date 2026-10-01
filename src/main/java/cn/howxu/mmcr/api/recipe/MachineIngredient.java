package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Dynamic;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.crafting.Ingredient;

public sealed interface MachineIngredient {

    Codec<MachineIngredient> CODEC = Codec.of(MachineIngredient::encode, MachineIngredient::decode);

    String type();

    private static <T> DataResult<T> encode(MachineIngredient ingredient, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder().add("type", ops.createString(ingredient.type()));
        if (ingredient instanceof ItemIngredient(
                Ingredient item1, int count, DataComponentPredicateSet components, float chance
        )) {
            builder = builder
                    .add("item", item1, Ingredient.CODEC)
                    .add("count", ops.createInt(count));
            if (!components.isEmpty()) builder = builder.add("components", components, DataComponentPredicateSet.CODEC);
            if (chance != 1F) builder = builder.add("consume_chance", ops.createFloat(chance));
            return builder.build(prefix);
        }
        if (ingredient instanceof FluidIngredient(
                net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid1, int amount, float consumeChance
        )) {
            builder = builder
                    .add("fluid", fluid1, net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC)
                    .add("amount", ops.createInt(amount));
            if (consumeChance != 1F) builder = builder.add("consume_chance", ops.createFloat(consumeChance));
            return builder.build(prefix);
        }
        if (ingredient instanceof EnergyIngredient(RecipeModifier.IOType io, long fePerTick)) {
            return builder
                    .add("io", ops.createString(io.getKey()))
                    .add("fe_per_tick", ops.createLong(fePerTick))
                    .build(prefix);
        }
        return DataResult.error(() -> "Unknown machine ingredient: " + ingredient);
    }

    private static <T> DataResult<Pair<MachineIngredient, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(ingredient -> Pair.of(ingredient, input));
    }

    private static <T> DataResult<MachineIngredient> decodeByType(String type, DynamicOps<T> ops, T input) {
        return switch (type) {
              case "item" -> ops.get(input, "item")
                      .flatMap(value -> Ingredient.CODEC.parse(ops, value))
                      .flatMap(item -> ops.get(input, "count")
                              .flatMap(ops::getNumberValue)
                            .flatMap(count -> decodeComponents(ops, input)
                                    .map(components -> new ItemIngredient(item, count.intValue(), components, decodeConsumeChance(ops, input)))));
            case "fluid" -> ops.get(input, "fluid")
                    .flatMap(value -> net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC.parse(ops, value))
                    .flatMap(fluid -> ops.get(input, "amount")
                            .flatMap(ops::getNumberValue)
                            .map(amount -> new FluidIngredient(fluid, amount.intValue(), decodeConsumeChance(ops, input))));
            case "energy" -> {
                RecipeModifier.IOType io = ops.get(input, "io")
                        .flatMap(ops::getStringValue)
                        .map(RecipeModifier.IOType::byKey)
                        .map(t -> t == null ? RecipeModifier.IOType.INPUT : t)
                        .result()
                        .orElse(RecipeModifier.IOType.INPUT);
                yield ops.get(input, "fe_per_tick")
                        .flatMap(ops::getNumberValue)
                        .map(fePerTick -> new EnergyIngredient(io, fePerTick.longValue()));
            }
            default -> DataResult.error(() -> "Unknown ingredient type: " + type);
        };
    }

    private static <T> DataResult<DataComponentPredicateSet> decodeComponents(DynamicOps<T> ops, T input) {
        return new Dynamic<>(ops, input).get("components").result()
                .map(value -> DataComponentPredicateSet.CODEC.parse(value))
                .orElseGet(() -> DataResult.success(DataComponentPredicateSet.EMPTY));
    }

    private static <T> float decodeConsumeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "consume_chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }

    record ItemIngredient(Ingredient item, int count, DataComponentPredicateSet components, float consumeChance) implements MachineIngredient {
        public ItemIngredient(Ingredient item, int count) {
            this(item, count, DataComponentPredicateSet.EMPTY, 1F);
        }

        public ItemIngredient {
            components = components == null ? DataComponentPredicateSet.EMPTY : components;
            consumeChance = MachineOutput.clampChance(consumeChance);
        }

        @Override public String type() {
            return "item";
        }
    }

    record FluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid, int amount, float consumeChance) implements MachineIngredient {
        public FluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid, int amount) {
            this(fluid, amount, 1F);
        }

        public FluidIngredient {
            consumeChance = MachineOutput.clampChance(consumeChance);
        }

        @Override public String type() {
            return "fluid";
        }
    }

    record EnergyIngredient(RecipeModifier.IOType io, long fePerTick) implements MachineIngredient {
        public EnergyIngredient(long fePerTick) {
            this(RecipeModifier.IOType.INPUT, fePerTick);
        }

        public EnergyIngredient {
            if (io == null) io = RecipeModifier.IOType.INPUT;
        }

        @Override public String type() {
            return "energy";
        }
    }
}

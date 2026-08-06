package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.crafting.Ingredient;

public sealed interface MachineIngredient {

    Codec<MachineIngredient> CODEC = Codec.of(MachineIngredient::encode, MachineIngredient::decode);

    String type();

    private static <T> DataResult<T> encode(MachineIngredient ingredient, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder().add("type", ops.createString(ingredient.type()));
        if (ingredient instanceof ItemIngredient item) {
            return builder
                    .add("item", item.item(), Ingredient.CODEC)
                    .add("count", ops.createInt(item.count()))
                    .build(prefix);
        }
        if (ingredient instanceof FluidIngredient fluid) {
            return builder
                    .add("fluid", fluid.fluid(), net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC)
                    .add("amount", ops.createInt(fluid.amount()))
                    .build(prefix);
        }
        if (ingredient instanceof EnergyIngredient energy) {
            return builder
                    .add("io", ops.createString(energy.io().getKey()))
                    .add("fe_per_tick", ops.createInt(energy.fePerTick()))
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
                            .map(count -> new ItemIngredient(item, count.intValue())));
            case "fluid" -> ops.get(input, "fluid")
                    .flatMap(value -> net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC.parse(ops, value))
                    .flatMap(fluid -> ops.get(input, "amount")
                            .flatMap(ops::getNumberValue)
                            .map(amount -> new FluidIngredient(fluid, amount.intValue())));
            case "energy" -> {
                RecipeModifier.IOType io = ops.get(input, "io")
                        .flatMap(ops::getStringValue)
                        .map(RecipeModifier.IOType::byKey)
                        .map(t -> t == null ? RecipeModifier.IOType.INPUT : t)
                        .result()
                        .orElse(RecipeModifier.IOType.INPUT);
                yield ops.get(input, "fe_per_tick")
                        .flatMap(ops::getNumberValue)
                        .map(fePerTick -> new EnergyIngredient(io, fePerTick.intValue()));
            }
            default -> DataResult.error(() -> "Unknown ingredient type: " + type);
        };
    }

    record ItemIngredient(Ingredient item, int count) implements MachineIngredient {
        @Override public String type() {
            return "item";
        }
    }

    record FluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid, int amount) implements MachineIngredient {
        @Override public String type() {
            return "fluid";
        }
    }

    record EnergyIngredient(RecipeModifier.IOType io, int fePerTick) implements MachineIngredient {
        public EnergyIngredient(int fePerTick) {
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

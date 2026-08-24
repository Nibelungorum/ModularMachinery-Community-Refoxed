package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Dynamic;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public interface MachineRequirement {

    Codec<List<String>> TAGS_CODEC = Codec.STRING.listOf();
    Codec<MachineRequirement> CODEC = Codec.of(MachineRequirement::encode, MachineRequirement::decode);

    RequirementType<? extends MachineRequirement> type();

    RecipeModifier.IOType io();

    default List<String> tags() {
        return List.of();
    }

    default boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        return RequirementHandlerRegistry.simulate(this, context, requirementIndex);
    }

    default boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return RequirementHandlerRegistry.commit(this, context, requirementIndex);
    }

    default int maxInputParallelism(RecipeCraftingContext context, int limit) {
        return RequirementHandlerRegistry.maxInputParallelism(this, context, limit);
    }

    default boolean ioTick(RecipeCraftingContext context, int requirementIndex) {
        return RequirementHandlerRegistry.ioTick(this, context, requirementIndex);
    }

    static MachineRequirement fromInput(MachineIngredient ingredient) {
        if (ingredient instanceof MachineIngredient.ItemIngredient item) {
            return new ItemRequirement(RecipeModifier.IOType.INPUT, item.item(), item.count(), ItemStack.EMPTY,
                    1F, List.of(), item.components(), item.consumeChance());
        }
        if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
            return new FluidRequirement(RecipeModifier.IOType.INPUT, fluid.fluid(), fluid.amount(), FluidStack.EMPTY);
        }
        if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
            return new EnergyRequirement(energy.io(), energy.fePerTick());
        }
        throw new IllegalArgumentException("Unknown machine ingredient: " + ingredient);
    }

    static MachineRequirement itemOutput(ItemStack stack) {
        return itemOutput(stack, 1F);
    }

    static MachineRequirement itemOutput(ItemStack stack, float chance) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack, chance, List.of(), DataComponentPredicateSet.EMPTY, 1F);
    }

    static MachineRequirement fluidOutput(FluidStack stack) {
        return new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack.copy());
    }

    static MachineRequirement fluidOutput(FluidStack stack, float chance) {
        return new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack.copy(), chance, List.of());
    }

    private static <T> DataResult<T> encode(MachineRequirement requirement, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder()
                .add("type", ops.createString(requirement.type().id().getPath()))
                .add("io", RecipeModifier.IO_TYPE_CODEC.encodeStart(ops, requirement.io()).getOrThrow());
        if (!requirement.tags().isEmpty()) {
            builder = builder.add("tags", requirement.tags(), TAGS_CODEC);
        }
        if (requirement instanceof ItemRequirement item) {
            if (item.io() == RecipeModifier.IOType.INPUT) {
                builder = builder
                        .add("item", item.item(), Ingredient.CODEC)
                        .add("count", ops.createInt(item.count()));
                if (!item.components().isEmpty()) builder = builder.add("components", item.components(), DataComponentPredicateSet.CODEC);
                if (item.consumeChance() != 1F) builder = builder.add("consume_chance", ops.createFloat(item.consumeChance()));
                return builder.build(prefix);
            }
            ItemStack stack = item.stack(ops);
            var itemBuilder = builder.add("stack", stack, ItemStack.CODEC);
            if (item.chance() != 1F) itemBuilder = itemBuilder.add("chance", ops.createFloat(item.chance()));
            return itemBuilder.build(prefix);
        }
        if (requirement instanceof FluidRequirement fluid) {
            if (fluid.io() == RecipeModifier.IOType.INPUT) {
                return builder
                        .add("fluid", fluid.fluid(), FluidIngredient.CODEC)
                        .add("amount", ops.createInt(fluid.amount()))
                        .build(prefix);
            }
            var fluidBuilder = builder.add("stack", fluid.stack(), FluidStack.CODEC);
            if (fluid.chance() != 1F) fluidBuilder = fluidBuilder.add("chance", ops.createFloat(fluid.chance()));
            return fluidBuilder.build(prefix);
        }
        if (requirement instanceof EnergyRequirement energy) {
            return builder.add("fe_per_tick", ops.createInt(energy.fePerTick())).build(prefix);
        }
        if (requirement instanceof SmartInterfaceRequirement smartInterface) {
            return builder
                    .add("interface_type", ops.createString(smartInterface.interfaceType()))
                    .add("min_value", ops.createFloat(smartInterface.minValue()))
                    .add("max_value", ops.createFloat(smartInterface.maxValue()))
                    .build(prefix);
        }
        return DataResult.error(() -> "Unknown machine requirement: " + requirement);
    }

    private static <T> DataResult<Pair<MachineRequirement, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(requirement -> Pair.of(requirement, input));
    }

    private static <T> DataResult<MachineRequirement> decodeByType(String serializedType, DynamicOps<T> ops, T input) {
        Identifier typeId;
        try {
            typeId = serializedType.contains(":") ? Identifier.parse(serializedType) : MMCR.id(serializedType);
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Invalid requirement type: " + serializedType);
        }
        RequirementType<MachineRequirement> type = new RequirementType<>(typeId);
        if (RequirementHandlerRegistry.handlerFor(type) == null) {
            return DataResult.error(() -> "Unknown requirement type: " + serializedType);
        }
        if (ItemRequirement.TYPE.equals(type)) return decodeItem(ops, input);
        if (FluidRequirement.TYPE.equals(type)) return decodeFluid(ops, input);
        if (EnergyRequirement.TYPE.equals(type)) {
            RecipeModifier.IOType io = ops.get(input, "io")
                    .flatMap(value -> ops.getStringValue(value))
                    .map(RecipeModifier.IOType::byKey)
                    .map(t -> t == null ? RecipeModifier.IOType.INPUT : t)
                    .result()
                    .orElse(RecipeModifier.IOType.INPUT);
            List<String> tags = decodeTags(ops, input);
            return ops.get(input, "fe_per_tick")
                    .flatMap(ops::getNumberValue)
                    .<MachineRequirement>map(fePerTick -> new EnergyRequirement(io, fePerTick.intValue(), tags));
        }
        if (SmartInterfaceRequirement.TYPE.equals(type)) {
            return decodeIo(ops, input).flatMap(io -> ops.get(input, "interface_type")
                    .flatMap(ops::getStringValue)
                    .flatMap(interfaceType -> ops.get(input, "min_value")
                            .flatMap(ops::getNumberValue)
                            .flatMap(minValue -> ops.get(input, "max_value")
                                    .flatMap(ops::getNumberValue)
                                    .<MachineRequirement>map(maxValue -> new SmartInterfaceRequirement(io, interfaceType,
                                            minValue.floatValue(), maxValue.floatValue())))));
        }
        return DataResult.error(() -> "Unknown requirement type: " + serializedType);
    }

    private static <T> DataResult<MachineRequirement> decodeItem(DynamicOps<T> ops, T input) {
        return decodeIo(ops, input).flatMap(io -> {
            List<String> tags = decodeTags(ops, input);
            if (io == RecipeModifier.IOType.OUTPUT) {
                return ops.get(input, "stack")
                        .flatMap(value -> decodeItemOutputStack(ops, value, io, decodeChance(ops, input), tags));
            }
            return ops.get(input, "item")
                    .flatMap(value -> Ingredient.CODEC.parse(ops, value))
                    .flatMap(item -> ops.get(input, "count")
                            .flatMap(ops::getNumberValue)
                            .flatMap(count -> decodeComponents(ops, input).map(components -> new ItemRequirement(io, item,
                                    count.intValue(), ItemStack.EMPTY, 1F, tags, components, decodeConsumeChance(ops, input)))));
        });
    }

    private static <T> DataResult<MachineRequirement> decodeFluid(DynamicOps<T> ops, T input) {
        return decodeIo(ops, input).flatMap(io -> {
            List<String> tags = decodeTags(ops, input);
            if (io == RecipeModifier.IOType.OUTPUT) {
                return ops.get(input, "stack")
                        .flatMap(value -> FluidStack.CODEC.parse(ops, value))
                        .map(stack -> new FluidRequirement(io, null, 0, stack, decodeChance(ops, input), tags));
            }
            return ops.get(input, "fluid")
                    .flatMap(value -> FluidIngredient.CODEC.parse(ops, value))
                    .flatMap(fluid -> ops.get(input, "amount")
                            .flatMap(ops::getNumberValue)
                            .map(amount -> new FluidRequirement(io, fluid, amount.intValue(), FluidStack.EMPTY, tags)));
        });
    }

    private static <T> List<String> decodeTags(DynamicOps<T> ops, T input) {
        return ops.get(input, "tags")
                .flatMap(value -> TAGS_CODEC.parse(ops, value))
                .result()
                .orElse(List.of());
    }

    private static <T> float decodeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }

    private static <T> DataResult<DataComponentPredicateSet> decodeComponents(DynamicOps<T> ops, T input) {
        return new Dynamic<>(ops, input).get("components").result()
                .map(value -> DataComponentPredicateSet.CODEC.parse(value))
                .orElseGet(() -> DataResult.success(DataComponentPredicateSet.EMPTY));
    }

    private static <T> DataResult<MachineRequirement> decodeItemOutputStack(DynamicOps<T> ops, T stackInput,
            RecipeModifier.IOType io, float chance, List<String> tags) {
        Dynamic<T> stack = new Dynamic<>(ops, stackInput);
        return stack.get("id").asString().flatMap(id -> {
            Identifier itemId;
            try {
                itemId = Identifier.parse(id);
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Invalid item id " + id);
            }
            var item = BuiltInRegistries.ITEM.getValue(itemId);
            if (item == null) return DataResult.error(() -> "Unknown item " + itemId);
            int count = stack.get("count").asNumber().result().map(Number::intValue).orElse(1);
            ItemStack baseStack = new ItemStack(item, count);
            return decodeComponents(ops, stackInput)
                    .map(components -> new ItemRequirement(io, null, 0, baseStack, chance, tags, components, 1F));
        });
    }

    private static <T> float decodeConsumeChance(DynamicOps<T> ops, T input) {
        return ops.get(input, "consume_chance")
                .flatMap(ops::getNumberValue)
                .map(Number::floatValue)
                .result()
                .orElse(1F);
    }

    private static <T> DataResult<RecipeModifier.IOType> decodeIo(DynamicOps<T> ops, T input) {
        return ops.get(input, "io")
                .flatMap(value -> RecipeModifier.IO_TYPE_CODEC.parse(ops, value));
    }
}

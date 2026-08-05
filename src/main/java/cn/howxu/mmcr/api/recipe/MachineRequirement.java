package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/**
 * @author howxu <dev@howxu.cn>
 */
public sealed interface MachineRequirement permits ItemRequirement, FluidRequirement, EnergyRequirement {

    Codec<MachineRequirement> CODEC = Codec.of(MachineRequirement::encode, MachineRequirement::decode);

    String type();

    String describe();

    boolean matches(ProcessingComponent component);

    CraftCheck simulate(RecipeCraftingContext context);

    boolean commit(RecipeCraftingContext context);

    default boolean ioTick(RecipeCraftingContext context) {
        return true;
    }

    private static <T> DataResult<T> encode(MachineRequirement requirement, DynamicOps<T> ops, T prefix) {
        var builder = ops.mapBuilder().add("type", ops.createString(requirement.type()));
        if (requirement instanceof ItemRequirement item) {
            builder.add("item", item.item(), net.minecraft.world.item.crafting.Ingredient.CODEC)
                    .add("count", ops.createInt(item.count()))
                    .add("io", ops.createString(item.ioType().getSerializedName()));
            if (item.tag() != null) builder.add("tag", ops.createString(item.tag()));
            return builder.build(prefix);
        }
        if (requirement instanceof FluidRequirement fluid) {
            builder.add("fluid", fluid.fluid(), net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC)
                    .add("amount", ops.createInt(fluid.amount()))
                    .add("io", ops.createString(fluid.ioType().getSerializedName()));
            if (fluid.tag() != null) builder.add("tag", ops.createString(fluid.tag()));
            return builder.build(prefix);
        }
        if (requirement instanceof EnergyRequirement energy) {
            return builder.add("fe_per_tick", ops.createInt(energy.fePerTick())).build(prefix);
        }
        return DataResult.error(() -> "Unknown machine requirement: " + requirement);
    }

    private static <T> DataResult<Pair<MachineRequirement, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type")
                .flatMap(ops::getStringValue)
                .flatMap(type -> decodeByType(type, ops, input))
                .map(requirement -> Pair.of(requirement, input));
    }

    private static <T> DataResult<MachineRequirement> decodeByType(String type, DynamicOps<T> ops, T input) {
        return switch (type) {
            case "item" -> ops.get(input, "item")
                    .flatMap(value -> net.minecraft.world.item.crafting.Ingredient.CODEC.parse(ops, value))
                    .flatMap(item -> ops.get(input, "count")
                            .flatMap(ops::getNumberValue)
                            .flatMap(count -> decodeIo(ops, input)
                                    .flatMap(io -> decodeTag(ops, input)
                                            .map(tag -> new ItemRequirement(item, count.intValue(), tag, io)))));
            case "fluid" -> ops.get(input, "fluid")
                    .flatMap(value -> net.neoforged.neoforge.fluids.crafting.FluidIngredient.CODEC.parse(ops, value))
                    .flatMap(fluid -> ops.get(input, "amount")
                            .flatMap(ops::getNumberValue)
                            .flatMap(amount -> decodeIo(ops, input)
                                    .flatMap(io -> decodeTag(ops, input)
                                            .map(tag -> new FluidRequirement(fluid, amount.intValue(), tag, io)))));
            case "energy" -> ops.get(input, "fe_per_tick")
                    .flatMap(ops::getNumberValue)
                    .map(fePerTick -> new EnergyRequirement(fePerTick.intValue()));
            default -> DataResult.error(() -> "Unknown requirement type: " + type);
        };
    }

    private static <T> DataResult<cn.howxu.mmcr.util.IOType> decodeIo(DynamicOps<T> ops, T input) {
        return ops.get(input, "io")
                .flatMap(ops::getStringValue)
                .flatMap(value -> switch (value) {
                    case "input" -> DataResult.success(cn.howxu.mmcr.util.IOType.INPUT);
                    case "output" -> DataResult.success(cn.howxu.mmcr.util.IOType.OUTPUT);
                    default -> DataResult.error(() -> "Unknown IO type: " + value);
                });
    }

    private static <T> DataResult<String> decodeTag(DynamicOps<T> ops, T input) {
        return ops.get(input, "tag")
                .flatMap(ops::getStringValue)
                .result()
                .map(DataResult::success)
                .orElseGet(() -> DataResult.success(null));
    }
}

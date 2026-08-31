package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Objects;

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

    static MachineRequirement copyOf(MachineRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        if (requirement instanceof ItemRequirement item) {
            return new ItemRequirement(item.io(), item.item(), item.count(), item.stack(), item.chance(),
                    item.tags(), item.components(), item.consumeChance());
        }
        if (requirement instanceof FluidRequirement fluid) {
            return new FluidRequirement(fluid.io(), fluid.fluid(), fluid.amount(), fluid.stack(), fluid.chance(),
                    fluid.tags());
        }
        return copyThroughCodec(requirement);
    }

    static List<MachineRequirement> copyList(List<MachineRequirement> requirements) {
        Objects.requireNonNull(requirements, "requirements");
        return requirements.stream().map(MachineRequirement::copyOf).toList();
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
        if (requirement == null || requirement.type() == null) {
            return DataResult.error(() -> "Requirement type must not be null");
        }
        RequirementType<?> type = requirement.type();
        if (RequirementHandlerRegistry.handlerFor(type) == null) {
            return DataResult.error(() -> "Unknown requirement type: " + type.id());
        }
        return encodeByType(type, requirement, ops, prefix);
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
        RequirementType<?> type = RequirementHandlerRegistry.typeFor(typeId);
        if (type == null || RequirementHandlerRegistry.handlerFor(type) == null) {
            return DataResult.error(() -> "Unknown requirement type: " + serializedType);
        }
        return decodeByType(type, ops, input, serializedType);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<T> encodeByType(RequirementType<?> type, MachineRequirement requirement,
                                                   DynamicOps<T> ops, T prefix) {
        Codec<MachineRequirement> codec = (Codec<MachineRequirement>) type.codec().codec();
        return codec.encode(requirement, ops, prefix);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataResult<MachineRequirement> decodeByType(RequirementType<?> type, DynamicOps<T> ops,
                                                                    T input, String serializedType) {
        Codec<? extends MachineRequirement> codec = (Codec<? extends MachineRequirement>) type.codec().codec();
        return codec.parse(ops, input).flatMap(requirement -> {
            if (requirement == null || !type.equals(requirement.type()) || requirement.io() == null) {
                return DataResult.error(() -> "Decoded requirement does not match registered type: " + type.id());
            }
            return DataResult.success(requirement);
        });
    }

    private static MachineRequirement copyThroughCodec(MachineRequirement requirement) {
        @SuppressWarnings("unchecked") Codec<MachineRequirement> codec =
                (Codec<MachineRequirement>) (Codec<?>) requirement.type().codec().codec();
        return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow()).getOrThrow();
    }
}

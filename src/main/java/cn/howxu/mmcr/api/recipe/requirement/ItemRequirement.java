package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * @author howxu <dev@howxu.cn>
 */
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags,
                              DataComponentPredicateSet components, float consumeChance) implements MachineRequirement {
    private static final Identifier TYPE_ID = MMCR.id("item");
    public static final MapCodec<ItemRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(ItemRequirement::io),
            Ingredient.CODEC.optionalFieldOf("item").forGetter(value -> Optional.ofNullable(value.item())),
            Codec.INT.optionalFieldOf("count", 0).forGetter(ItemRequirement::count),
            ItemStack.CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter(ItemRequirement::stack),
            Codec.FLOAT.optionalFieldOf("chance", 1F).forGetter(ItemRequirement::chance),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(ItemRequirement::tags),
            DataComponentPredicateSet.CODEC.optionalFieldOf("components", DataComponentPredicateSet.EMPTY)
                    .forGetter(ItemRequirement::components),
            Codec.FLOAT.optionalFieldOf("consume_chance", 1F)
                    .forGetter(ItemRequirement::consumeChance)
    ).apply(instance, (ignored, io, item, count, stack, chance, tags, components, consumeChance) ->
            new ItemRequirement(io, item.orElse(null), count, stack, chance, tags, components, consumeChance)));
    private static final RequirementHandler<ItemRequirement> HANDLER = new ItemRequirementHandler();
    public static final RequirementType<ItemRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, ItemRequirement::copy);

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack) {
        this(io, item, count, stack, 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
    }

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, List<String> tags) {
        this(io, item, count, stack, 1F, tags, DataComponentPredicateSet.EMPTY, 1F);
    }

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags) {
        this(io, item, count, stack, chance, tags, DataComponentPredicateSet.EMPTY, 1F);
    }

    public ItemRequirement {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
        components = components == null ? DataComponentPredicateSet.EMPTY : components;
        consumeChance = MachineOutput.clampChance(consumeChance);
    }

    @Override
    public RequirementType<ItemRequirement> type() {
        return TYPE;
    }

    public ItemStack stack(DynamicOps<?> ops) {
        ItemStack copy = stack.copy();
        if (!components.isEmpty()) {
            if (ops == null) components.applyTo(copy);
            else components.applyTo(copy, ops);
        }
        return copy;
    }

    public ItemStack resolvedStack() {
        return stack(null);
    }

    private static ItemRequirement copy(ItemRequirement requirement) {
        return new ItemRequirement(requirement.io(), requirement.item(), requirement.count(), requirement.stack(),
                requirement.chance(), requirement.tags(), requirement.components(), requirement.consumeChance());
    }

}

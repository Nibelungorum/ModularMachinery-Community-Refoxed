package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags,
                              DataComponentPredicateSet components, float consumeChance) implements MachineRequirement {
    public static final RequirementType<ItemRequirement> TYPE = new RequirementType<>(MMCR.id("item"));

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

}

package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags,
                              DataComponentPredicateSet components, float consumeChance) implements MachineRequirement {

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
    public String type() {
        return "item";
    }

    @Override
    public boolean simulate(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.simulateItemInput(requirementIndex, this)
                : context.simulateItemOutput(requirementIndex, this);
    }

    @Override
    public boolean commit(RecipeCraftingContext context, int requirementIndex) {
        return io == RecipeModifier.IOType.INPUT
                ? context.collectItemInputRoute(requirementIndex)
                : context.collectItemOutputRoute(requirementIndex);
    }

    @Override
    public int maxInputParallelism(RecipeCraftingContext context, int limit) {
        if (io != RecipeModifier.IOType.INPUT || item == null || count <= 0) return -1;
        if (consumeChance == 0F) return Math.max(1, limit);
        if (!tags.isEmpty() || !components.isEmpty()) return -1;
        if (item.items().count() != 1) return -1;
        int available = context.countMatchingItemInputs(item, List.of());
        return Math.min(Math.max(1, limit), available / count);
    }
}

package cn.howxu.mmcr.api.recipe.requirement;

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
public record ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, float chance, List<String> tags) implements MachineRequirement {

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack) {
        this(io, item, count, stack, 1F, List.of());
    }

    public ItemRequirement(RecipeModifier.IOType io, @Nullable Ingredient item, int count, ItemStack stack, List<String> tags) {
        this(io, item, count, stack, 1F, tags);
    }

    public ItemRequirement {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        chance = MachineOutput.clampChance(chance);
        tags = tags == null ? List.of() : List.copyOf(tags);
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
}

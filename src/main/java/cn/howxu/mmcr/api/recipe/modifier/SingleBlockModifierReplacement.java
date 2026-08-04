package cn.howxu.mmcr.api.recipe.modifier;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SingleBlockModifierReplacement extends AbstractModifierReplacement {

    public SingleBlockModifierReplacement(List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        super(modifiers, description, descriptiveStack);
    }

    public SingleBlockModifierReplacement(String modifierName, List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
    }

    public SingleBlockModifierReplacement(String modifierName, List<RecipeModifier> modifiers, List<String> description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
    }
}

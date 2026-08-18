package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public class SingleBlockModifierReplacement extends AbstractModifierReplacement {

    private final BlockPredicate replacement;

    public SingleBlockModifierReplacement(
            String modifierName,
            BlockPredicate replacement,
            List<RecipeModifier> modifiers,
            ItemStack descriptiveStack) {
        super(modifierName, modifiers, List.of(), descriptiveStack);
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    public BlockPredicate getReplacement() {
        return replacement;
    }
}

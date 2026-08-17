package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockArray;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MultiBlockModifierReplacement extends AbstractModifierReplacement {

    private final BlockArray blockArray;

    public MultiBlockModifierReplacement(String modifierName, BlockArray blockArray, List<RecipeModifier> modifiers, List<String> description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.blockArray = blockArray;
    }

    public BlockArray getBlockArray() {
        return blockArray;
    }
}

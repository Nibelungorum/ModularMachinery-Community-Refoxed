package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockArray;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

public class MultiBlockModifierReplacement extends AbstractModifierReplacement {

    private final BlockArray blockArray;

    public MultiBlockModifierReplacement(String modifierName, BlockArray blockArray, List<RecipeModifier> modifiers, List<String> description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.blockArray = blockArray;
    }

    public BlockArray getBlockArray() {
        return blockArray;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MultiBlockModifierReplacement other)) return false;
        return baseEquals(other) && Objects.equals(blockArray, other.blockArray);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseHashCode(), blockArray);
    }
}

package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class SingleBlockModifierReplacement extends AbstractModifierReplacement {

    private @Nullable BlockPos pos;
    private final BlockPredicate replacement;

    public SingleBlockModifierReplacement(List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        super(modifiers, description, descriptiveStack);
        this.replacement = new BlockPredicate.Any();
    }

    public SingleBlockModifierReplacement(String modifierName, List<RecipeModifier> modifiers, String description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.replacement = new BlockPredicate.Any();
    }

    public SingleBlockModifierReplacement(String modifierName, List<RecipeModifier> modifiers, List<String> description, ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.replacement = new BlockPredicate.Any();
    }

    public SingleBlockModifierReplacement(
            String modifierName,
            BlockPos pos,
            BlockPredicate replacement,
            List<RecipeModifier> modifiers,
            String description,
            ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.pos = requirePosition(pos);
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    private SingleBlockModifierReplacement(
            String modifierName,
            BlockPos pos,
            BlockPredicate replacement,
            List<RecipeModifier> modifiers,
            List<String> description,
            ItemStack descriptiveStack) {
        super(modifierName, modifiers, description, descriptiveStack);
        this.pos = requirePosition(pos);
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    public @Nullable BlockPos getPos() {
        return pos;
    }

    public BlockPredicate getReplacement() {
        return replacement;
    }

    public SingleBlockModifierReplacement setPos(BlockPos pos) {
        this.pos = requirePosition(pos);
        return this;
    }

    public SingleBlockModifierReplacement copyAt(BlockPos newPos) {
        return new SingleBlockModifierReplacement(
                modifierName, newPos, replacement, modifiers, description, descriptiveStack);
    }

    private static BlockPos requirePosition(BlockPos pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos");
        }
        return pos;
    }
}

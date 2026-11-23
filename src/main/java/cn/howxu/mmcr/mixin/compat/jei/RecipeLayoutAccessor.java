package cn.howxu.mmcr.mixin.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.library.gui.recipes.RecipeLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * JEI snapshots transfer slots before it creates the per-layout preview widgets.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(value = RecipeLayout.class, remap = false)
public interface RecipeLayoutAccessor {
    @Mutable
    @Accessor("recipeSlotsView")
    void mmcr$setRecipeSlotsView(IRecipeSlotsView slots);
}

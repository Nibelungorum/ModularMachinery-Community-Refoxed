package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Presents exact source totals as a native-sprite ingredient without item transfer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceJeiAdapter implements JeiIngredientAdapter {
    @Override
    public ResourceLocation typeId() {
        return ArsSourceIds.SOURCE;
    }

    @Override
    public IIngredientType<?> ingredientType() {
        return SourceJeiIngredient.TYPE;
    }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        SourceJeiIngredient ingredient = new SourceJeiIngredient(entry.amount(), entry.role() == RecipeIngredientRole.INPUT);
        return Optional.of(new JeiDisplayEntry(entry.role(), ArsSourceIds.SOURCE, ingredientType(), ingredient,
                (int) Math.min(entry.amount(), Integer.MAX_VALUE), 1F, null, false));
    }

    @Override
    public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
        return Optional.empty();
    }
}

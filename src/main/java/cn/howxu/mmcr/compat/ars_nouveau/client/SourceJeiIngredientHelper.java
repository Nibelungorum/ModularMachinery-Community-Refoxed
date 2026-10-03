package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies source as one resource while retaining long recipe quantities.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceJeiIngredientHelper implements IIngredientHelper<SourceJeiIngredient> {
    @Override
    public IIngredientType<SourceJeiIngredient> getIngredientType() {
        return SourceJeiIngredient.TYPE;
    }

    @Override
    public String getDisplayName(SourceJeiIngredient ingredient) {
        return Component.translatable("jei.mmcr.source").getString();
    }

    @Override
    @Deprecated(forRemoval = true)
    public String getUniqueId(SourceJeiIngredient ingredient, UidContext context) {
        return ArsSourceIds.SOURCE.toString();
    }

    @Override
    public Object getUid(SourceJeiIngredient ingredient, UidContext context) {
        return ArsSourceIds.SOURCE;
    }

    @Override
    public ResourceLocation getResourceLocation(SourceJeiIngredient ingredient) {
        return ArsSourceIds.SOURCE;
    }

    @Override
    public long getAmount(SourceJeiIngredient ingredient) {
        return ingredient.amount();
    }

    @Override
    public SourceJeiIngredient copyWithAmount(SourceJeiIngredient ingredient, long amount) {
        return new SourceJeiIngredient(amount, ingredient.input());
    }

    @Override
    public SourceJeiIngredient copyIngredient(SourceJeiIngredient ingredient) {
        return ingredient;
    }

    @Override
    public SourceJeiIngredient normalizeIngredient(SourceJeiIngredient ingredient) {
        return copyWithAmount(ingredient, 1L);
    }

    @Override
    public String getErrorInfo(@Nullable SourceJeiIngredient ingredient) {
        return String.valueOf(ingredient);
    }
}

package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Identifies mana without loading Botania or treating its decorative icon as an item.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaJeiIngredientHelper implements IIngredientHelper<ManaJeiIngredient> {
    @Override public IIngredientType<ManaJeiIngredient> getIngredientType() { return ManaJeiIngredient.TYPE; }
    @Override public String getDisplayName(ManaJeiIngredient ingredient) {
        return Component.translatable("jei.mmcr.mana").getString();
    }
    @Override @Deprecated(forRemoval = true)
    public String getUniqueId(ManaJeiIngredient ingredient, UidContext context) { return BotaniaManaIds.MANA.toString(); }
    @Override public Object getUid(ManaJeiIngredient ingredient, UidContext context) { return BotaniaManaIds.MANA; }
    @Override public ResourceLocation getResourceLocation(ManaJeiIngredient ingredient) { return BotaniaManaIds.MANA; }
    @Override public long getAmount(ManaJeiIngredient ingredient) { return ingredient.amount(); }
    @Override public ManaJeiIngredient copyWithAmount(ManaJeiIngredient ingredient, long amount) {
        return new ManaJeiIngredient(amount, ingredient.input());
    }
    @Override public ManaJeiIngredient copyIngredient(ManaJeiIngredient ingredient) { return ingredient; }
    @Override public ManaJeiIngredient normalizeIngredient(ManaJeiIngredient ingredient) { return copyWithAmount(ingredient, 1L); }
    @Override public String getErrorInfo(@Nullable ManaJeiIngredient ingredient) { return String.valueOf(ingredient); }
}

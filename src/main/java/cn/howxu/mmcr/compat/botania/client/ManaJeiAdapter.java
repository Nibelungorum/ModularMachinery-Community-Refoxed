package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Neutral non-transferable adapter for both mana directions.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaJeiAdapter implements JeiIngredientAdapter {
    @Override public ResourceLocation typeId() { return BotaniaManaIds.MANA; }
    @Override public IIngredientType<?> ingredientType() { return ManaJeiIngredient.TYPE; }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        var mana = new ManaJeiIngredient(entry.amount(), entry.role() == RecipeIngredientRole.INPUT);
        return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(), mana,
                (int) Math.min(entry.amount(), Integer.MAX_VALUE), 1F, null, false));
    }

    @Override public Optional<IRecipeTransferHandler<?, ?>> transferHandler() { return Optional.empty(); }
}

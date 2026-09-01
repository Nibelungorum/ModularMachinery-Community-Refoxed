package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Converts one MMCR requirement type into JEI display and transfer data.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface JeiIngredientAdapter {
    Identifier typeId();

    IIngredientType<?> ingredientType();

    Optional<JeiDisplayEntry> display(RecipeIoEntry entry);

    default Optional<IIngredientRenderer<?>> renderer(RecipeIoEntry entry) {
        return Optional.empty();
    }

    Optional<IRecipeTransferHandler<?, ?>> transferHandler();

    default Component transferError(RecipeIoEntry entry) {
        return Component.translatable("jei.mmcr.transfer.unsupported", entry.typeId());
    }
}

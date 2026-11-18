package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;

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

    /** True only if AE2's native JEI conversion preserves this real slot and its exact amount. */
    default boolean nativePatternSlot(JeiDisplayEntry entry) { return false; }

    /** Real ingredient values, not display samples. Empty means display-only. */
    default List<?> actualIngredients(JeiDisplayEntry entry) { return List.of(); }

    default MachineRecipeLayout.Kind layoutKind() {
        if (ingredientType() == VanillaTypes.ITEM_STACK) return MachineRecipeLayout.Kind.ITEM;
        if (ingredientType() == NeoForgeTypes.FLUID_STACK) return MachineRecipeLayout.Kind.FLUID;
        return ingredientType() == null ? MachineRecipeLayout.Kind.TEXT : MachineRecipeLayout.Kind.GENERIC;
    }

    default int displayOrder() {
        return switch (layoutKind()) { case FLUID -> 0; case ITEM -> 2; default -> 3; };
    }
    default String quantityText(long amount) { return MachineRecipeCategory.itemQuantityText(amount); }
    default Optional<Component> quantityTooltip(long amount) { return Optional.empty(); }

    Optional<IRecipeTransferHandler<?, ?>> transferHandler();

    default Component transferError(RecipeIoEntry entry) {
        return Component.translatable("jei.mmcr.transfer.unsupported", entry.typeId());
    }
}

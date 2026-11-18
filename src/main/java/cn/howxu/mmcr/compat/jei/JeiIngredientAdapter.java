package cn.howxu.mmcr.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Converts one MMCR requirement type into JEI display and transfer data.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface JeiIngredientAdapter {
    ResourceLocation typeId();

    IIngredientType<?> ingredientType();

    Optional<JeiDisplayEntry> display(RecipeIoEntry entry);

    default Optional<IIngredientRenderer<?>> renderer(RecipeIoEntry entry) {
        return Optional.empty();
    }

    /** Optional appearance and JEI indexing for the visible grid slot (not permission to fill buses or encode patterns). */
    record SlotStyle(int order, ResourceLocation background, boolean indexed, boolean headerText) {}
    default SlotStyle slotStyle() { return null; }

    /** Non-null puts this entry in a metadata row rather than an ingredient slot. */
    default RecipeDetails.Row detailRow(JeiDisplayEntry entry) { return null; }

    /** Null keeps the native ingredient overlay; a declared unit is rendered alongside the entry's chance. */
    default String quantityOverlay(JeiDisplayEntry entry) { return null; }
    default Optional<Component> quantityTooltip(JeiDisplayEntry entry) { return Optional.empty(); }

    /** Contributes exact JEI slots independently of item-bus filling. Return true to suppress the legacy slot. */
    default boolean addTransferSlots(IRecipeLayoutBuilder builder, JeiDisplayEntry entry) { return false; }

    Optional<IRecipeTransferHandler<?, ?>> transferHandler();

    default Component transferError(RecipeIoEntry entry) {
        return Component.translatable("jei.mmcr.transfer.unsupported", entry.typeId().toString());
    }
}

package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.util.ReadableNumber;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Presents source totals as neutral text without an Ars ingredient or transfer handler.
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
        return null;
    }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        Component label = Component.translatable(entry.role() == RecipeIngredientRole.INPUT
                ? "jei.mmcr.machine_recipe.source_input" : "jei.mmcr.machine_recipe.source_output",
                ReadableNumber.formatExact(entry.amount()));
        return Optional.of(new JeiDisplayEntry(entry.role(), ArsSourceIds.SOURCE, null, label,
                (int) Math.min(entry.amount(), Integer.MAX_VALUE), 1F, null, false));
    }

    @Override
    public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
        return Optional.empty();
    }
}

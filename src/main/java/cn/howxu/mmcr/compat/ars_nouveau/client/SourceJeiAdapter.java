package cn.howxu.mmcr.compat.ars_nouveau.client;

import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.util.ReadableNumber;

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

    private static final SlotStyle STYLE = new SlotStyle(-1,
            ResourceLocation.parse("mmcr:textures/gui/ars_nouveau/jei_source_slot.png"), true, true);
    @Override public SlotStyle slotStyle() { return STYLE; }

    @Override public String quantityOverlay(JeiDisplayEntry entry) {
        return ReadableNumber.formatForSlot(entry.exactAmount(), 0, "");
    }

    @Override
    public IIngredientType<?> ingredientType() {
        return SourceJeiIngredient.TYPE;
    }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        var value = entry.value() instanceof SourceRequirement requirement ? requirement : null;
        long amount = value == null || entry.declaredValues() ? entry.amount() : value.amount();
        float chance = value == null || entry.declaredValues() ? entry.chance() : value.io().isInput() ? value.consumeChance() : value.chance();
        SourceJeiIngredient ingredient = new SourceJeiIngredient(amount, entry.role() == RecipeIngredientRole.INPUT);
        return Optional.of(new JeiDisplayEntry(entry.role(), ArsSourceIds.SOURCE, ingredientType(), ingredient,
                (int) Math.min(amount, Integer.MAX_VALUE), chance, null, false, amount));
    }

    @Override
    public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
        return Optional.empty();
    }
}

package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.compat.botania.ManaRequirement;

import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import cn.howxu.mmcr.compat.jei.RecipeDetails;
import cn.howxu.mmcr.compat.jei.MachineRecipeDisplay;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Optional;

/** Non-transferable metadata adapter; mana never becomes a JEI ingredient slot.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaJeiAdapter implements JeiIngredientAdapter {
    @Override public ResourceLocation typeId() { return BotaniaManaIds.MANA; }
    @Override public @Nullable IIngredientType<?> ingredientType() { return null; }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        var value = entry.value() instanceof ManaRequirement requirement ? requirement : null;
        long amount = value == null || entry.declaredValues() ? entry.amount() : value.amount();
        float chance = value == null || entry.declaredValues() ? entry.chance() : value.io().isInput() ? value.consumeChance() : value.chance();
        var mana = new ManaJeiDisplay(amount, entry.role() == RecipeIngredientRole.INPUT);
        return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(), mana,
                (int) Math.min(amount, Integer.MAX_VALUE), chance, null, false, amount));
    }

    @Override public RecipeDetails.Row detailRow(JeiDisplayEntry entry) {
        if (!(entry.ingredient() instanceof ManaJeiDisplay mana)) return null;
        var label = MachineRecipeDisplay.withChanceLabel(mana.label(), entry.chance(), mana.input());
        var tip = Component.translatable(mana.input() ? "jei.mmcr.machine_recipe.mana_input" : "jei.mmcr.machine_recipe.mana_output",
                ReadableNumber.formatExact(mana.amount()));
        return new RecipeDetails.Row(label, BotaniaManaIds.CREATIVE_POOL, entry.chance() < 1F
                ? List.of(MachineRecipeDisplay.withChanceLabel(tip, entry.chance(), mana.input())) : List.of());
    }
    @Override public Optional<IRecipeTransferHandler<?, ?>> transferHandler() { return Optional.empty(); }
}

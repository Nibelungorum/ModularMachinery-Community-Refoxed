package cn.howxu.mmcr.compat.mekanism.client;

import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import cn.howxu.mmcr.util.ReadableNumber;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Mekanism-owned JEI samples and exact transfer stacks; not loaded by the absent-mod path.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalJeiAdapter implements JeiIngredientAdapter {
    private static final long CHEMICAL_RENDER_AMOUNT = 1000L;
    @Override
    public boolean addTransferSlots(IRecipeLayoutBuilder builder, JeiDisplayEntry entry) {
        IRecipeSlotBuilder slot = entry.role() == RecipeIngredientRole.INPUT
                ? builder.addInputSlot(-1000, -1000)
                : builder.addOutputSlot(-1000, -1000);
        for (ChemicalStack chemical : actualChemicalStacks(entry.ingredient())) {
            slot.addIngredient(MekanismJEI.TYPE_CHEMICAL, chemical.copyWithAmount(entry.exactAmount()));
        }
        return true;
    }

    private static List<ChemicalStack> actualChemicalStacks(Object ingredient) {
        if (ingredient instanceof List<?> ingredients) {
            return ingredients.stream()
                    .filter(ChemicalStack.class::isInstance)
                    .map(ChemicalStack.class::cast)
                    .filter(stack -> !stack.isEmpty())
                    .toList();
        }
        return ingredient instanceof ChemicalStack chemical && !chemical.isEmpty()
                ? List.of(chemical)
                : List.of();
    }

    @Override
    public ResourceLocation typeId() {
        return MekanismRecipeTypes.CHEMICAL;
    }

    @Override public String quantityOverlay(JeiDisplayEntry entry) {
        return ReadableNumber.formatForSlot(entry.exactAmount(), 3, "B");
    }
    @Override public Optional<Component> quantityTooltip(JeiDisplayEntry entry) {
        return Optional.of(Component.translatable("jei.mmcr.machine_recipe.chemical_amount",
                BigDecimal.valueOf(entry.exactAmount(), 3).stripTrailingZeros().toPlainString() + "B"));
    }

    @Override
    public IIngredientType<?> ingredientType() {
        return MekanismJEI.TYPE_CHEMICAL;
    }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        if (!(entry.value() instanceof LoadedChemicalRequirement chemical)) return Optional.empty();
        long amount = entry.declaredValues() ? entry.amount() : chemical.ingredient().amount();
        List<ChemicalStack> stacks = chemicalStacks(chemical.ingredient());
        Object ingredient = entry.role() == RecipeIngredientRole.INPUT ? stacks
                : stacks.isEmpty() ? ChemicalStack.EMPTY : stacks.getFirst();
        return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(), ingredient,
                (int) Math.min(Integer.MAX_VALUE, amount), entry.declaredValues() ? entry.chance() : chemical.io().isInput() ? chemical.consumeChance() : chemical.chance(),
                null, false, amount));
    }

    @Override
    public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
        return Optional.empty();
    }

    private static List<ChemicalStack> chemicalStacks(ChemicalIngredient ingredient) {
        if (ingredient.kind() == ChemicalIngredient.Kind.CHEMICAL) {
            return MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                            ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id()))
                    .map(holder -> List.of(new ChemicalStack(holder, CHEMICAL_RENDER_AMOUNT)))
                    .orElseGet(List::of);
        }
        TagKey<Chemical> tag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id());
        return MekanismAPI.CHEMICAL_REGISTRY.getTag(tag).stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> new ChemicalStack(holder, CHEMICAL_RENDER_AMOUNT))
                .toList();
    }
}

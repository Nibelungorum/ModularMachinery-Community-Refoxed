package cn.howxu.mmcr.compat.mekanism.loaded.jei;

import cn.howxu.mmcr.compat.jei.JeiIngredientAdapter;
import cn.howxu.mmcr.compat.jei.JeiDisplayEntry;
import cn.howxu.mmcr.compat.jei.RecipeIoEntry;
import cn.howxu.mmcr.compat.jei.MachineRecipeCategory;
import cn.howxu.mmcr.util.ReadableNumber;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** Display samples fill the icon; actual JEI slots require an exactly representable native stack.
 * @author howxu <dev@howxu.cn>
 */
public final class ChemicalJeiAdapter implements JeiIngredientAdapter {
    public static final ChemicalJeiAdapter INSTANCE = new ChemicalJeiAdapter();
    private static final int CHEMICAL_RENDER_AMOUNT = 1000;
    private ChemicalJeiAdapter() {}

    @Override public int displayOrder() { return 1; }
    @Override public String quantityText(long amount) { return ReadableNumber.formatForSlot(amount, 3, "B"); }
    @Override public Optional<Component> quantityTooltip(long amount) {
        return Optional.of(Component.translatable("jei.mmcr.machine_recipe.chemical_amount",
                MachineRecipeCategory.chemicalTooltipQuantity(amount)));
    }
    @Override public boolean nativePatternSlot(JeiDisplayEntry entry) {
        return !actualIngredients(entry).isEmpty() && entry.count() <= Integer.MAX_VALUE;
    }
    @Override public List<?> actualIngredients(JeiDisplayEntry entry) {
        if (entry.count() <= 0 || entry.count() > Integer.MAX_VALUE) return List.of();
        var stacks = entry.ingredient() instanceof List<?> list ? list : List.of(entry.ingredient());
        return stacks.stream().map(ChemicalStack.class::cast).filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithAmount(Math.toIntExact(entry.count()))).toList();
    }
    @Override
    public Identifier typeId() {
        return MekanismRecipeTypes.CHEMICAL;
    }

    @Override
    public IIngredientType<?> ingredientType() {
        return MekanismJEI.TYPE_CHEMICAL;
    }

    @Override
    public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        if (!(entry.value() instanceof LoadedChemicalRequirement chemical)) return Optional.empty();
        List<ChemicalStack> stacks = chemicalStacks(chemical.ingredient());
        Object ingredient = entry.role() == RecipeIngredientRole.INPUT ? stacks
                : stacks.isEmpty() ? ChemicalStack.EMPTY : stacks.getFirst();
        return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(), ingredient,
                entry.amount(), entry.chance(), null, false));
    }

    @Override
    public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
        return Optional.empty();
    }

    private static List<ChemicalStack> chemicalStacks(ChemicalIngredient ingredient) {
        if (ingredient.kind() == ChemicalIngredient.Kind.CHEMICAL) {
            return MekanismAPI.CHEMICAL_REGISTRY.get(ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id()))
                    .map(holder -> List.of(new ChemicalStack(holder, CHEMICAL_RENDER_AMOUNT)))
                    .orElseGet(List::of);
        }
        TagKey<Chemical> tag = TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ingredient.id());
        return MekanismAPI.CHEMICAL_REGISTRY.get(tag).stream()
                .flatMap(holders -> holders.stream())
                .map(holder -> new ChemicalStack(holder, CHEMICAL_RENDER_AMOUNT))
                .toList();
    }
}

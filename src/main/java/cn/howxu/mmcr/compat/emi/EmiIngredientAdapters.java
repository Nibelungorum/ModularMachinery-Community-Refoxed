package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ListEmiIngredient;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts owned neutral display snapshots into native EMI resources.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EmiIngredientAdapters {
    private static final String MEKANISM_ADAPTER =
            "cn.howxu.mmcr.compat.mekanism.loaded.MekanismEmiIngredients";

    private EmiIngredientAdapters() {
    }

    /** Copies our native candidates without ListEmiIngredient's shallow wrapper copy or tag normalization. */
    public static EmiIngredient deepCopy(EmiIngredient ingredient) {
        if (ingredient instanceof ListEmiIngredient list) {
            return new ListEmiIngredient(list.getIngredients().stream().map(EmiIngredientAdapters::deepCopy).toList(),
                    list.getAmount()).setChance(list.getChance());
        }
        // Native item/fluid/chemical and Source stacks copy their chance, comparison, and remainder chain.
        return ingredient.copy();
    }

    public static EmiIngredient convert(RecipeDisplayEntry entry) {
        if (entry.amount() <= 0) return EmiStack.EMPTY;
        List<EmiStack> stacks = new ArrayList<>();
        switch (entry.kind()) {
            case ITEM -> {
                for (ItemStack candidate : entry.items()) {
                    EmiStack stack = EmiStack.of(candidate.copy(), entry.amount());
                    if (!stack.isEmpty()) stacks.add(stack);
                }
            }
            case FLUID -> {
                for (FluidStack candidate : entry.fluids()) {
                    FluidStack copy = candidate.copy();
                    if (!copy.isEmpty()) {
                        stacks.add(EmiStack.of(copy.getFluid(), copy.getComponentsPatch(), entry.amount()));
                    }
                }
            }
            case CHEMICAL -> {
                EmiIngredient chemicals = convertChemicals(entry);
                for (EmiStack stack : chemicals.getEmiStacks()) {
                    if (!stack.isEmpty()) stacks.add(stack.copy().setAmount(entry.amount()));
                }
            }
            case SOURCE -> stacks.add(new SourceEmiStack(entry.amount()));
            case TEXT -> { return EmiStack.EMPTY; }
        }
        if (stacks.isEmpty()) return EmiStack.EMPTY;
        if (entry.exactComponents() && !entry.components().isEmpty()) {
            Set<DataComponentType<?>> keys = Set.copyOf(entry.components().values().keySet());
            Comparison comparison = Comparison.compareData(stack -> componentProjection(stack, keys));
            stacks.forEach(stack -> stack.comparison(comparison));
        }
        for (EmiStack stack : stacks) {
            if (entry.io() == IOType.INPUT && entry.chance() == 0F) {
                stack.setRemainder(stack.copy());
            } else {
                stack.setChance(entry.chance());
            }
        }
        // The generic factory normalizes alternatives and can compress them to tags,
        // losing candidate-local comparison, quantity, or remainder state.
        return stacks.size() == 1 ? stacks.getFirst()
                : new ListEmiIngredient(List.copyOf(stacks), entry.amount()).setChance(stacks.getFirst().getChance());
    }

    public static boolean supportsRecipeTree(MachineRecipeData data) {
        boolean hasOutput = false;
        for (RecipeDisplayEntry entry : data.entries()) {
            if (entry.kind() != RecipeDisplayEntry.Kind.SOURCE && !entry.exactComponents()) return false;
            boolean complete = switch (entry.kind()) {
                case ITEM -> !entry.items().isEmpty() && entry.items().stream().noneMatch(ItemStack::isEmpty);
                case FLUID -> !entry.fluids().isEmpty() && entry.fluids().stream().noneMatch(FluidStack::isEmpty);
                case CHEMICAL -> !entry.chemicals().isEmpty();
                case SOURCE -> true;
                case TEXT -> false;
            };
            if (!complete) return false;
            EmiIngredient ingredient = convert(entry);
            if (ingredient.isEmpty()) return false;
            if (entry.kind() == RecipeDisplayEntry.Kind.CHEMICAL
                    && ingredient.getEmiStacks().size() != entry.chemicals().size()) return false;
            if (entry.io() == IOType.OUTPUT && entry.chance() > 0F) hasOutput = true;
        }
        return hasOutput;
    }

    private static Map<DataComponentType<?>, Object> componentProjection(EmiStack stack,
                                                                         Set<DataComponentType<?>> keys) {
        Map<DataComponentType<?>, Object> values = new HashMap<>();
        for (DataComponentType<?> key : keys) values.put(key, stack.get(key));
        return values;
    }

    private static EmiIngredient convertChemicals(RecipeDisplayEntry entry) {
        ModList mods = ModList.get();
        if (mods == null || !mods.isLoaded("emi") || !mods.isLoaded("mekanism")) return EmiStack.EMPTY;
        try {
            return (EmiIngredient) Class.forName(MEKANISM_ADAPTER).getMethod("convert", List.class, long.class)
                    .invoke(null, entry.chemicals(), entry.amount());
        } catch (ReflectiveOperationException | LinkageError exception) {
            MMCR.LOG.warn("Unable to convert Mekanism EMI ingredients", exception);
            return EmiStack.EMPTY;
        }
    }
}

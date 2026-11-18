package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.client.SourceJeiAdapter;
import cn.howxu.mmcr.compat.botania.client.ManaJeiAdapter;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import cn.howxu.mmcr.compat.mekanism.client.ChemicalJeiAdapter;
import net.neoforged.fml.ModList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Registry of JEI adapters keyed by MMCR requirement type ID.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiIngredientAdapterRegistry {
    private static final Map<ResourceLocation, JeiIngredientAdapter> ADAPTERS = new LinkedHashMap<>();

    static {
        registerBuiltIns();
    }

    private JeiIngredientAdapterRegistry() {
    }

    public static synchronized void register(JeiIngredientAdapter adapter) {
        JeiIngredientAdapter previous = ADAPTERS.putIfAbsent(adapter.typeId(), adapter);
        if (previous != null && previous != adapter) {
            throw new IllegalStateException("JEI adapter already registered for " + adapter.typeId());
        }
    }

    public static synchronized Optional<JeiIngredientAdapter> get(ResourceLocation typeId) {
        return Optional.ofNullable(ADAPTERS.get(typeId));
    }

    public static synchronized void registerBuiltIns() {
        if (!ADAPTERS.isEmpty()) return;
        register(new ItemAdapter());
        register(new FluidAdapter());
        var mods = ModList.get();
        if (mods == null || mods.isLoaded("mekanism")) register(new ChemicalJeiAdapter());
        register(new SourceJeiAdapter());
        register(new ManaJeiAdapter());
    }

    static JeiDisplayEntry textEntry(RecipeIoEntry entry) {
        return new JeiDisplayEntry(entry.role(), entry.typeId(), null,
                Component.literal(entry.typeId().toString()), boundedCount(entry.amount()), entry.chance(), null, false, entry.amount());
    }

    static Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
        return get(entry.typeId()).flatMap(adapter -> adapter.display(entry).map(display -> new JeiDisplayEntry(
                display.role(), entry.typeId(), display.ingredientType(), display.ingredient(), display.count(),
                display.chance(), adapter.renderer(entry).orElse(display.renderer()), display.transferable(), display.exactAmount())));
    }

    private static int boundedCount(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }

    private static final class ItemAdapter implements JeiIngredientAdapter {
        @Override
        public ResourceLocation typeId() {
            return ItemRequirement.TYPE.id();
        }

        @Override
        public IIngredientType<?> ingredientType() {
            return VanillaTypes.ITEM_STACK;
        }

        @Override
        public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
            if (!(entry.value() instanceof ItemRequirement item)) return Optional.empty();
            if (entry.role() == RecipeIngredientRole.INPUT && item.item() == null) {
                return Optional.empty();
            }
            if (entry.role() == RecipeIngredientRole.INPUT) {
                List<ItemStack> stacks = safeItems(item.item())
                        .map(stack -> new ItemStack(stack.getItem()))
                        .toList();
                return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(),
                        stacks, entry.declaredValues() ? boundedCount(entry.amount()) : item.count(),
                        entry.declaredValues() ? entry.chance() : item.consumeChance(), null, true));
            }
            ItemStack stack = item.resolvedStack();
            return stack.isEmpty() ? Optional.empty() : Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(),
                    stack.copyWithCount(1), entry.declaredValues() ? boundedCount(entry.amount()) : stack.getCount(),
                    entry.declaredValues() ? entry.chance() : item.chance(), null, false));
        }

        @Override
        public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
            return Optional.empty();
        }
    }

    private static final class FluidAdapter implements JeiIngredientAdapter {
        @Override
        public ResourceLocation typeId() {
            return FluidRequirement.TYPE.id();
        }

        @Override
        public IIngredientType<?> ingredientType() {
            return NeoForgeTypes.FLUID_STACK;
        }

        @Override
        public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
            if (!(entry.value() instanceof FluidRequirement fluid)) return Optional.empty();
            if (entry.role() == RecipeIngredientRole.INPUT && fluid.fluid() == null) {
                return Optional.empty();
            }
            FluidStack stack = entry.role() == RecipeIngredientRole.INPUT
                    ? safeFluids(fluid.fluid()).findFirst().map(fluidStack -> new FluidStack(fluidStack.getFluid(), 1)).orElse(FluidStack.EMPTY)
                    : fluid.stack().copyWithAmount(1);
            return Optional.of(new JeiDisplayEntry(entry.role(), typeId(), ingredientType(),
                    stack, entry.declaredValues() ? boundedCount(entry.amount()) : fluid.io().isInput() ? fluid.amount() : fluid.stack().getAmount(),
                    entry.declaredValues() ? entry.chance() : fluid.io().isInput() ? fluid.consumeChance() : fluid.chance(), null, false));
        }

        @Override
        public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
            return Optional.empty();
        }
    }

    private static Stream<ItemStack> safeItems(Ingredient ingredient) {
        try {
            return Arrays.stream(ingredient.getItems());
        } catch (UnsupportedOperationException ignored) {
            return Stream.empty();
        }
    }

    private static Stream<FluidStack> safeFluids(FluidIngredient ingredient) {
        try {
            return Arrays.stream(ingredient.getStacks());
        } catch (UnsupportedOperationException ignored) {
            return Stream.empty();
        }
    }

}

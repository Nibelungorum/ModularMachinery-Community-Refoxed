package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of JEI adapters keyed by MMCR requirement type ID.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JeiIngredientAdapterRegistry {
    private static final Map<Identifier, JeiIngredientAdapter> ADAPTERS = new LinkedHashMap<>();

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

    public static synchronized Optional<JeiIngredientAdapter> get(Identifier typeId) {
        return Optional.ofNullable(ADAPTERS.get(typeId));
    }

    public static synchronized void registerBuiltIns() {
        if (!ADAPTERS.isEmpty()) return;
        register(new ItemAdapter());
        register(new FluidAdapter());
    }

    static JeiDisplayEntry textEntry(RecipeIoEntry entry) {
        return new JeiDisplayEntry(entry.role(), null,
                Component.literal(entry.typeId().toString()), boundedCount(entry.amount()), false);
    }

    private static int boundedCount(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }

    private static final class ItemAdapter implements JeiIngredientAdapter {
        @Override
        public Identifier typeId() {
            return MMCR.id("item");
        }

        @Override
        public IIngredientType<?> ingredientType() {
            return VanillaTypes.ITEM_STACK;
        }

        @Override
        public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
            if (!(entry.value() instanceof ItemRequirement item)) return Optional.empty();
            if (entry.role() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT && item.item() == null) {
                return Optional.empty();
            }
            ItemStack stack = entry.role() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                    ? item.item().items().findFirst().map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY)
                    : item.resolvedStack();
            return stack.isEmpty() ? Optional.empty() : Optional.of(new JeiDisplayEntry(entry.role(), ingredientType(),
                    stack.copyWithCount(1), boundedCount(entry.amount()), entry.role() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT));
        }

        @Override
        public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
            return Optional.empty();
        }
    }

    private static final class FluidAdapter implements JeiIngredientAdapter {
        @Override
        public Identifier typeId() {
            return MMCR.id("fluid");
        }

        @Override
        public IIngredientType<?> ingredientType() {
            return NeoForgeTypes.FLUID_STACK;
        }

        @Override
        public Optional<JeiDisplayEntry> display(RecipeIoEntry entry) {
            if (!(entry.value() instanceof FluidRequirement fluid)) return Optional.empty();
            if (entry.role() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT && fluid.fluid() == null) {
                return Optional.empty();
            }
            FluidStack stack = entry.role() == mezz.jei.api.recipe.RecipeIngredientRole.INPUT
                    ? fluid.fluid().fluids().findFirst().map(holder -> new FluidStack(holder.value(), 1)).orElse(FluidStack.EMPTY)
                    : fluid.stack().copyWithAmount(1);
            return stack.isEmpty() ? Optional.empty() : Optional.of(new JeiDisplayEntry(entry.role(), ingredientType(),
                    stack, boundedCount(entry.amount()), false));
        }

        @Override
        public Optional<IRecipeTransferHandler<?, ?>> transferHandler() {
            return Optional.empty();
        }
    }
}

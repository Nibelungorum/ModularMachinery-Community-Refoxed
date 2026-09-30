package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.registry.ModUIs;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Transfers JEI item inputs into an item input bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeTransferHandler implements IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> {

    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<MachineRecipeDisplay> recipeType;

    public MachineRecipeTransferHandler(IRecipeTransferHandlerHelper helper, RecipeType<MachineRecipeDisplay> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override
    public Class<? extends ItemBusMenu> getContainerClass() {
        return ItemBusMenu.class;
    }

    @Override
    public Optional<MenuType<ItemBusMenu>> getMenuType() {
        return Optional.of(ModUIs.ITEM_BUS.get());
    }

    @Override
    public RecipeType<MachineRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(ItemBusMenu container, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        List<JeiDisplayEntry> inputs = recipe.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.INPUT)
                .toList();
        for (JeiDisplayEntry entry : inputs) {
            Optional<IRecipeTransferHandler<?, ?>> adapterHandler = JeiIngredientAdapterRegistry
                    .get(entry.typeId()).flatMap(JeiIngredientAdapter::transferHandler);
            if (adapterHandler.isPresent()) {
                @SuppressWarnings("rawtypes")
                IRecipeTransferHandler handler = adapterHandler.get();
                return handler.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
            }
        }
        Optional<JeiDisplayEntry> unsupported = inputs.stream()
                .filter(entry -> entry.ingredientType() != VanillaTypes.ITEM_STACK || !entry.transferable())
                .findFirst();
        if (unsupported.isPresent()) {
            JeiDisplayEntry entry = unsupported.get();
            RecipeIoEntry ioEntry = new RecipeIoEntry(entry.role(), entry.typeId(), entry.ingredient(), entry.count(), entry.chance());
            Component error = JeiIngredientAdapterRegistry.get(entry.typeId())
                    .map(adapter -> adapter.transferError(ioEntry))
                    .orElseGet(() -> Component.translatable("jei.mmcr.transfer.unsupported", entry.typeId()));
            return helper.createUserErrorWithTooltip(error);
        }
        List<JeiDisplayEntry> transferEntries = inputs.stream().filter(JeiDisplayEntry::transferable).toList();
        if (transferEntries.isEmpty()) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.mmcr.transfer.no_item_inputs"));
        }
        if (transferEntries.size() > container.busSlotCount()) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.mmcr.transfer.not_enough_slots"));
        }
        IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> handler = helper.createUnregisteredRecipeTransferHandler(
                helper.createBasicRecipeTransferInfo(
                        ItemBusMenu.class,
                        ModUIs.ITEM_BUS.get(),
                        recipeType,
                        ItemBusMenu.BUS_SLOT_START,
                        container.busSlotCount(),
                        container.playerInventorySlotStart(),
                        ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT));
        IRecipeSlotsView actualCountSlots = helper.createRecipeSlotsView(
                withActualInputCounts(recipeSlots, recipe));
        return handler.transferRecipe(container, recipe, actualCountSlots, player, maxTransfer, doTransfer);
    }

    static List<IRecipeSlotView> withActualInputCounts(
            IRecipeSlotsView recipeSlots,
            MachineRecipeDisplay recipe) {
        List<JeiDisplayEntry> itemEntries = recipe.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.INPUT)
                .filter(JeiDisplayEntry::transferable)
                .filter(entry -> entry.ingredientType() == VanillaTypes.ITEM_STACK)
                .toList();
        int[] itemInputIndex = {0};
        return recipeSlots.getSlotViews().stream()
                .map(slot -> {
                    if (slot.getRole() != RecipeIngredientRole.INPUT
                            || slot.getAllIngredients().noneMatch(ingredient ->
                            ingredient.getIngredient(VanillaTypes.ITEM_STACK).isPresent())
                            || itemInputIndex[0] >= itemEntries.size()) {
                        return slot;
                    }
                    int count = itemEntries.get(itemInputIndex[0]++).count();
                    return new ActualCountSlotView(slot, count);
                })
                .toList();
    }

    private record ActualCountSlotView(IRecipeSlotView delegate, int count) implements IRecipeSlotView {

        @Override
            public Stream<ITypedIngredient<?>> getAllIngredients() {
                return getAllIngredientsList().stream().filter(Objects::nonNull);
            }

            @Override
            public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
                List<@Nullable ITypedIngredient<?>> ingredients = new ArrayList<>(delegate.getAllIngredientsList().size());
                for (ITypedIngredient<?> ingredient : delegate.getAllIngredientsList()) {
                    ingredients.add(ingredient == null ? null : withActualCount(ingredient, count));
                }
                return Collections.unmodifiableList(ingredients);
            }

            @Override
            public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
                return delegate.getDisplayedIngredient().map(ingredient -> withActualCount(ingredient, count));
            }

            @Override
            public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
                return delegate.getDisplayedIngredients().map(ingredient -> withActualCount(ingredient, count));
            }

            @Override
            public Optional<TagKey<?>> getTagKey() {
                return delegate.getTagKey();
            }

            @Override
            public RecipeIngredientRole getRole() {
                return delegate.getRole();
            }

            @Override
            public void drawHighlight(GuiGraphics guiGraphics, int color) {
                delegate.drawHighlight(guiGraphics, color);
            }

            @Override
            public Optional<String> getSlotName() {
                return delegate.getSlotName();
            }
        }

    private static ITypedIngredient<?> withActualCount(ITypedIngredient<?> ingredient, int count) {
        ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(null);
        return stack == null ? ingredient : new ActualItemIngredient(stack.copyWithCount(count));
    }

    private record ActualItemIngredient(ItemStack stack) implements ITypedIngredient<ItemStack> {
        @Override
        public IIngredientType<ItemStack> getType() {
            return VanillaTypes.ITEM_STACK;
        }

        @Override
        public ItemStack getIngredient() {
            return stack;
        }

        @Override
        public ITypedIngredient<ItemStack> normalize(IIngredientHelper<ItemStack> ingredientHelper) {
            ItemStack normalized = ingredientHelper.normalizeIngredient(stack);
            return normalized == stack ? this : new ActualItemIngredient(normalized);
        }
    }
}

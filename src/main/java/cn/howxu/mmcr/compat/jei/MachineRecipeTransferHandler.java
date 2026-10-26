package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.registry.ModUIs;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.transfer.RecipeTransferContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.function.BiFunction;

/**
 * Transfers JEI item inputs into an item input bus.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeTransferHandler implements IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> {

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeType<MachineRecipeDisplay> recipeType;
    private final IIngredientManager ingredientManager;

    public MachineRecipeTransferHandler(IRecipeTransferHandlerHelper helper, IRecipeType<MachineRecipeDisplay> recipeType,
            IIngredientManager ingredientManager) {
        this.helper = helper;
        this.recipeType = recipeType;
        this.ingredientManager = ingredientManager;
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
    public IRecipeType<MachineRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    // JEI still requires the legacy abstract method; keep old callers on the shared transfer logic.
    @Override
    @Deprecated(since = "29.35.0", forRemoval = true)
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(ItemBusMenu container, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        return transferRecipeInternal(container, recipe, recipeSlots,
                (handler, slots) -> handler.transferRecipe(container, recipe, slots, player, maxTransfer, doTransfer));
    }

    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            IRecipeTransferContext<MachineRecipeDisplay, ItemBusMenu> context, boolean doTransfer) {
        return transferRecipeInternal(context.getContainer(), context.getRecipe(), context.getRecipeSlots(),
                (handler, slots) -> handler.transferRecipe(
                        RecipeTransferContext.copyWithRecipeSlots(context, slots), doTransfer));
    }

    private @Nullable IRecipeTransferError transferRecipeInternal(
            ItemBusMenu container, MachineRecipeDisplay recipe, IRecipeSlotsView recipeSlots,
            BiFunction<IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay>, IRecipeSlotsView,
                    @Nullable IRecipeTransferError> transfer) {
        List<JeiDisplayEntry> inputs = recipe.entries().stream()
                .filter(entry -> entry.role() == RecipeIngredientRole.INPUT)
                .toList();
        for (JeiDisplayEntry entry : inputs) {
            Optional<IRecipeTransferHandler<?, ?>> adapterHandler = JeiIngredientAdapterRegistry
                    .get(entry.typeId()).flatMap(JeiIngredientAdapter::transferHandler);
            if (adapterHandler.isPresent()) {
                @SuppressWarnings("unchecked")
                IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> handler =
                        (IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay>) adapterHandler.get();
                return transfer.apply(handler, recipeSlots);
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
                withActualInputCounts(recipeSlots, recipe, ingredientManager));
        return transfer.apply(handler, actualCountSlots);
    }

    static List<IRecipeSlotView> withActualInputCounts(
            IRecipeSlotsView recipeSlots,
            MachineRecipeDisplay recipe, IIngredientManager ingredientManager) {
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
                    return new ActualCountSlotView(slot, count, ingredientManager);
                })
                .toList();
    }

    private record ActualCountSlotView(IRecipeSlotView delegate, int count,
            IIngredientManager ingredientManager) implements IRecipeSlotView {

        @Override
            public Stream<ITypedIngredient<?>> getAllIngredients() {
                return getAllIngredientsList().stream().filter(Objects::nonNull);
            }

            @Override
            public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
                List<@Nullable ITypedIngredient<?>> ingredients = new ArrayList<>(delegate.getAllIngredientsList().size());
                for (ITypedIngredient<?> ingredient : delegate.getAllIngredientsList()) {
                    ingredients.add(ingredient == null ? null : withActualCount(ingredient, count, ingredientManager));
                }
                return Collections.unmodifiableList(ingredients);
            }

            @Override
            public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
                return delegate.getDisplayedIngredient().map(ingredient -> withActualCount(ingredient, count, ingredientManager));
            }

            @Override
            public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
                return delegate.getDisplayedIngredients().map(ingredient -> withActualCount(ingredient, count, ingredientManager));
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
            public void drawHighlight(GuiGraphicsExtractor guiGraphics, int color) {
                delegate.drawHighlight(guiGraphics, color);
            }

            @Override
            public Optional<String> getSlotName() {
                return delegate.getSlotName();
            }
        }

    private static ITypedIngredient<?> withActualCount(ITypedIngredient<?> ingredient, int count,
            IIngredientManager ingredientManager) {
        ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).orElse(null);
        return stack == null ? ingredient : ingredientManager.createTypedIngredient(
                VanillaTypes.ITEM_STACK, stack.copyWithCount(count), false).orElseThrow();
    }
}

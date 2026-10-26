package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.gui.ItemBusScreen;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineRecipeTransferHandlerTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Field holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        holder.set(ModUIs.ITEM_BUS, Holder.direct(new MenuType<ItemBusMenu>(
                (id, inventory) -> new ItemBusMenu(id, inventory), FeatureFlags.VANILLA_SET)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void contextualTransferRestoresCountsAndPreservesCompletion(boolean doTransfer) {
        MachineRecipeDisplay display = MachineRecipeDisplay.from(RecipeTestSupport.create(
                MMCR.id("jei_context_transfer"), MMCR.id("blast_furnace"), 20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 8)), List.of()));
        var recipeType = JeiMachineRecipeTypes.forPool(display.recipePoolId());
        Inventory inventory = new Inventory(null);
        ItemBusMenu menu = new ItemBusMenu(1, inventory);
        ItemBusScreen screen = new ItemBusScreen(menu, inventory, Component.empty());
        IRecipeSlotsView source = () -> List.of(itemSlot(new ItemStack(Items.IRON_INGOT)));
        AtomicReference<RecipeTransferResult> completed = new AtomicReference<>();
        IRecipeTransferContext<MachineRecipeDisplay, ItemBusMenu> context = (IRecipeTransferContext<MachineRecipeDisplay, ItemBusMenu>)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IRecipeTransferContext.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getRecipe" -> display;
                            case "getRecipeType" -> recipeType;
                            case "getContainer" -> menu;
                            case "getRecipeSlots" -> source;
                            case "getTransferId" -> 42;
                            case "isMaxTransfer" -> true;
                            case "getScreen" -> screen;
                            case "getPlayer" -> null;
                            case "completeRecipeTransfer" -> {
                                completed.set((RecipeTransferResult) args[0]);
                                yield null;
                            }
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
        AtomicReference<IRecipeTransferContext<MachineRecipeDisplay, ItemBusMenu>> transferred = new AtomicReference<>();
        IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay> delegate = (IRecipeTransferHandler<ItemBusMenu, MachineRecipeDisplay>)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IRecipeTransferHandler.class},
                        (proxy, method, args) -> {
                            assertThat(method.getName()).isEqualTo("transferRecipe");
                            assertThat(args).hasSize(2);
                            assertThat(args[1]).isEqualTo(doTransfer);
                            transferred.set((IRecipeTransferContext<MachineRecipeDisplay, ItemBusMenu>) args[0]);
                            return null;
                        });
        IRecipeTransferHandlerHelper helper = (IRecipeTransferHandlerHelper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IRecipeTransferHandlerHelper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createBasicRecipeTransferInfo" -> null;
                    case "createUnregisteredRecipeTransferHandler" -> delegate;
                    case "createRecipeSlotsView" -> (IRecipeSlotsView) () -> (List<IRecipeSlotView>) args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        IIngredientManager ingredients = (IIngredientManager) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class},
                (proxy, method, args) -> {
                    assertThat(method.getName()).isEqualTo("createTypedIngredient");
                    assertThat(args[2]).isEqualTo(false);
                    return Optional.of(new TypedItem((ItemStack) args[1]));
                });

        assertThat(new MachineRecipeTransferHandler(helper, recipeType, ingredients).transferRecipe(context, doTransfer)).isNull();
        var actual = transferred.get();
        assertThat(actual).isNotNull();
        assertThat(actual.getScreen()).isSameAs(screen);
        assertThat(actual.getContainer()).isSameAs(menu);
        assertThat(actual.getRecipe()).isSameAs(display);
        assertThat(actual.getRecipeType()).isSameAs(recipeType);
        assertThat(actual.getTransferId()).isEqualTo(context.getTransferId());
        assertThat(actual.isMaxTransfer()).isTrue();
        assertThat(actual.getRecipeSlots().getSlotViews().getFirst().getDisplayedItemStack().orElseThrow().getCount())
                .isEqualTo(8);
        assertThat(source.getSlotViews().getFirst().getDisplayedItemStack().orElseThrow().getCount()).isOne();
        actual.completeRecipeTransfer(RecipeTransferResult.SUCCESS);
        assertThat(completed.get()).isEqualTo(RecipeTransferResult.SUCCESS);
    }

    @Test
    void transferViewRestoresRealCountForAllItemVariants() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_transfer_count"),
                MMCR.id("blast_furnace"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 8)),
                List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);
        assertThat(display.entries()).singleElement().satisfies(entry -> assertThat(entry.transferable()).isTrue());

        IRecipeSlotView placeholder = itemSlot(
                new ItemStack(Items.IRON_INGOT, 1),
                new ItemStack(Items.GOLD_INGOT, 1));
        IRecipeSlotsView source = () -> List.of(placeholder);

        List<IRecipeSlotView> transformed = MachineRecipeTransferHandler
                .withActualInputCounts(source, display, TypedItem::new);

        assertThat(transformed).singleElement().satisfies(slot -> {
            assertThat(slot.getItemStacks().map(ItemStack::getCount).toList()).containsExactly(8, 8);
            assertThat(slot.getDisplayedIngredients().map(ingredient -> ingredient.getItemStack().orElseThrow())
                    .map(ItemStack::getCount).toList()).containsExactly(8);
            assertThat(slot.getTagKey()).isEqualTo(placeholder.getTagKey()).isPresent();
            assertThat(slot.getDisplayedItemStack().orElseThrow().getCount()).isEqualTo(8);
        });
        assertThat(placeholder.getItemStacks().map(ItemStack::getCount).toList()).containsExactly(1, 1);
    }

    @Test
    void transferViewSkipsFluidInputSlotsWhenRestoringItemCounts() {
        MachineRecipe recipe = RecipeTestSupport.create(
                MMCR.id("jei_transfer_mixed_inputs"),
                MMCR.id("blast_furnace"),
                20,
                List.of(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 8)),
                List.of());
        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        IRecipeSlotView fluidSlot = ingredientSlot(new TypedFluid(new FluidStack(Fluids.WATER, 1_000)));
        IRecipeSlotView itemSlot = itemSlot(new ItemStack(Items.IRON_INGOT, 1));
        IRecipeSlotsView source = () -> List.of(fluidSlot, itemSlot);

        List<IRecipeSlotView> transformed = MachineRecipeTransferHandler
                .withActualInputCounts(source, display, TypedItem::new);

        assertThat(transformed.get(0).getIngredients(NeoForgeTypes.FLUID_STACK)
                .map(FluidStack::getAmount).toList())
                .containsExactly(1_000);
        assertThat(transformed.get(1).getItemStacks().map(ItemStack::getCount).toList())
                .containsExactly(8);
        assertThatThrownBy(() -> transformed.get(1).getAllIngredientsList().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static IRecipeSlotView itemSlot(ItemStack... stacks) {
        List<ITypedIngredient<?>> ingredients = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            ingredients.add(new TypedItem(stack));
        }
        return ingredientSlot(ingredients.toArray(ITypedIngredient<?>[]::new));
    }

    private static IRecipeSlotView ingredientSlot(ITypedIngredient<?>... values) {
        List<ITypedIngredient<?>> ingredients = new ArrayList<>(List.of(values));
        return new IRecipeSlotView() {
            @Override
            public Stream<ITypedIngredient<?>> getAllIngredients() {
                return ingredients.stream();
            }

            @Override
            public List<@Nullable ITypedIngredient<?>> getAllIngredientsList() {
                return ingredients;
            }

            @Override
            public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
                return ingredients.stream().findFirst();
            }

            @Override
            public Stream<ITypedIngredient<?>> getDisplayedIngredients() {
                return ingredients.stream().limit(1);
            }

            @Override
            public Optional<TagKey<?>> getTagKey() {
                return Optional.of(TagKey.create(Registries.ITEM, MMCR.id("transfer_inputs")));
            }

            @Override
            public RecipeIngredientRole getRole() {
                return RecipeIngredientRole.INPUT;
            }

            @Override
            public void drawHighlight(GuiGraphics guiGraphics, int color) {
            }

            @Override
            public Optional<String> getSlotName() {
                return Optional.empty();
            }
        };
    }

    private record TypedItem(ItemStack stack) implements ITypedIngredient<ItemStack> {
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
            return normalized == stack ? this : new TypedItem(normalized);
        }
    }

    private record TypedFluid(FluidStack stack) implements ITypedIngredient<FluidStack> {
        @Override
        public IIngredientType<FluidStack> getType() {
            return NeoForgeTypes.FLUID_STACK;
        }

        @Override
        public FluidStack getIngredient() {
            return stack;
        }

        @Override
        public ITypedIngredient<FluidStack> normalize(IIngredientHelper<FluidStack> ingredientHelper) {
            FluidStack normalized = ingredientHelper.normalizeIngredient(stack);
            return normalized == stack ? this : new TypedFluid(normalized);
        }
    }
}

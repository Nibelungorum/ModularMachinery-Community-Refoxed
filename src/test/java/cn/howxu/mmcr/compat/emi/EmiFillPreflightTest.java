package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.DirectionalItemSlot;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** Behavioural checks of the native-fill plan without loading EMI or a client.
 * @author howxu <dev@howxu.cn>
 */
class EmiFillPreflightTest {
    @BeforeAll static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        // Match the existing ItemBusMenuTest fixture; this deferred menu is not bound by bootstrap.
        Class<?> type = ModUIs.ITEM_BUS.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try { holder = type.getDeclaredField("holder"); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(ModUIs.ITEM_BUS, Holder.direct(new MenuType<>((id, inventory) ->
                new ItemBusMenu(id, inventory), FeatureFlags.VANILLA_SET)));
    }

    @Test void shiftAddsOnlyTheBatchesThatStillFit() {
        ItemBusMenu menu = menu();
        MachineRecipeData data = data(input(20, 1F));
        menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT, 40));
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 64));
        int limit = EmiFillPreflight.selectedLimit(menu, data, List.of(new ItemStack(Items.IRON_INGOT, 20)));
        int present = EmiFillPreflight.batchesAlreadyPresent(menu, data);
        assertThat(EmiFillPreflight.additionalBatches(limit, present, Integer.MAX_VALUE)).isEqualTo(1);
        assertThat(EmiFillPreflight.check(menu, data, List.of(new ItemStack(Items.IRON_INGOT, 60)))).isEmpty();
        assertThat(EmiFillPreflight.additionalBatches(limit, 3, Integer.MAX_VALUE)).isZero();
        assertThat(EmiFillPreflight.additionalBatches(limit, 0, 1)).isEqualTo(1);
    }

    @Test void repeatedCandidatesShareOneStockpile() {
        ItemBusMenu menu = menu();
        MachineRecipeData data = data(input(20, 1F), input(20, 1F));
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 60));
        List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 40), new ItemStack(Items.IRON_INGOT, 40));
        assertThat(EmiFillPreflight.selectedLimit(menu, data, desired)).isEqualTo(1);
        assertThat(key(EmiFillPreflight.check(menu, data, desired).orElseThrow())).isEqualTo("emi.not_enough_ingredients");
        assertThat(menu.getSlot(menu.playerInventorySlotStart()).getItem().getCount()).isEqualTo(60);
        assertThat(desired).extracting(ItemStack::getCount).containsExactly(40, 40);
    }

    @Test void catalystQuantityIsFixedAndDoesNotLimitExistingConsumedBatches() {
        ItemBusMenu menu = menu();
        MachineRecipeData data = data(input(20, 0.25F), input(2, 0F));
        menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT, 40));
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 2));
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 20));
        List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 60), new ItemStack(Items.IRON_INGOT, 2));
        assertThat(EmiFillPreflight.batchesAlreadyPresent(menu, data)).isEqualTo(2);
        assertThat(EmiFillPreflight.selectedLimit(menu, data, desired)).isEqualTo(3);
        assertThat(EmiFillPreflight.check(menu, data, desired)).isEmpty();
        assertThat(EmiFillPreflight.check(menu, data,
                List.of(new ItemStack(Items.IRON_INGOT, 60), new ItemStack(Items.IRON_INGOT, 6)))).isPresent();
    }

    @Test void preflightDoesNotClearOrFillLiveSlots() {
        ItemBusMenu menu = menu();
        MachineRecipeData data = data(input(20, 1F));
        menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT, 40));
        menu.getSlot(5).set(new ItemStack(Items.GOLD_INGOT, 7));
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 20));
        List<ItemStack> before = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
        List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 60));
        assertThat(EmiFillPreflight.check(menu, data, desired)).isEmpty();
        for (int i = 0; i < before.size(); i++) assertThat(ItemStack.matches(before.get(i), menu.getSlot(i).getItem())).isTrue();
        assertThat(desired.getFirst().getCount()).isEqualTo(60);
    }

    @Test void clearingNeedsSpaceBeforeIngredientsAreTaken() {
        ItemBusMenu menu = menu();
        for (int i = menu.playerInventorySlotStart(); i < menu.slots.size(); i++) {
            menu.getSlot(i).set(new ItemStack(Items.IRON_INGOT, 64));
        }
        menu.getSlot(5).set(new ItemStack(Items.GOLD_INGOT, 1));
        assertThat(key(EmiFillPreflight.check(menu, data(input(20, 1F)),
                List.of(new ItemStack(Items.IRON_INGOT, 20))).orElseThrow())).isEqualTo("emi.mmcr.transfer.inventory_space");
        assertThat(menu.getSlot(5).getItem().getCount()).isEqualTo(1);
    }

    @Test void oversizedBusStacksAreRejectedInBothClientSlotMappingsWithoutMutation() {
        for (int index : List.of(0, ItemBusMenu.DEFAULT_BUS_SLOT_COUNT - 1)) {
            ItemBusMenu menu = menu();
            MachineRecipeData data = data(input(20, 1F));
            List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 20));
            menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 20));
            menu.getSlot(index).set(new ItemStack(Items.ENDER_PEARL, 16));
            assertThat(EmiFillPreflight.check(menu, data, desired)).isEmpty();
            // SimpleContainer.setItem clamps to a native stack; synchronization can
            // expose the bus's larger stored count, so reproduce it on the held stack.
            menu.getSlot(index).getItem().grow(16);
            List<ItemStack> before = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
            Component error = EmiFillPreflight.support(menu, data).orElseThrow();
            assertThat(key(error)).isEqualTo("emi.mmcr.transfer.oversized_bus_stack");
            Object[] arguments = ((TranslatableContents) error.getContents()).getArgs();
            assertThat(arguments[0]).isEqualTo(index + 1);
            assertThat(arguments[2]).isEqualTo(16);
            assertThat(key(EmiFillPreflight.check(menu, data, desired).orElseThrow()))
                    .isEqualTo("emi.mmcr.transfer.oversized_bus_stack");
            for (int i = 0; i < before.size(); i++) {
                assertThat(ItemStack.matches(before.get(i), menu.getSlot(i).getItem())).isTrue();
            }
            assertThat(menu.getCarried().isEmpty()).isTrue();
            assertThat(desired.getFirst().getCount()).isEqualTo(20);
        }
    }

    @Test void oversizedAndPartialBatchesCannotReportSuccess() {
        ItemBusMenu menu = menu();
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 64));
        MachineRecipeData data = data(input(20, 1F));
        assertThat(key(EmiFillPreflight.check(menu, data,
                List.of(new ItemStack(Items.IRON_INGOT, 100))).orElseThrow())).isEqualTo("emi.mmcr.transfer.slot_capacity");
        assertThat(EmiFillPreflight.check(menu, data, List.of(new ItemStack(Items.IRON_INGOT, 64)))).isPresent();
    }

    @Test void chosenStackHonorsTheDestinationSlotLimitAndPlacementFilter() {
        ItemBusMenu menu = menu();
        menu.slots.set(0, new DirectionalItemSlot(new LongItemStorage(1, 32L,
                stack -> stack.is(Items.IRON_INGOT), null), 0, 0, 0));
        menu.getSlot(menu.playerInventorySlotStart()).set(new ItemStack(Items.IRON_INGOT, 64));
        assertThat(EmiFillPreflight.selectedLimit(menu, data(input(20, 1F)),
                List.of(new ItemStack(Items.IRON_INGOT, 20)))).isEqualTo(1);
        assertThat(EmiFillPreflight.check(menu, data(input(20, 1F)),
                List.of(new ItemStack(Items.IRON_INGOT, 20)))).isEmpty();
        assertThat(key(EmiFillPreflight.check(menu, data(input(20, 1F)),
                List.of(new ItemStack(Items.IRON_INGOT, 40))).orElseThrow())).isEqualTo("emi.mmcr.transfer.slot_capacity");
        menu.slots.set(0, new DirectionalItemSlot(new LongItemStorage(1, 64L,
                stack -> stack.is(Items.GOLD_INGOT), null), 0, 0, 0));
        assertThat(key(EmiFillPreflight.check(menu, data(input(20, 1F)),
                List.of(new ItemStack(Items.IRON_INGOT, 20))).orElseThrow())).isEqualTo("jei.mmcr.transfer.unsupported");
    }

    @Test void missingInputsAndTooManyInputsCannotBeFilled() {
        assertThat(key(EmiFillPreflight.check(menu(), data(), List.of()).orElseThrow()))
                .isEqualTo("jei.mmcr.transfer.no_item_inputs");
        assertThat(key(EmiFillPreflight.check(menu(), data(input(1, 1F), input(1, 1F), input(1, 1F),
                input(1, 1F), input(1, 1F), input(1, 1F), input(1, 1F)), List.of()).orElseThrow()))
                .isEqualTo("jei.mmcr.transfer.not_enough_slots");
    }

    @Test void incrementArithmeticCannotOverflowOrBecomeNegative() {
        assertThat(EmiFillPreflight.additionalBatches(Integer.MAX_VALUE, Integer.MAX_VALUE - 2, Integer.MAX_VALUE)).isEqualTo(2);
        assertThat(EmiFillPreflight.additionalBatches(3, Integer.MAX_VALUE, Integer.MAX_VALUE)).isZero();
        assertThat(EmiFillPreflight.additionalBatches(3, 0, -1)).isZero();
    }

    @Test void cursorAndMissingSlotMappingHaveSpecificFailures() {
        ItemBusMenu menu = menu();
        MachineRecipeData data = data(input(1, 1F));
        menu.setCarried(new ItemStack(Items.GOLD_INGOT));
        assertThat(key(EmiFillPreflight.check(menu, data, List.of(new ItemStack(Items.IRON_INGOT))).orElseThrow()))
                .isEqualTo("emi.mmcr.transfer.cursor_not_empty");
        menu.setCarried(ItemStack.EMPTY);
        assertThat(EmiFillPreflight.check(menu, data, List.of())).isPresent();
    }

    private static ItemBusMenu menu() { return new ItemBusMenu(1, new Inventory(null)); }
    private static ItemRequirement input(int amount, float chance) {
        return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), amount,
                ItemStack.EMPTY, 1F, DataComponentPredicateSet.EMPTY, chance);
    }
    private static MachineRecipeData data(MachineRequirement... inputs) {
        return MachineRecipeData.from(MachineRecipe.fromCanonical(MMCR.id("fill_test"), MMCR.id("test_cube"),
                20, List.of(inputs), List.of(), List.of(), 0, 1, false, false, false, Set.of()), null);
    }
    private static String key(Component component) { return ((TranslatableContents) component.getContents()).getKey(); }
}

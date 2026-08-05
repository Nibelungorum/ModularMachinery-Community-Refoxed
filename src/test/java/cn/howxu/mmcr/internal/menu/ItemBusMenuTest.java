package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ItemBusMenuTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.ITEM_BUS, new MenuType<>(ItemBusMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_menu_includes_item_bus_slots_before_player_inventory() {
        ItemBusMenu clientMenu = new ItemBusMenu(1, emptyInventory());

        assertThat(clientMenu.slots).hasSize(ItemBusMenu.COLS * ItemBusMenu.ROWS + 36);
    }

    @Test
    void item_bus_menu_uses_two_rows_of_three_slots() {
        ItemBusMenu clientMenu = new ItemBusMenu(1, emptyInventory());

        assertThat(ItemBusMenu.COLS).isEqualTo(3);
        assertThat(ItemBusMenu.ROWS).isEqualTo(2);
        assertThat(clientMenu.slots.subList(0, ItemBusMenu.COLS * ItemBusMenu.ROWS)).hasSize(6);
    }

    @Test
    void item_bus_slots_start_one_pixel_further_left() {
        ItemBusMenu clientMenu = new ItemBusMenu(1, emptyInventory());

        assertThat(clientMenu.slots.getFirst().x).isEqualTo(61);
    }

    @Test
    void input_slots_behave_like_container_slots() {
        bindItemComponents(Items.IRON_INGOT);
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        DirectionalItemSlot slot = new DirectionalItemSlot(handler, 0, 0, 0, IOType.INPUT);

        assertThat(slot.mayPlace(Items.IRON_INGOT.getDefaultInstance())).isTrue();
        assertThat(slot.mayPickup(null)).isTrue();
    }

    @Test
    void output_slots_allow_inserting_and_pickup() {
        bindItemComponents(Items.IRON_INGOT);
        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        DirectionalItemSlot slot = new DirectionalItemSlot(handler, 0, 0, 0, IOType.OUTPUT);

        assertThat(slot.mayPlace(Items.IRON_INGOT.getDefaultInstance())).isTrue();
        assertThat(slot.mayPickup(null)).isTrue();
    }

    @Test
    void output_slots_merge_player_insert_before_using_empty_slot() {
        bindItemComponents(Items.IRON_INGOT);
        ItemStackHandler handler = new ItemStackHandler(2);
        handler.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance().copyWithCount(10));
        DirectionalItemSlot first = new DirectionalItemSlot(handler, 0, 0, 0, IOType.OUTPUT);
        DirectionalItemSlot second = new DirectionalItemSlot(handler, 1, 0, 0, IOType.OUTPUT);

        ItemStack stack = Items.IRON_INGOT.getDefaultInstance().copyWithCount(5);
        stack = first.safeInsert(stack);
        if (!stack.isEmpty()) {
            stack = second.safeInsert(stack);
        }

        assertThat(stack.isEmpty()).as("remaining=%s slot0=%s slot1=%s", stack, handler.getStackInSlot(0), handler.getStackInSlot(1)).isTrue();
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(15);
        assertThat(handler.getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    void output_slots_reject_mismatched_insert_like_input_slots() {
        bindItemComponents(Items.IRON_INGOT);
        bindItemComponents(Items.GOLD_INGOT);
        ItemStackHandler handler = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.is(Items.IRON_INGOT);
            }
        };
        DirectionalItemSlot inputSlot = new DirectionalItemSlot(handler, 0, 0, 0, IOType.INPUT);
        DirectionalItemSlot outputSlot = new DirectionalItemSlot(handler, 0, 0, 0, IOType.OUTPUT);

        ItemStack inputRemaining = inputSlot.safeInsert(Items.GOLD_INGOT.getDefaultInstance());
        ItemStack outputRemaining = outputSlot.safeInsert(Items.GOLD_INGOT.getDefaultInstance());

        assertThat(outputRemaining.getCount()).isEqualTo(inputRemaining.getCount());
        assertThat(handler.getStackInSlot(0).isEmpty()).isTrue();
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void bind(Object deferredHolder, MenuType<ItemBusMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }

}

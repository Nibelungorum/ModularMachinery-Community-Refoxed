package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.port.ItemBusSize;
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
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, playerInventory) -> new ItemBusMenu(containerId, playerInventory), FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_menu_includes_item_bus_slots_before_player_inventory() {
        ItemBusMenu clientMenu = new ItemBusMenu(1, emptyInventory());

        assertThat(clientMenu.slots).hasSize(clientMenu.busSlotCount() + 36);
    }

    @Test
    void item_bus_menu_uses_two_rows_of_four_slots_by_default() {
        ItemBusMenu clientMenu = new ItemBusMenu(1, emptyInventory());

        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.NORMAL)).isEqualTo(3);
        assertThat(clientMenu.busRows()).isEqualTo(2);
        assertThat(clientMenu.slots.subList(0, clientMenu.busSlotCount())).hasSize(6);
    }

    @Test
    void bus_grid_uses_size_specific_rows_and_columns() {
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.TINY)).isEqualTo(1);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.TINY)).isEqualTo(1);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.SMALL)).isEqualTo(2);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.SMALL)).isEqualTo(2);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.NORMAL)).isEqualTo(2);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.NORMAL)).isEqualTo(3);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.REINFORCED)).isEqualTo(3);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.REINFORCED)).isEqualTo(3);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.BIG)).isEqualTo(3);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.BIG)).isEqualTo(4);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.HUGE)).isEqualTo(4);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.HUGE)).isEqualTo(4);
        assertThat(ItemBusMenu.rowsForSize(ItemBusSize.LUDICROUS)).isEqualTo(4);
        assertThat(ItemBusMenu.columnsForSize(ItemBusSize.LUDICROUS)).isEqualTo(8);
    }

    @Test
    void image_height_scales_for_large_buses() {
        assertThat(ItemBusMenu.imageHeightForSlots(6)).isEqualTo(166);
        assertThat(ItemBusMenu.imageHeightForSlots(32)).isGreaterThan(166);
    }

    @Test
    void player_inventory_starts_after_actual_bus_slots() {
        assertThat(ItemBusMenu.playerInventorySlotStart(1)).isEqualTo(1);
        assertThat(ItemBusMenu.playerInventorySlotStart(6)).isEqualTo(6);
        assertThat(ItemBusMenu.playerInventorySlotStart(32)).isEqualTo(32);
    }

    @Test
    void item_bus_slot_layouts_use_size_specific_origins() {
        assertSlotLayout(ItemBusSize.TINY, 81, 30, 1, 1);
        assertSlotLayout(ItemBusSize.SMALL, 70, 18, 2, 2);
        assertSlotLayout(ItemBusSize.NORMAL, 61, 18, 2, 3);
        assertSlotLayout(ItemBusSize.REINFORCED, 61, 13, 3, 3);
        assertSlotLayout(ItemBusSize.BIG, 52, 18, 3, 4);
        assertSlotLayout(ItemBusSize.HUGE, 53, 8, 4, 4);
        assertSlotLayout(ItemBusSize.LUDICROUS, 17, 8, 4, 8);
    }

    @Test
    void item_bus_texture_uses_size_specific_inventory_background() {
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.TINY)).isEqualTo("textures/gui/inventory_tiny.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.SMALL)).isEqualTo("textures/gui/inventory_small.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.NORMAL)).isEqualTo("textures/gui/inventory_normal.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.REINFORCED)).isEqualTo("textures/gui/inventory_reinforced.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.BIG)).isEqualTo("textures/gui/inventory_big.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.HUGE)).isEqualTo("textures/gui/inventory_huge.png");
        assertThat(ItemBusMenu.texturePathForSize(ItemBusSize.LUDICROUS)).isEqualTo("textures/gui/inventory_ludicrous.png");
    }

    @Test
    void title_is_hidden_for_reinforced_huge_and_ludicrous_buses() {
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.TINY)).isTrue();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.SMALL)).isTrue();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.NORMAL)).isTrue();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.REINFORCED)).isFalse();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.BIG)).isTrue();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.HUGE)).isFalse();
        assertThat(ItemBusMenu.showsTitleForSize(ItemBusSize.LUDICROUS)).isFalse();
    }

    @Test
    void input_slots_allow_inserting_and_pickup() {
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

    private static void assertSlotLayout(ItemBusSize size, int startX, int startY, int rows, int columns) {
        ItemBusMenu.SlotLayout layout = ItemBusMenu.slotLayoutForSize(size);

        assertThat(layout.startX()).isEqualTo(startX);
        assertThat(layout.startY()).isEqualTo(startY);
        assertThat(layout.rows()).isEqualTo(rows);
        assertThat(layout.columns()).isEqualTo(columns);
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

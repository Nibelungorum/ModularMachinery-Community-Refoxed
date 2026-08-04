package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
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
    void input_slots_allow_inserting_and_manual_pickup() {
        DirectionalItemSlot slot = new DirectionalItemSlot(new ItemStackHandler(1), 0, 0, 0, IOType.INPUT);

        assertThat(slot.mayPlace(ItemStack.EMPTY)).isTrue();
        assertThat(slot.mayPickup(null)).isTrue();
    }

    @Test
    void output_slots_reject_inserting_but_allow_pickup() {
        DirectionalItemSlot slot = new DirectionalItemSlot(new ItemStackHandler(1), 0, 0, 0, IOType.OUTPUT);

        assertThat(slot.mayPlace(ItemStack.EMPTY)).isFalse();
        assertThat(slot.mayPickup(null)).isTrue();
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
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

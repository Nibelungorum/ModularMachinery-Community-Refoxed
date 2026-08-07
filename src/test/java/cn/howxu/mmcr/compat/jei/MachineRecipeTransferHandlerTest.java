package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineRecipeTransferHandlerTest {

    @Test
    void itemBusSlotRangesMatchMenuConstructionOrder() {
        assertThat(ItemBusMenu.BUS_SLOT_START).isZero();
        assertThat(ItemBusMenu.BUS_SLOT_COUNT).isEqualTo(ItemBusMenu.COLS * ItemBusMenu.ROWS);
        assertThat(ItemBusMenu.PLAYER_INVENTORY_SLOT_START).isEqualTo(ItemBusMenu.BUS_SLOT_COUNT);
        assertThat(ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT).isEqualTo(36);
    }
}

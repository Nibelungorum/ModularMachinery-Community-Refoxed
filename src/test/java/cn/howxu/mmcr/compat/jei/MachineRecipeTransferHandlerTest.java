package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineRecipeTransferHandlerTest {

    @Test
    void itemBusSlotRangesMatchMenuConstructionOrder() {
        assertThat(ItemBusMenu.BUS_SLOT_START).isZero();
        assertThat(ItemBusMenu.playerInventorySlotStart(6)).isEqualTo(6);
        assertThat(ItemBusMenu.playerInventorySlotStart(32)).isEqualTo(32);
        assertThat(ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT).isEqualTo(36);
    }
}

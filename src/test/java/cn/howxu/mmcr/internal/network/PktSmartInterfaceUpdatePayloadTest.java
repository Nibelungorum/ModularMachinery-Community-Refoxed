package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.menu.SmartInterfaceMenu;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PktSmartInterfaceUpdatePayloadTest {
    @Test
    void update_is_ignored_when_player_does_not_have_the_target_menu_open() {
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(null, new BlockPos(1, 2, 3), 0, 8F)).isFalse();
    }

    @Test
    void update_requires_matching_menu_position_and_finite_value() {
        BlockPos pos = new BlockPos(1, 2, 3);
        SmartInterfaceMenu menu = SmartInterfaceMenu.clientOpen(1, new net.minecraft.world.entity.player.Inventory(null, null), pos);

        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(menu, pos, 0, 8F)).isTrue();
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(menu, pos, 0, Float.NaN)).isFalse();
        assertThat(PktSmartInterfaceUpdatePayload.canUpdate(menu, pos.above(), 0, 8F)).isFalse();
    }
}

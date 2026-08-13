package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class PktAutoIOConfigPayloadTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, inventory) -> new ItemBusMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
    }

    @Test
    void enabled_set_accepts_port_menu_without_side() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);

        assertThat(PktAutoIOConfigPayload.canUpdate(menu, BlockPos.ZERO, AutoIOAction.SET_ENABLED, null)).isTrue();
    }

    @Test
    void side_set_requires_side() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);

        assertThat(PktAutoIOConfigPayload.canUpdate(menu, BlockPos.ZERO, AutoIOAction.SET_SIDE, null)).isFalse();
        assertThat(PktAutoIOConfigPayload.canUpdate(menu, BlockPos.ZERO, AutoIOAction.SET_SIDE, Direction.EAST)).isTrue();
    }

    @Test
    void wrong_position_is_rejected_for_set_action() {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);

        assertThat(PktAutoIOConfigPayload.canUpdate(menu, new BlockPos(1, 0, 0), AutoIOAction.SET_ENABLED, null)).isFalse();
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

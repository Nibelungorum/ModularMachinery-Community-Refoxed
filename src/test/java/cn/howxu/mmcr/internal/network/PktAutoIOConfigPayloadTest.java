package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

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
    void enabled_set_accepts_port_menu_without_side() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_ENABLED, null)).isTrue();
    }

    @Test
    void side_set_requires_side() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_SIDE, null)).isFalse();
        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_SIDE, Direction.EAST)).isTrue();
    }

    @Test
    void wrong_position_is_rejected_for_set_action() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, new BlockPos(1, 0, 0), AutoIOAction.SET_ENABLED, null)).isFalse();
    }

    @Test
    void still_invalid_menu_is_rejected_before_update() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO) {
            @Override
            public boolean stillValid(Player player) {
                return false;
            }
        };

        assertThat(PktAutoIOConfigPayload.canUpdate(playerWith(menu), BlockPos.ZERO,
                AutoIOAction.SET_ENABLED, null)).isFalse();
    }

    @Test
    void missing_capability_identity_is_rejected_before_update() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, null,
                AutoIOAction.SET_ENABLED, null)).isFalse();
    }

    @Test
    void port_update_requires_the_menu_owner_to_be_the_target_port() {
        ItemInputBusBlockEntity owner = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null, null), owner);
        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(BlockPos.ZERO);

        assertThat(PktAutoIOConfigPayload.ownsMenu(menu, owner)).isTrue();
        assertThat(PktAutoIOConfigPayload.ownsMenu(menu, replacement)).isFalse();
    }

    private static ServerPlayer playerWith(AbstractContainerMenu menu) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        player.containerMenu = menu;
        return player;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
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

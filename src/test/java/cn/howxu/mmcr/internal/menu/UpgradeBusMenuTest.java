package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests standalone upgrade bus menu behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
class UpgradeBusMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.UPGRADE_BUS, new MenuType<>((containerId, playerInventory) ->
                new UpgradeBusMenu(containerId, playerInventory), FeatureFlags.VANILLA_SET));
    }

    @Test
    void moves_items_between_player_inventory_and_upgrade_bus_slots() {
        Inventory playerInventory = new Inventory(null, null);
        ItemStack playerStack = new ItemStack(Items.IRON_INGOT, 4);
        playerStack.set(DataComponents.MAX_STACK_SIZE, 64);
        playerInventory.setItem(9, playerStack);
        UpgradeBusBlockEntity owner = create(UpgradeBusSize.NORMAL, BlockPos.ZERO);
        UpgradeBusMenu menu = new UpgradeBusMenu(1, playerInventory, owner);

        ItemStack movedToBus = menu.quickMoveStack(null, menu.playerInventorySlotStart());

        assertThat(movedToBus.getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(owner.itemStackHandler().getStackInSlot(0).getCount()).isEqualTo(4);
        assertThat(playerInventory.getItem(9).isEmpty()).isTrue();

        ItemStack movedToPlayer = menu.quickMoveStack(null, 0);

        assertThat(movedToPlayer.getItem()).isEqualTo(Items.IRON_INGOT);
        assertThat(owner.itemStackHandler().getStackInSlot(0).isEmpty()).isTrue();
        assertThat(playerInventory.getItem(8).getCount()).isEqualTo(4);
    }

    @Test
    void client_open_payload_contains_position_tier_and_fixed_slot_count() {
        BlockPos pos = new BlockPos(4, 5, 6);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        UpgradeBusMenu.writeClientOpenData(buffer, pos, UpgradeBusSize.ULTIMATE);

        UpgradeBusMenu clientMenu = UpgradeBusMenu.clientOpen(1, new Inventory(null, null), buffer);

        assertThat(clientMenu.owner()).isNull();
        assertThat(clientMenu.pos()).isEqualTo(pos);
        assertThat(clientMenu.size()).isEqualTo(UpgradeBusSize.ULTIMATE);
        assertThat(clientMenu.busSlotCount()).isEqualTo(16);
        assertThat(clientMenu.slots).hasSize(16 + 36);
    }

    private static UpgradeBusBlockEntity create(UpgradeBusSize size, BlockPos pos) {
        String id = "upgrade_bus_" + size.id();
        return new UpgradeBusBlockEntity(size, pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static void bind(Object deferredHolder, MenuType<UpgradeBusMenu> menuType) throws Exception {
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

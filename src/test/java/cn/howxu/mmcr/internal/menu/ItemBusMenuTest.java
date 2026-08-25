package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.internal.port.ItemBusSize;
import io.netty.buffer.Unpooled;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void client_open_uses_server_supplied_ludicrous_layout_without_client_owner() {
        BlockPos pos = new BlockPos(4, 5, 6);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(pos);
        buffer.writeEnum(ItemBusSize.LUDICROUS);
        buffer.writeVarInt(ItemBusSize.LUDICROUS.slots());

        ItemBusMenu clientMenu = ItemBusMenu.clientOpen(1, emptyInventory(), buffer);

        assertThat(clientMenu.owner()).isNull();
        assertThat(clientMenu.pos()).isEqualTo(pos);
        assertThat(clientMenu.busSize()).isEqualTo(ItemBusSize.LUDICROUS);
        assertThat(clientMenu.busSlotCount()).isEqualTo(32);
        assertThat(clientMenu.busRows()).isEqualTo(4);
        assertThat(clientMenu.busColumns()).isEqualTo(8);
        assertThat(clientMenu.playerInventorySlotStart()).isEqualTo(32);
        assertThat(clientMenu.slots).hasSize(32 + ItemBusMenu.PLAYER_INVENTORY_SLOT_COUNT);
    }

    @Test
    void normal_server_layout_round_trips_to_client_without_owner() {
        ItemInputBusBlockEntity serverOwner = new ItemInputBusBlockEntity(
                new BlockPos(1, 2, 3), ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        ItemBusMenu serverMenu = new ItemBusMenu(1, emptyInventory(), serverOwner);

        ItemBusMenu clientMenu = clientMenuFromServer(serverMenu);

        assertLayoutMatches(serverMenu, clientMenu);
    }

    @Test
    void ludicrous_server_layout_round_trips_to_client_without_owner() {
        ItemInputBusBlockEntity serverOwner = new ItemInputBusBlockEntity(
                new BlockPos(4, 5, 6), ModBlocks.BLOCKS.get("item_input_bus_ludicrous").get().defaultBlockState());
        ItemBusMenu serverMenu = new ItemBusMenu(1, emptyInventory(), serverOwner);

        ItemBusMenu clientMenu = clientMenuFromServer(serverMenu);

        assertLayoutMatches(serverMenu, clientMenu);
    }

    @Test
    void client_open_rejects_invalid_server_size_or_slot_count() {
        FriendlyByteBuf invalidSize = new FriendlyByteBuf(Unpooled.buffer());
        invalidSize.writeBlockPos(BlockPos.ZERO);
        invalidSize.writeVarInt(ItemBusSize.values().length);
        invalidSize.writeVarInt(1);

        assertThatThrownBy(() -> ItemBusMenu.clientOpen(1, emptyInventory(), invalidSize))
                .isInstanceOf(IllegalArgumentException.class);

        FriendlyByteBuf invalidCount = new FriendlyByteBuf(Unpooled.buffer());
        invalidCount.writeBlockPos(BlockPos.ZERO);
        invalidCount.writeEnum(ItemBusSize.NORMAL);
        invalidCount.writeVarInt(ItemBusSize.NORMAL.slots() + 1);

        assertThatThrownBy(() -> ItemBusMenu.clientOpen(1, emptyInventory(), invalidCount))
                .isInstanceOf(IllegalArgumentException.class);
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

    private static ItemBusMenu clientMenuFromServer(ItemBusMenu serverMenu) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ItemBusMenu.writeClientOpenData(buffer, serverMenu.pos(), serverMenu.owner());
        return ItemBusMenu.clientOpen(1, emptyInventory(), buffer);
    }

    private static void assertLayoutMatches(ItemBusMenu serverMenu, ItemBusMenu clientMenu) {
        assertThat(clientMenu.owner()).isNull();
        assertThat(clientMenu.pos()).isEqualTo(serverMenu.pos());
        assertThat(clientMenu.busSize()).isEqualTo(serverMenu.busSize());
        assertThat(clientMenu.busSlotCount()).isEqualTo(serverMenu.busSlotCount());
        assertThat(clientMenu.busRows()).isEqualTo(serverMenu.busRows());
        assertThat(clientMenu.busColumns()).isEqualTo(serverMenu.busColumns());
        assertThat(clientMenu.playerInventorySlotStart()).isEqualTo(serverMenu.playerInventorySlotStart());
        assertThat(clientMenu.imageHeight()).isEqualTo(serverMenu.imageHeight());
        assertThat(clientMenu.texturePath()).isEqualTo(serverMenu.texturePath());
        assertThat(clientMenu.slots).hasSize(serverMenu.slots.size());
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

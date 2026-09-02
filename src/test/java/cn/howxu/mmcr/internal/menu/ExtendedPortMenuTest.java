package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.internal.tile.ExtendedCombinedPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedFluidHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ExtendedItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the client-only state of extended port menus.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedPortMenuTest {
    private static final BlockPos POS = new BlockPos(4, 5, 6);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.EXTENDED_ITEM, new MenuType<>((containerId, inventory) ->
                new ExtendedItemMenu(containerId, inventory, POS,
                        PortKinds.EXTENDED_ITEM_INPUT.id(), 2), FeatureFlags.VANILLA_SET));
        bind(ModUIs.EXTENDED_FLUID, new MenuType<>((containerId, inventory) ->
                new ExtendedFluidMenu(containerId, inventory, POS,
                        PortKinds.EXTENDED_FLUID_INPUT.id(), 2), FeatureFlags.VANILLA_SET));
        bind(ModUIs.EXTENDED_COMBINED, new MenuType<>((containerId, inventory) ->
                new ExtendedCombinedMenu(containerId, inventory, POS,
                        PortKinds.EXTENDED_COMBINED_INPUT.id(), 3, 1), FeatureFlags.VANILLA_SET));
    }

    @Test
    void extended_item_client_open_keeps_only_position_kind_and_snapshot() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ExtendedItemMenu.writeClientOpenData(buffer, POS, PortKinds.EXTENDED_ITEM_INPUT.id(), 2);

        ExtendedItemMenu menu = ExtendedItemMenu.clientOpen(1, emptyInventory(), buffer);

        assertThat(menu.owner()).isNull();
        assertThat(menu.pos()).isEqualTo(POS);
        assertThat(menu.kind()).isEqualTo(PortKinds.EXTENDED_ITEM_INPUT.id());
        assertThat(menu.slotCount()).isEqualTo(2);
        assertThat(menu.entries()).isEmpty();
        assertThat(menu.selectedCapabilityId()).isEqualTo(BuiltinCapabilityDefinitions.ITEM_TYPE.id());
    }

    @Test
    void extended_combined_menu_has_no_player_inventory_or_slots() {
        ExtendedCombinedPortBlockEntity owner = new ExtendedCombinedPortBlockEntity(
                POS, ModBlocks.BLOCKS.get(PortKinds.EXTENDED_COMBINED_INPUT.id()).get().defaultBlockState());
        ExtendedCombinedMenu serverMenu = new ExtendedCombinedMenu(1, emptyInventory(), owner);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ExtendedCombinedMenu.writeClientOpenData(buffer, serverMenu.pos(), serverMenu.kind(),
                serverMenu.itemSlotCount(), serverMenu.fluidTankCount());

        ExtendedCombinedMenu clientMenu = ExtendedCombinedMenu.clientOpen(1, emptyInventory(), buffer);

        assertThat(serverMenu.owner()).isSameAs(owner);
        assertThat(clientMenu.owner()).isNull();
        assertThat(clientMenu.pos()).isEqualTo(POS);
        assertThat(clientMenu.itemSlotCount()).isEqualTo(serverMenu.itemSlotCount());
        assertThat(clientMenu.fluidTankCount()).isEqualTo(serverMenu.fluidTankCount());
    }

    @Test
    void extended_client_open_rejects_malformed_kind_and_slot_count() {
        FriendlyByteBuf invalidKind = new FriendlyByteBuf(Unpooled.buffer());
        invalidKind.writeBlockPos(POS);
        invalidKind.writeUtf("not a registered port kind");
        invalidKind.writeVarInt(1);

        assertThatThrownBy(() -> ExtendedItemMenu.clientOpen(1, emptyInventory(), invalidKind))
                .isInstanceOf(IllegalArgumentException.class);

        FriendlyByteBuf invalidCount = new FriendlyByteBuf(Unpooled.buffer());
        invalidCount.writeBlockPos(POS);
        invalidCount.writeUtf(PortKinds.EXTENDED_ITEM_INPUT.id());
        invalidCount.writeVarInt(0);

        assertThatThrownBy(() -> ExtendedItemMenu.clientOpen(1, emptyInventory(), invalidCount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExtendedFluidMenu clientFluidMenu() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ExtendedFluidMenu.writeClientOpenData(buffer, POS, PortKinds.EXTENDED_FLUID_INPUT.id(), 2);
        return ExtendedFluidMenu.clientOpen(1, emptyInventory(), buffer);
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
        Field holder = deferredHolder.getClass().getDeclaredField("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(menuType));
    }
}

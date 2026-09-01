package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests ordinary combined menu slot binding and fluid layout data.
 *
 * @author howxu <dev@howxu.cn>
 */
class CombinedPortMenuTest {
    private static final BlockPos POS = new BlockPos(7, 8, 9);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.COMBINED, new MenuType<>((containerId, inventory) ->
                new CombinedPortMenu(containerId, inventory, POS,
                        PortKinds.COMBINED_INPUT.id(), 12, 2), FeatureFlags.VANILLA_SET));
    }

    @Test
    void ordinary_combined_menu_binds_item_slots_and_exposes_storage() {
        CombinedPortBlockEntity owner = new CombinedPortBlockEntity(
                POS, ModBlocks.BLOCKS.get("combined_input_reinforced").get().defaultBlockState());
        CombinedPortMenu serverMenu = new CombinedPortMenu(1, emptyInventory(), owner);

        assertThat(serverMenu.itemSlotCount()).isEqualTo(12);
        assertThat(serverMenu.fluidTankCount()).isEqualTo(2);
        assertThat(serverMenu.getSlot(0)).isInstanceOf(DirectionalItemSlot.class);
        assertThat(serverMenu.playerInventorySlotStart()).isEqualTo(12);
        assertThat(serverMenu.slots).hasSize(12 + 36);
    }

    @Test
    void combined_client_open_has_no_server_owner_and_accepts_storage_snapshot() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        CombinedPortMenu.writeClientOpenData(buffer, POS, "combined_input_reinforced", 12, 2);
        CombinedPortMenu clientMenu = CombinedPortMenu.clientOpen(1, emptyInventory(), buffer);

        CombinedPortBlockEntity clientPort = new CombinedPortBlockEntity(
                POS, ModBlocks.BLOCKS.get("combined_input_reinforced").get().defaultBlockState());
        clientMenu.applySnapshot(new PktPortStorageSyncPayload(POS,
                "combined_input_reinforced", List.of()), clientPort);

        assertThat(clientMenu.owner()).isNull();
        assertThat(clientMenu.pos()).isEqualTo(POS);
        assertThat(clientMenu.itemEntries()).hasSize(12);
        assertThat(clientMenu.fluidEntries()).hasSize(2);
        assertThat(clientMenu.displayEntries()).hasSize(14);
        assertThat(clientMenu.slots).hasSize(12 + 36);
    }

    @Test
    void combined_client_open_rejects_invalid_counts() {
        FriendlyByteBuf invalidItemCount = new FriendlyByteBuf(Unpooled.buffer());
        invalidItemCount.writeBlockPos(POS);
        invalidItemCount.writeUtf(PortKinds.COMBINED_INPUT.id());
        invalidItemCount.writeVarInt(0);
        invalidItemCount.writeVarInt(1);

        assertThatThrownBy(() -> CombinedPortMenu.clientOpen(1, emptyInventory(), invalidItemCount))
                .isInstanceOf(IllegalArgumentException.class);
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

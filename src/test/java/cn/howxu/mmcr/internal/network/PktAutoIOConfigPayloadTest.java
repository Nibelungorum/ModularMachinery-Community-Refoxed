package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.compat.mekanism.MekanismBridgeBootstrap;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortMenu;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortMenu;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class PktAutoIOConfigPayloadTest {

    private static MekanismBridge currentBridge() {
        return MekanismBridge.get();
    }

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        MekanismBridgeBootstrap.installForTesting(new LoadedMekanismBridge());
        TestBootstrap.bootstrap();
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, inventory) -> new ItemBusMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
        bind(ModUIs.CHEMICAL_PORT, new MenuType<>((containerId, inventory) ->
                new ChemicalPortMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
        bind(ModUIs.HEAT_PORT, new MenuType<>((containerId, inventory) ->
                new HeatPortMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
    }

    @AfterAll
    static void resetBridge() {
        MekanismBridgeBootstrap.resetForTesting();
    }

    @Test
    void enabled_set_accepts_port_menu_without_side() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_ENABLED, null)).isTrue();
    }

    @Test
    void enabled_set_accepts_loaded_chemical_and_heat_port_menus() throws Exception {
        ChemicalPortMenu chemicalMenu = new ChemicalPortMenu(1, testInventory(), BlockPos.ZERO);
        HeatPortMenu heatMenu = new HeatPortMenu(1, testInventory(), BlockPos.ZERO);

        assertThat(MekanismBridge.get().isPortMenuAt(chemicalMenu, BlockPos.ZERO, null)).isTrue();
        assertThat(MekanismBridge.get().isPortMenuAt(heatMenu, BlockPos.ZERO, null)).isTrue();
        assertThat(MekanismBridge.get().capabilityIdForMenu(chemicalMenu))
                .isEqualTo(MekanismRecipeTypes.CHEMICAL);
        assertThat(MekanismBridge.get().capabilityIdForMenu(heatMenu))
                .isEqualTo(MekanismRecipeTypes.HEAT);
    }

    @Test
    void unavailable_bridge_rejects_non_port_menus_without_loading_loaded_classes() throws Exception {
        MekanismBridge previous = currentBridge();
        try {
            MekanismBridgeBootstrap.installForTesting(MekanismBridgeBootstrap.selectForTesting(false));
            AbstractContainerMenu nonPortMenu = new AbstractMachineMenu(null, 0) {
                @Override public boolean stillValid(Player player) { return true; }
                @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
                    return net.minecraft.world.item.ItemStack.EMPTY;
                }
            };
            ServerPlayer player = playerWith(nonPortMenu);

            assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO,
                    AutoIOAction.SET_ENABLED, null)).isFalse();
            assertThat(MekanismBridge.get().isPortMenuAt(nonPortMenu, BlockPos.ZERO, null)).isFalse();
            assertThat(PktAutoIOConfigPayload.ownsMenu(nonPortMenu, null)).isFalse();
        } catch (Throwable failure) {
            throw failure;
        } finally {
            MekanismBridgeBootstrap.installForTesting(previous);
            MekanismBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void side_set_requires_side() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_SIDE, null)).isFalse();
        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, AutoIOAction.SET_SIDE, Direction.EAST)).isTrue();
    }

    @Test
    void wrong_position_is_rejected_for_set_action() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, new BlockPos(1, 0, 0), AutoIOAction.SET_ENABLED, null)).isFalse();
    }

    @Test
    void still_invalid_menu_is_rejected_before_update() throws Exception {
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), BlockPos.ZERO) {
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
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), BlockPos.ZERO);
        ServerPlayer player = playerWith(menu);

        assertThat(PktAutoIOConfigPayload.canUpdate(player, BlockPos.ZERO, null,
                AutoIOAction.SET_ENABLED, null)).isFalse();
    }

    @Test
    void port_update_requires_the_menu_owner_to_be_the_target_port() {
        ItemInputBusBlockEntity owner = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemBusMenu menu = new ItemBusMenu(1, new Inventory(null), owner);
        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(BlockPos.ZERO);

        assertThat(PktAutoIOConfigPayload.ownsMenu(menu, owner)).isTrue();
        assertThat(PktAutoIOConfigPayload.ownsMenu(menu, replacement)).isFalse();
    }

    private static ServerPlayer playerWith(AbstractContainerMenu menu) throws Exception {
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        player.containerMenu = menu;
        return player;
    }

    private static Inventory testInventory() throws Exception {
        Player player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        return new Inventory(player);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void bind(Object deferredHolder, MenuType<?> menuType) throws Exception {
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

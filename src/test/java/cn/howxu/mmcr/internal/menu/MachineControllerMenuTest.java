package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final machine controller menu synchronization boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.MACHINE_CONTROLLER,
                new MenuType<>((containerId, inventory) -> new MachineControllerMenu(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_menu_applies_machine_state_and_connected_host_from_the_final_payload() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));

        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(3, 4, 5), "mmcr:recipe", true, true,
                List.of("mmcr:steel"), true, "mmcr:locked_recipe", "mmcr:test_cube", 2, 3, true,
                "mmcr:host", CraftingStatus.Status.CRAFTING, "", null, true, false,
                4, 20, 6, 8, true, 2, 1, 2, 3, 100, 1000,
                 new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 250), FluidStack.EMPTY, Map.of()));

        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.hasActiveRecipe()).isTrue();
        assertThat(menu.machineId()).isEqualTo(MMCR.id("test_cube"));
        assertThat(menu.lockedRecipeId()).isEqualTo("mmcr:locked_recipe");
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.currentParallelism()).isEqualTo(6);
        assertThat(menu.maxParallelism()).isEqualTo(8);
        assertThat(menu.factoryThreadCount()).isEqualTo(2);
        assertThat(menu.installedModuleCount()).isEqualTo(3);
        assertThat(menu.primaryFluid().getAmount()).isEqualTo(250);
    }

    @Test
    void client_menu_keeps_parallel_controller_count_from_payload_after_data_slot_update() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(3, 4, 5), "mmcr:recipe", true, true,
                List.of(), false, "", "mmcr:test_cube", 0, 0, false, "",
                CraftingStatus.Status.CRAFTING, "", null, true, false,
                1, 20, 4, 4, false, 0, 0, 1, 4, 0, 0,
                 FluidStack.EMPTY, FluidStack.EMPTY, Map.of()));

        menu.setData(6, 0);

        assertThat(menu.parallelControllerCount()).isEqualTo(1);
    }

    @Test
    void client_open_round_trips_role_and_machine_identity() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        MachineControllerMenu.writeClientOpenData(buffer, new BlockPos(7, 8, 9),
                MMCR.id("test_cube"), MMCR.id("host"), 1, true, 4);

        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null), buffer);

        assertThat(menu.controllerPos()).isEqualTo(new BlockPos(7, 8, 9));
        assertThat(menu.machineId()).isEqualTo(MMCR.id("test_cube"));
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.isHostController()).isTrue();
        assertThat(menu.isFormed()).isTrue();
        assertThat(menu.installedModuleCount()).isEqualTo(4);
        buffer.release();
    }

    @Test
    void module_menu_state_keeps_role_and_connected_host_identity_across_payload_updates() {
        MachineControllerMenu menu = MachineControllerMenu.clientOpen(1, new Inventory(null, null),
                menuBuffer(new BlockPos(7, 8, 9), MMCR.id("module"), MMCR.id("host"), 2, true, 1));

        assertThat(menu.isModuleController()).isTrue();
        assertThat(menu.isHostController()).isFalse();
        assertThat(menu.connectedHostId()).hasValue(MMCR.id("host"));
        assertThat(menu.installedModuleCount()).isEqualTo(1);
        assertThat(MachineControllerMenu.resolvedControllerRole(0, 2, 0)).isEqualTo(2);

        menu.applyClientSnapshot(new PktMachineStatePayload(new BlockPos(7, 8, 9), "", true, false,
                List.of(), false, "", "mmcr:module", 2, 0, false, "",
                CraftingStatus.Status.IDLE, "", null, true, false,
                0, 0, 0, 1, false, 0, 0, 0, 0, 0, 0,
                 FluidStack.EMPTY, FluidStack.EMPTY, Map.of()));

        assertThat(menu.isModuleController()).isTrue();
        assertThat(menu.connectedHostId()).isEmpty();
        assertThat(menu.installedModuleCount()).isZero();
    }

    private static RegistryFriendlyByteBuf menuBuffer(BlockPos pos, Identifier machineId,
                                                      Identifier connectedHostId, int role,
                                                      boolean formed, int installedModules) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        MachineControllerMenu.writeClientOpenData(buffer, pos, machineId, connectedHostId, role, formed,
                installedModules);
        return buffer;
    }

    private static void bind(Object deferredHolder, MenuType<MachineControllerMenu> menuType) throws Exception {
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

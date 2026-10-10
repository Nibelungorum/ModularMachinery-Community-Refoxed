package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the final factory controller menu boundary.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryControllerMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void selected_thread_falls_back_to_the_base_lane_when_removed() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(snapshot(0, 1));
        menu.selectThread(1);
        assertThat(menu.selectedThreadIndex()).isEqualTo(1);

        menu.applySnapshot(snapshot(0));
        assertThat(menu.selectedThreadIndex()).isZero();
    }

    @Test
    void empty_snapshot_keeps_the_base_thread_visible() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));

        menu.applySnapshot(FactorySnapshot.empty());

        assertThat(menu.selectedThread()).isEqualTo(FactoryRuntime.ThreadSnapshot.idleBase());
    }

    @Test
    void current_parallelism_uses_the_selected_active_thread() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 2, 24L, false,
                List.of(activeThread(0, 12), activeThread(1, 8)), "Factory", 0, null, List.of(), 0, 1));

        menu.selectThread(1);

        assertThat(menu.currentParallelism()).isEqualTo(8);
        assertThat(menu.maxParallelism()).isEqualTo(24);
    }

    @Test
    void inactive_thread_reports_zero_parallelism_and_failure_is_exposed() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
         menu.applySnapshot(new FactorySnapshot(true, false, List.of(), 1, 0, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, true, false, false, "", 0, 0, 1,
                        "gui.mmcr.controller.failure.missing_input")),
                "Factory", 0, null, List.of(), 0, 1));

        assertThat(menu.currentParallelism()).isZero();
        assertThat(menu.selectedThread().lastFailureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_input");
    }

    @Test
    void player_inventory_is_shifted_right_of_factory_thread_list() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));

        assertThat(menu.slots.getFirst().x).isEqualTo(112);
        assertThat(menu.slots.getFirst().y).isEqualTo(132);
        assertThat(menu.slots.get(27).x).isEqualTo(112);
        assertThat(menu.slots.get(27).y).isEqualTo(190);
    }

    @Test
    void inventory_policy_preserves_local_lane_selection_and_slot_identity() {
        var menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(snapshot(0, 1));
        menu.selectThread(1);
        var slots = List.copyOf(menu.slots);
        var opening = menu.uiOpenData();
        menu.setPlayerInventoryVisible(false);
        assertThat(menu.selectedThreadIndex()).isEqualTo(1);
        assertThat(menu.uiOpenData()).isSameAs(opening);
        assertThat(menu.uiServerSession()).isNull();
        assertThat(menu.slots).containsExactlyElementsOf(slots).allMatch(slot -> !slot.isActive());
        menu.setPlayerInventoryVisible(true);
        assertThat(menu.selectedThreadIndex()).isEqualTo(1);
        assertThat(menu.slots).allMatch(slot -> slot.isActive());
    }

    @Test
    void matched_stage_accessor_reads_from_snapshot() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 2, 24L, false,
                List.of(activeThread(0, 12), activeThread(1, 8)), "Factory", 0, null, List.of(), 4, 10));

        assertThat(menu.matchedStage()).isEqualTo(4);
        assertThat(menu.stageCount()).isEqualTo(10);
    }

    @Test
    void factory_menu_exposes_the_selected_and_supported_recipe_pools() {
        var machineId = MMCR.id("factory_menu_recipe_pool_machine");
        var firstPool = MMCR.id("factory_menu_recipe_pool_first");
        var secondPool = MMCR.id("factory_menu_recipe_pool_second");
        MachineRegistry.replaceClientRecipePools(java.util.Map.of(machineId, List.of(firstPool, secondPool)));
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(true, false, List.of(), 1, 0, 1L, false,
                List.of(FactoryRuntime.ThreadSnapshot.idleBase()), "Factory", 0, null, List.of(), 0, 1,
                machineId.toString(), secondPool.toString()));

        assertThat(menu.machineId()).isEqualTo(machineId);
        assertThat(menu.currentRecipePoolId()).isEqualTo(secondPool);
        assertThat(menu.recipePoolIds()).containsExactly(firstPool, secondPool);
        MachineRegistry.clearClientRecipePools();
    }

    private static FactorySnapshot snapshot(int... indexes) {
         return new FactorySnapshot(true, false, List.of(), indexes.length, 0, 1L, false,
                Arrays.stream(indexes).mapToObj(index -> idleThread(index)).toList(),
                "", 0, null, List.of(), 0, 1);
    }

    private static FactoryRuntime.ThreadSnapshot activeThread(int index, int parallelism) {
        return new FactoryRuntime.ThreadSnapshot(index, index == 0, false, true,
                "mmcr:recipe_" + index, 1, 20, parallelism, "");
    }

    private static FactoryRuntime.ThreadSnapshot idleThread(int index) {
        return new FactoryRuntime.ThreadSnapshot(index, index == 0, false, false,
                "", 0, 0, 1, "");
    }

    private static void bind(Object deferredHolder, MenuType<FactoryControllerMenu> menuType) throws Exception {
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

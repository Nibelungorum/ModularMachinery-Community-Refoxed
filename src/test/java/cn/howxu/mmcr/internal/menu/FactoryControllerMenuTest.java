package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
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
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(snapshot(0, 1));
        menu.selectThread(1);
        assertThat(menu.selectedThreadIndex()).isEqualTo(1);

        menu.applySnapshot(snapshot(0));
        assertThat(menu.selectedThreadIndex()).isZero();
    }

    @Test
    void empty_snapshot_keeps_the_base_thread_visible() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));

        menu.applySnapshot(FactorySnapshot.empty());

        assertThat(menu.selectedThread()).isEqualTo(FactoryRuntime.ThreadSnapshot.idleBase());
    }

    @Test
    void current_parallelism_uses_the_selected_active_thread() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 2, 24L, false,
                List.of(activeThread(0, 12), activeThread(1, 8)), "Factory", 0, null, List.of()));

        menu.selectThread(1);

        assertThat(menu.currentParallelism()).isEqualTo(8);
        assertThat(menu.maxParallelism()).isEqualTo(24);
    }

    @Test
    void selected_thread_exposes_only_its_own_recipe_lock() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
         menu.applySnapshot(new FactorySnapshot(true, false, List.of(), 2, 0, 1L, false,
                List.of(lockedThread(0, false, ""), lockedThread(1, true, "mmcr:locked")),
                "Factory", 0, null, List.of()));

        assertThat(menu.selectedRecipeLocked()).isFalse();
        menu.selectThread(1);
        assertThat(menu.selectedRecipeLocked()).isTrue();
        assertThat(menu.selectedLockedRecipeId()).isEqualTo("mmcr:locked");
    }

    @Test
    void inactive_thread_reports_zero_parallelism_and_failure_is_exposed() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
         menu.applySnapshot(new FactorySnapshot(true, false, List.of(), 1, 0, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, true, false, false, "", 0, 0, 1,
                        "gui.mmcr.controller.failure.missing_input", false, "")),
                "Factory", 0, null, List.of()));

        assertThat(menu.currentParallelism()).isZero();
        assertThat(menu.selectedThread().lastFailureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_input");
    }

    @Test
    void player_inventory_is_shifted_right_of_factory_thread_list() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));

        assertThat(menu.slots.getFirst().x).isEqualTo(112);
        assertThat(menu.slots.getFirst().y).isEqualTo(131);
        assertThat(menu.slots.get(27).x).isEqualTo(112);
        assertThat(menu.slots.get(27).y).isEqualTo(189);
    }

    private static FactorySnapshot snapshot(int... indexes) {
         return new FactorySnapshot(true, false, List.of(), indexes.length, 0, 1L, false,
                java.util.Arrays.stream(indexes).mapToObj(index -> lockedThread(index, false, "")).toList(),
                "", 0, null, List.of());
    }

    private static FactoryRuntime.ThreadSnapshot activeThread(int index, int parallelism) {
        return new FactoryRuntime.ThreadSnapshot(index, index == 0, false, true,
                "mmcr:recipe_" + index, 1, 20, parallelism, "", false, "");
    }

    private static FactoryRuntime.ThreadSnapshot lockedThread(int index, boolean locked, String recipeId) {
        return new FactoryRuntime.ThreadSnapshot(index, index == 0, false, false,
                "", 0, 0, 1, "", locked, recipeId);
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

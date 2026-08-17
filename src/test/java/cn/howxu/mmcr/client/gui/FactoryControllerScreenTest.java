package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryControllerScreenTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        cn.howxu.mmcr.test.TestBootstrap.bootstrap();
        bind(cn.howxu.mmcr.registry.ModUIs.FACTORY_CONTROLLER,
                new MenuType<>(FactoryControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void recipe_lock_button_loses_focus_after_click() {
        Button button = Button.builder(Component.empty(), ignored -> {}).bounds(0, 0, 20, 20).build();
        button.setFocused(true);

        FactoryControllerScreen.clearRecipeLockButtonFocus(button);

        assertThat(button.isFocused()).isFalse();
    }

    @Test
    void locked_selected_and_progress_overlays_draw_in_priority_order() {
        FactoryRecipeScheduler.ThreadSnapshot thread = new FactoryRecipeScheduler.ThreadSnapshot(
                1, false, false, true, "mmcr:active", 5, 10, 1, "", true, "mmcr:active");

        assertThat(FactoryControllerScreen.threadOverlayColors(thread, 1))
                .containsExactly(FactoryControllerScreen.PROGRESS_THREAD_OVERLAY,
                        FactoryControllerScreen.SELECTED_THREAD_OVERLAY,
                        FactoryControllerScreen.LOCKED_THREAD_OVERLAY);
    }

    @Test
    void parallel_label_uses_selected_thread_parallelism() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 1, 2, 16, 16,
                "Factory", 0, List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 16),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, false, "", 0, 0, 1))));

        menu.selectThread(1);
        assertThat(FactoryControllerScreen.selectedParallelism(menu)).isZero();

        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 2, 2, 24, 16,
                "Factory", 0, List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 16),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, true, "mmcr:second", 4, 20, 8))));
        assertThat(FactoryControllerScreen.selectedParallelism(menu)).isEqualTo(8);
    }

    private static void bind(Object deferredHolder, MenuType<FactoryControllerMenu> menuType) throws Exception {
        Class<?> type = deferredHolder.getClass();
        java.lang.reflect.Field holder = null;
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

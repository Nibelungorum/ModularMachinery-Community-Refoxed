package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Factory controller screen behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryControllerScreenTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
    }

    @Test
    void active_selected_thread_hides_aggregate_last_failure() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 1, 2, 1, 1, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, true, false, true, "mmcr:recipe", 1, 20,
                        1, "", false, ""),
                        new FactoryRuntime.ThreadSnapshot(1, false, false, false, "", 0, 0, 1,
                                "", false, "")),
                "Factory", 0, new cn.howxu.mmcr.api.capability.status.ExecutionStatus(
                        cn.howxu.mmcr.MMCR.id("failure"),
                        cn.howxu.mmcr.api.capability.status.StatusSeverity.BLOCKED,
                        cn.howxu.mmcr.MMCR.id("crafting_runtime"),
                        java.util.Map.of("reason", "insufficient_resource")), List.of()));

        assertThat(FactoryControllerScreen.selectedFailureUnloc(menu)).isEmpty();
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

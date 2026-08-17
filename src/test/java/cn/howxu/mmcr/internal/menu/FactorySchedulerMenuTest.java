package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FactorySchedulerMenuTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_SCHEDULER, new MenuType<>((containerId, playerInventory) -> new FactorySchedulerMenu(containerId, playerInventory), FeatureFlags.VANILLA_SET));
        bindItemComponents(Items.IRON_INGOT);
    }

    @Test
    void scheduler_slot_accepts_only_thread_dispersers() {
        FactorySchedulerMenu menu = new FactorySchedulerMenu(1, emptyInventory());

        assertThat(menu.slots.get(0).mayPlace(new ItemStack(ModItems.THREAD_DISPERSER.get()))).isTrue();
        assertThat(menu.slots.get(0).mayPlace(Items.IRON_INGOT.getDefaultInstance())).isFalse();
    }

    private static Inventory emptyInventory() {
        return new Inventory(null, null);
    }

    private static void bindItemComponents(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
    }

    private static void bind(Object deferredHolder, MenuType<FactorySchedulerMenu> menuType) throws Exception {
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

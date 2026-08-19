package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.neoforged.neoforge.network.IContainerFactory;

import java.lang.reflect.Field;

import io.netty.buffer.Unpooled;
import static org.assertj.core.api.Assertions.assertThat;

class SmartInterfaceMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.SMART_INTERFACE, new MenuType<>((IContainerFactory<SmartInterfaceMenu>) SmartInterfaceMenu::clientOpen,
                FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_open_reads_only_the_interface_position() {
        BlockPos pos = new BlockPos(4, 5, 6);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(pos);

        SmartInterfaceMenu menu = SmartInterfaceMenu.clientOpen(1, new Inventory(null, null), buffer);

        assertThat(menu.pos()).isEqualTo(pos);
    }

    private static void bind(Object deferredHolder, MenuType<SmartInterfaceMenu> menuType) throws Exception {
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

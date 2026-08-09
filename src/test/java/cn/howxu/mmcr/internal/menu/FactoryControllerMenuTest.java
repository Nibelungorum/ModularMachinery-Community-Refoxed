package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactoryControllerMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        cn.howxu.mmcr.test.TestBootstrap.bootstrap();
        bind(cn.howxu.mmcr.registry.ModUIs.FACTORY_CONTROLLER,
                new MenuType<>(FactoryControllerMenu::clientOpen, FeatureFlags.VANILLA_SET));
    }

    @Test
    void client_selection_defaults_to_zero_and_falls_back_when_removed() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null), bufferAt(BlockPos.ZERO));
        menu.applySnapshot(snapshot(0, 1));
        menu.selectThread(1);
        assertThat(menu.selectedThread().index()).isEqualTo(1);

        menu.applySnapshot(snapshot(0));
        assertThat(menu.selectedThread().index()).isZero();
    }

    @Test
    void empty_snapshot_keeps_thread_zero_visible() {
        FactoryControllerSnapshot snapshot = FactoryControllerSnapshot.empty(BlockPos.ZERO);

        assertThat(snapshot.threadCount()).isEqualTo(1);
        assertThat(snapshot.threads()).containsExactly(FactoryRecipeScheduler.ThreadSnapshot.idleBase());
    }

    @Test
    void current_parallelism_uses_the_selected_active_thread() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 2, 2, 32, 16,
                "Factory", 0, java.util.List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 16),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, true, "mmcr:second", 4, 20, 8))));

        menu.selectThread(1);

        assertThat(menu.currentParallelism()).isEqualTo(8);
        assertThat(menu.maxParallelism()).isEqualTo(16);
    }

    @Test
    void current_parallelism_is_zero_for_selected_idle_thread() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 1, 2, 16, 16,
                "Factory", 0, java.util.List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 16),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, false, "", 0, 0, 1))));

        menu.selectThread(1);

        assertThat(menu.currentParallelism()).isZero();
        assertThat(menu.maxParallelism()).isEqualTo(16);
    }

    @Test
    void player_inventory_is_shifted_right_of_factory_thread_list() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null), bufferAt(BlockPos.ZERO));

        assertThat(menu.slots.getFirst().x).isEqualTo(112);
        assertThat(menu.slots.getFirst().y).isEqualTo(131);
        assertThat(menu.slots.get(27).x).isEqualTo(112);
        assertThat(menu.slots.get(27).y).isEqualTo(189);
    }

    private static FactoryControllerSnapshot snapshot(int... indexes) {
        return new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, indexes.length, 0, 1,
                java.util.Arrays.stream(indexes).mapToObj(index -> new FactoryRecipeScheduler.ThreadSnapshot(
                        index, index == 0, false, false, "", 0, 0, 1)).toList());
    }

    private static net.minecraft.network.FriendlyByteBuf bufferAt(BlockPos pos) {
        io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(raw);
        buffer.writeBlockPos(pos);
        return buffer;
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

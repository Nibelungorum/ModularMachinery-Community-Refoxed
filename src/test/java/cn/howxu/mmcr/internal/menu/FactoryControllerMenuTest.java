package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import static org.assertj.core.api.Assertions.assertThat;

class FactoryControllerMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
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
                "Factory", 0, List.of(
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
                "Factory", 0, List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 16),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, false, "", 0, 0, 1))));

        menu.selectThread(1);

        assertThat(menu.currentParallelism()).isZero();
        assertThat(menu.maxParallelism()).isEqualTo(16);
    }

    @Test
    void selected_thread_exposes_only_its_own_recipe_lock() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, 2, 0, 1,
                "Factory", 0, List.of(
                lockedSnapshot(0, false, "mmcr:first"),
                lockedSnapshot(1, true, "mmcr:second"))));

        assertThat(menu.selectedRecipeLocked()).isFalse();
        assertThat(menu.selectedLockedRecipeId()).isEmpty();

        menu.selectThread(1);

        assertThat(menu.selectedRecipeLocked()).isTrue();
        assertThat(menu.selectedLockedRecipeId()).isEqualTo("mmcr:second");
    }

    @Test
    void thread_snapshot_compatibility_constructor_defaults_to_unlocked() {
        FactoryRecipeScheduler.ThreadSnapshot snapshot = new FactoryRecipeScheduler.ThreadSnapshot(
                0, true, false, false, "", 0, 0, 1, "failure");

        assertThat(snapshot.locked()).isFalse();
        assertThat(snapshot.lockedRecipeId()).isEmpty();
        assertThat(snapshot.lastFailureUnloc()).isEqualTo("failure");
    }

    @Test
    void payload_round_trip_preserves_thread_recipe_lock() {
        FactoryControllerSnapshot snapshot = new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, 1, 0, 1,
                "Factory", 0, List.of(lockedSnapshot(0, true, "mmcr:locked_recipe")));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.EMPTY, ConnectionType.NEOFORGE);

        PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer, new PktFactoryControllerStatePayload(snapshot));
        FactoryRecipeScheduler.ThreadSnapshot decoded =
                PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer).snapshot().threads().getFirst();

        assertThat(decoded.locked()).isTrue();
        assertThat(decoded.lockedRecipeId()).isEqualTo("mmcr:locked_recipe");
    }

    @Test
    void unlocked_thread_snapshot_discards_recipe_id() {
        FactoryRecipeScheduler.ThreadSnapshot snapshot = new FactoryRecipeScheduler.ThreadSnapshot(
                0, true, false, false, "", 0, 0, 1, "", false, "mmcr:stale_lock");

        assertThat(snapshot.lockedRecipeId()).isEmpty();
    }

    @Test
    void snapshot_exposes_the_last_failure_key() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, true, 1, 1, 1, 1,
                "Factory", 0, "gui.mmcr.controller.failure.missing_input",
                List.of(new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, true, "mmcr:first", 4, 20, 1))));

        assertThat(menu.lastFailureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_input");
        assertThat(menu.selectedThread().tick()).isEqualTo(4);
    }

    @Test
    void data_slot_exposes_level_failure_when_factory_snapshot_has_no_failure() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));

        menu.setData(2, 4);

        assertThat(menu.lastFailureUnloc()).isEqualTo("gui.mmcr.controller.failure.level_insufficient");
    }

    @Test
    void absent_failure_is_exposed_as_an_empty_key() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));

        assertThat(menu.lastFailureUnloc()).isEmpty();
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
                Arrays.stream(indexes).mapToObj(index -> new FactoryRecipeScheduler.ThreadSnapshot(
                        index, index == 0, false, false, "", 0, 0, 1)).toList());
    }

    private static FactoryRecipeScheduler.ThreadSnapshot lockedSnapshot(int index, boolean locked, String recipeId) {
        return new FactoryRecipeScheduler.ThreadSnapshot(index, index == 0, false, false,
                "", 0, 0, 1, "", locked, locked ? recipeId : "");
    }

    private static FriendlyByteBuf bufferAt(BlockPos pos) {
        ByteBuf raw = Unpooled.buffer();
        FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
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

package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.datagen.Translations;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.network.FactoryControllerSnapshot;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeScheduler;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
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
    void queue_layout_selects_thread_zero_and_maps_visible_rows() {
        assertThat(FactoryControllerScreen.defaultSelectedThread()).isZero();
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 3, 10, 10)).isEqualTo(3);
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 100, 10)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 39)).isZero();
        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 40)).isEqualTo(-1);
    }

    @Test
    void visible_row_maps_to_snapshot_thread_index() {
        List<FactoryRecipeScheduler.ThreadSnapshot> threads = List.of(thread(3), thread(42));

        assertThat(FactoryControllerScreen.threadIndexAt(8, 8, 0, 10, 41, threads)).isEqualTo(42);
    }

    @Test
    void visible_thread_count_matches_mmce_page_size() {
        assertThat(FactoryControllerScreen.visibleThreadCount(3)).isEqualTo(3);
        assertThat(FactoryControllerScreen.visibleThreadCount(8)).isEqualTo(6);
    }

    @Test
    void scroll_offset_is_capped_to_available_thread_pages() {
        assertThat(FactoryControllerScreen.clampScrollOffset(0, 8)).isZero();
        assertThat(FactoryControllerScreen.clampScrollOffset(2, 8)).isEqualTo(2);
        assertThat(FactoryControllerScreen.clampScrollOffset(7, 8)).isEqualTo(2);
    }

    @Test
    void scrollbar_handle_uses_mmce_offset_and_scroll_range() {
        assertThat(FactoryControllerScreen.shouldRenderScrollbar(6)).isTrue();
        assertThat(FactoryControllerScreen.isScrollbarInteractive(6)).isFalse();
        assertThat(FactoryControllerScreen.isScrollbarInteractive(7)).isTrue();
        assertThat(FactoryControllerScreen.shouldRenderScrollbar(7)).isTrue();
        assertThat(FactoryControllerScreen.SCROLLBAR_HANDLE_WIDTH).isEqualTo(12);
        assertThat(FactoryControllerScreen.SCROLLBAR_HANDLE_HEIGHT).isEqualTo(32);
        assertThat(FactoryControllerScreen.scrollbarHandleY(0, 8)).isEqualTo(FactoryControllerScreen.SCROLLBAR_Y);
        assertThat(FactoryControllerScreen.scrollbarHandleY(1, 8)).isEqualTo(90);
        assertThat(FactoryControllerScreen.scrollbarHandleY(2, 8)).isEqualTo(173);
    }

    @Test
    void scrollbar_drag_position_maps_back_to_scroll_offset() {
        assertThat(FactoryControllerScreen.scrollOffsetFromScrollbarY(FactoryControllerScreen.SCROLLBAR_Y, 8, 0)).isZero();
        assertThat(FactoryControllerScreen.scrollOffsetFromScrollbarY(90, 8, 0)).isEqualTo(1);
        assertThat(FactoryControllerScreen.scrollOffsetFromScrollbarY(173, 8, 0)).isEqualTo(2);
        assertThat(FactoryControllerScreen.scrollOffsetFromScrollbarY(500, 8, 0)).isEqualTo(2);
    }

    @Test
    void progress_is_zero_for_idle_and_full_when_complete() {
        assertThat(FactoryControllerScreen.progressWidth(0, 0)).isZero();
        assertThat(FactoryControllerScreen.progressWidth(100, 100)).isEqualTo(FactoryControllerScreen.THREAD_ROW_WIDTH);
    }

    @Test
    void thread_elements_use_the_full_atlas_without_extra_vertical_offset() {
        assertThat(FactoryControllerScreen.elementTextureWidth()).isEqualTo(256);
        assertThat(FactoryControllerScreen.elementTextureHeight()).isEqualTo(256);
        assertThat(FactoryControllerScreen.threadElementY(20)).isEqualTo(20);
    }

    @Test
    void selected_overlay_is_clipped_to_the_thread_element_bounds() {
        assertThat(FactoryControllerScreen.selectedOverlayX(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.selectedOverlayY(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.selectedOverlayWidth()).isEqualTo(FactoryControllerScreen.THREAD_ROW_WIDTH);
        assertThat(FactoryControllerScreen.selectedOverlayHeight()).isEqualTo(FactoryControllerScreen.THREAD_ROW_HEIGHT);
        assertThat(FactoryControllerScreen.selectedOverlayRight(20)).isEqualTo(105);
        assertThat(FactoryControllerScreen.selectedOverlayBottom(20)).isEqualTo(51);
    }

    @Test
    void locked_selected_and_progress_overlays_draw_in_priority_order() {
        FactoryRecipeScheduler.ThreadSnapshot thread = new FactoryRecipeScheduler.ThreadSnapshot(
                1, false, false, true, "mmcr:active", 5, 10, 1, "", true, "mmcr:active");

        assertThat(FactoryControllerScreen.threadOverlayColors(thread, 1))
                .containsExactly(FactoryControllerScreen.PROGRESS_THREAD_OVERLAY,
                        FactoryControllerScreen.SELECTED_THREAD_OVERLAY,
                        FactoryControllerScreen.LOCKED_THREAD_OVERLAY);
        assertThat(FactoryControllerScreen.lockedOverlayX(20)).isEqualTo(FactoryControllerScreen.selectedOverlayX(20));
        assertThat(FactoryControllerScreen.lockedOverlayY(20)).isEqualTo(FactoryControllerScreen.selectedOverlayY(20));
        assertThat(FactoryControllerScreen.lockedOverlayRight(20)).isEqualTo(FactoryControllerScreen.selectedOverlayRight(20));
        assertThat(FactoryControllerScreen.lockedOverlayBottom(20)).isEqualTo(FactoryControllerScreen.selectedOverlayBottom(20));
    }

    @Test
    void progress_overlay_aligns_with_the_thread_element_without_extra_bottom_pixel() {
        assertThat(FactoryControllerScreen.progressOverlayX(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.progressOverlayY(20)).isEqualTo(20);
        assertThat(FactoryControllerScreen.progressOverlayHeight()).isEqualTo(FactoryControllerScreen.THREAD_ROW_HEIGHT - 1);
        assertThat(FactoryControllerScreen.progressOverlayBottom(20)).isEqualTo(51);
    }

    @Test
    void detail_lines_match_factory_controller_spacing_and_show_progress_last_when_active() {
        assertThat(FactoryControllerScreen.detailTitleY(12)).isEqualTo(12);
        assertThat(FactoryControllerScreen.nextDetailY(12)).isEqualTo(26);
        assertThat(FactoryControllerScreen.shouldRenderProgress(false, 0)).isFalse();
        assertThat(FactoryControllerScreen.shouldRenderProgress(true, 0)).isFalse();
        assertThat(FactoryControllerScreen.shouldRenderProgress(true, 100)).isTrue();
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

    @Test
    void level_lines_use_the_same_text_as_the_normal_controller() {
        var typeId = MMCR.id("factory_test_coil");
        MachineLevelRegistry.beginRegistration();
        MachineLevelRegistry.registerType(new LevelType(typeId, net.minecraft.network.chat.Component.literal("Coils")));
        MachineLevel level = new MachineLevel(MMCR.id("factory_test_iron_coil"), typeId, 0,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                new ItemStack(Holder.direct(Blocks.IRON_BLOCK.asItem(), DataComponentMap.EMPTY)), LevelModifier.IDENTITY);
        MachineLevelRegistry.registerLevel(level);
        MachineLevelRegistry.freezeRegistration();

        assertThat(FactoryControllerScreen.levelLines(java.util.Map.of(typeId, level)))
                .containsExactly(MachineMenuScreen.levelLine(level));
    }

    @Test
    void detail_failure_prefers_the_selected_thread_failure() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, 2, 0, 1,
                "Factory", 0, "gui.mmcr.controller.failure.missing_input", List.of(
                new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, false, "", 0, 0, 1, ""),
                new FactoryRecipeScheduler.ThreadSnapshot(1, false, false, false, "", 0, 0, 1,
                        "gui.mmcr.controller.failure.level_insufficient"))));

        menu.selectThread(1);

        assertThat(FactoryControllerScreen.selectedFailureUnloc(menu))
                .isEqualTo("gui.mmcr.controller.failure.level_insufficient");
    }

    @Test
    void detail_failure_falls_back_to_controller_failure_when_selected_thread_has_none() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null, null));
        menu.applySnapshot(new FactoryControllerSnapshot(BlockPos.ZERO, true, false, 0, 1, 0, 1,
                "Factory", 0, "gui.mmcr.controller.failure.missing_output",
                List.of(new FactoryRecipeScheduler.ThreadSnapshot(0, true, false, false, "", 0, 0, 1, ""))));

        assertThat(FactoryControllerScreen.selectedFailureUnloc(menu))
                .isEqualTo("gui.mmcr.controller.failure.missing_output");
    }

    @Test
    void recipe_lock_button_stays_outside_factory_inventory_and_hotbar_bounds() {
        FactoryControllerScreen.Rect button = FactoryControllerScreen.recipeLockButtonRect(0, 0, 280, 213);
        FactoryControllerScreen.Rect playerInventory = new FactoryControllerScreen.Rect(113, 131, 162, 54);
        FactoryControllerScreen.Rect hotbar = new FactoryControllerScreen.Rect(113, 189, 162, 18);

        assertThat(button.width()).isEqualTo(button.height());
        assertThat(button.overlaps(playerInventory)).isFalse();
        assertThat(button.overlaps(hotbar)).isFalse();
    }

    @Test
    void enabled_tooltip_contains_recipe_id_on_second_line() {
        assertThat(FactoryControllerScreen.recipeLockTooltip(true, "mod:recipe"))
                .extracting(component -> ((TranslatableContents) component.getContents()).getKey())
                .containsExactly("gui.mmcr.controller.recipe_lock.enabled", "gui.mmcr.controller.recipe_lock.recipe");
        assertThat(((TranslatableContents) FactoryControllerScreen.recipeLockTooltip(true, "mod:recipe").get(1).getContents()).getArgs()[0].toString())
                .isEqualTo("literal{mod:recipe}");
    }

    @Test
    void disabled_tooltip_contains_disabled_line_only() {
        assertThat(FactoryControllerScreen.recipeLockTooltip(false, "mod:recipe"))
                .singleElement()
                .satisfies(component -> assertThat(((TranslatableContents) component.getContents()).getKey())
                        .isEqualTo("gui.mmcr.controller.recipe_lock.disabled"));
    }

    @Test
    void recipe_lock_translation_keys_use_requested_chinese_tooltip_text() {
        assertThat(Translations.ALL.get("zh_cn"))
                .containsEntry("gui.mmcr.controller.recipe_lock.enabled", "配方锁定: 启用")
                .containsEntry("gui.mmcr.controller.recipe_lock.disabled", "配方锁定: 禁用")
                .containsEntry("gui.mmcr.controller.recipe_lock.recipe", "%s");
    }

    private static FactoryRecipeScheduler.ThreadSnapshot thread(int index) {
        return new FactoryRecipeScheduler.ThreadSnapshot(index, false, false, false, "", 0, 0, 1);
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

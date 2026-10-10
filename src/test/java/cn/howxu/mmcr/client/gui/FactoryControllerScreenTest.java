package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.client.controller.ControllerScreenTextCache;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.TextLineData;
import cn.howxu.mmcr.internal.menu.FactoryControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Factory controller screen behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryControllerScreenTest {
    private static final ResourceLocation DETAIL_LEVEL_TYPE_ID = MMCR.id("factory_detail_level_type");
    private static final List<ResourceLocation> DETAIL_LEVEL_IDS = List.of(
            MMCR.id("factory_detail_level_one"),
            MMCR.id("factory_detail_level_two"),
            MMCR.id("factory_detail_level_three"));

    @AfterEach
    void clearCache() {
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.FACTORY_CONTROLLER,
                new MenuType<>((containerId, inventory) -> FactoryControllerMenu.clientOpen(containerId, inventory),
                        FeatureFlags.VANILLA_SET));
        LevelType levelType = new LevelType(DETAIL_LEVEL_TYPE_ID, Component.literal("Factory Detail Level"));
        TestBootstrap.registerType(levelType);
        for (int index = 0; index < DETAIL_LEVEL_IDS.size(); index++) {
            TestBootstrap.registerLevel(detailLevel(index));
        }
    }

    @Test
    void fallback_detail_title_preserves_screen_title_style() {
        Component title = Component.literal("Styled factory").withStyle(ChatFormatting.GOLD);

        Component detailTitle = FactoryControllerScreen.detailTitle(title, "", 2);

        assertThat(detailTitle.getSiblings().getFirst()).isEqualTo(title);
        assertThat(detailTitle.getString()).isEqualTo("Styled factory #2");
    }

    @Test
    void recipe_pool_entry_uses_the_approved_factory_coordinates() {
        assertThat(FactoryControllerScreen.RECIPE_POOL_BUTTON_X).isEqualTo(258);
        assertThat(FactoryControllerScreen.RECIPE_POOL_BUTTON_Y).isEqualTo(9);
    }

    @Test
    void active_selected_thread_hides_aggregate_last_failure() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 1, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, "base", true, false, true, "mmcr:recipe", 1, 20,
                                1, (ExecutionStatus) null),
                        new FactoryRuntime.ThreadSnapshot(1, "factory-1", false, false, false, "", 0, 0, 1,
                                (ExecutionStatus) null)),
                "Factory", 0, failure(MMCR.id("failure")), List.of(), 0, 1));

        assertThat(FactoryControllerScreen.selectedFailureUnloc(menu)).isEmpty();
    }

    @Test
    void factory_detail_lines_preserve_snapshot_order() {
        FactoryControllerMenu menu = menuWithDetailRows();
        List<ControllerTextLine> detailLines = ControllerUiTextLines.details(menu.legacyUiSnapshot(), "base");

        assertThat(detailLines.getFirst().text()).isEqualTo(
                Component.translatable("gui.mmcr.controller.status_label")
                        .append(Component.literal(" "))
                        .append(Component.translatable("gui.mmcr.controller.running")));
        assertThat(detailLines.getFirst().color()).isEqualTo(0xFF55FF55);

        assertThat(detailLines.subList(1, detailLines.size())).containsExactly(
                new ControllerTextLine(Component.translatable("gui.mmcr.controller.recipe_pool",
                        Component.literal("mmcr:factory_detail_pool")), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(0)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(1)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.levelLine(
                        detailLevel(2)), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.last_failure", Component.translatable(
                                "gui.mmcr.controller.failure.missing_input")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.parallelSlotLine(2),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(MachineControllerScreen.parallelLine(4, 8),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.redstone_stopped"), MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.threads", Component.literal("2"), Component.literal("3")),
                        MachineControllerScreen.STATUS_LABEL_COLOR),
                new ControllerTextLine(Component.translatable(
                        "gui.mmcr.controller.progress", "100%"), -1));
    }

    @Test
    void factory_screen_appends_external_lines_after_standard_lines() {
        FactoryControllerMenu menu = menuWithDetailRows();
        ControllerScreenTextCache.replace(BlockPos.ZERO, 1L,
                List.of(new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                        MMCR.id("factory_external"), Component.literal("external"))));

        List<ControllerTextLine> standard = FactoryControllerScreen.detailLines(menu);
        List<ControllerTextLine> composed = FactoryControllerScreen.controllerTextLines(menu);

        assertThat(composed.subList(0, standard.size())).containsExactlyElementsOf(standard);
        assertThat(composed.getLast()).isEqualTo(new ControllerTextLine(Component.literal("external"),
                ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR));
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @Test
    void factory_screen_uses_text_for_the_selected_thread_only() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 2, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, "lane-0", true, false, true,
                                "mmcr:recipe_0", 1, 20, 1, (ExecutionStatus) null),
                        new FactoryRuntime.ThreadSnapshot(1, "lane-1", false, false, true,
                                "mmcr:recipe_1", 2, 20, 1, (ExecutionStatus) null)),
                "Factory", 0, null, List.of(), 0, 1));
        ControllerScreenTextSnapshot.Line first = new ControllerScreenTextSnapshot.Line(
                ControllerScreenTextScope.CONTROLLER, MMCR.id("factory_lane_0"), Component.literal("lane 0"));
        ControllerScreenTextSnapshot.Line second = new ControllerScreenTextSnapshot.Line(
                ControllerScreenTextScope.CONTROLLER, MMCR.id("factory_lane_1"), Component.literal("lane 1"));
        ControllerScreenTextCache.replace(BlockPos.ZERO, "lane-0", 1L, List.of(first));
        ControllerScreenTextCache.replace(BlockPos.ZERO, "lane-1", 1L, List.of(second));

        assertThat(FactoryControllerScreen.controllerTextLines(menu))
                .anyMatch(line -> line.text().equals(first.text()))
                .noneMatch(line -> line.text().equals(second.text()));

        menu.selectThread(1);

        assertThat(FactoryControllerScreen.controllerTextLines(menu))
                .anyMatch(line -> line.text().equals(second.text()))
                .noneMatch(line -> line.text().equals(first.text()));
    }

    @Test
    void factory_controller_viewport_wraps_long_external_text() throws Exception {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        ControllerScreenTextCache.replace(BlockPos.ZERO, 1L,
                List.of(new ControllerScreenTextSnapshot.Line(ControllerScreenTextScope.CONTROLLER,
                        MMCR.id("factory_long"), Component.literal("x".repeat(161)))));
        FactoryControllerScreen screen = (FactoryControllerScreen) unsafe().allocateInstance(FactoryControllerScreen.class);

        List<ControllerScreenTextComposer.VisualLine> visualLines = ControllerScreenTextComposer.wrap(
                ControllerScreenTextComposerTest.testFont(), FactoryControllerScreen.controllerTextLines(menu),
                screen.scrollableTextViewport().width());

        assertThat(visualLines).hasSizeGreaterThan(2);
        assertThat(visualLines.getLast().color()).isEqualTo(ControllerScreenTextComposer.DEFAULT_EXTERNAL_COLOR);
        ControllerScreenTextCache.clear(BlockPos.ZERO);
    }

    @Test
    void factory_detail_line_count_is_independent_from_thread_scroll_range() {
        FactoryControllerMenu menu = menuWithDetailRows();

        assertThat(ControllerUiTextLines.details(menu.legacyUiSnapshot(), "base")).hasSize(11);
        assertThat(FactoryControllerScreen.clampScrollOffset(99, menu.threads().size())).isZero();
    }

    @Test
    void default_factory_reads_owned_host_fields_and_global_then_selected_lane_then_recipe_lines() throws Exception {
        var current = new AtomicReference<ControllerUiSnapshot>(uiSnapshot(Role.HOST,
                List.of(uiLane("base", 0, true, "base text"), uiLane("stable", 7, false, "selected text"))));
        FactoryControllerScreen screen = snapshotScreen(current::get);
        screen.selectLane("stable");
        List<ControllerTextLine> lines = screen.scrollableTextLines();
        assertThat(lines).extracting(ControllerTextLine::text).contains(
                Component.translatable("gui.mmcr.controller.installed_modules", Component.literal("5")));
        assertThat(lines).extracting(ControllerTextLine::text).doesNotContain(Component.literal("base text"));
        int global = lines.stream().map(ControllerTextLine::text).toList().indexOf(Component.literal("global"));
        assertThat(lines.get(global + 1).text()).isEqualTo(Component.literal("selected text"));
        assertThat(lines.get(global + 2).tooltip()).containsExactly(
                Component.translatable("gui.mmcr.controller.recipe.energy_input_exact", "600"));
        assertThat(FactoryControllerScreen.detailTitle(current.get().machineName(), 7).getString())
                .isEqualTo("Owned factory #7");
    }

    @Test
    void selection_survives_reindexing_and_deleted_lanes_fall_back_and_reset_detail_scroll() throws Exception {
        LaneData base = uiLane("base", 0, true, "base text");
        LaneData selected = uiLane("stable", 7, false, "selected text");
        var current = new AtomicReference<ControllerUiSnapshot>(uiSnapshot(Role.MODULE, List.of(base, selected)));
        FactoryControllerScreen screen = snapshotScreen(current::get);
        screen.selectLane("stable");
        Field offset = AbstractScrollableTextScreen.class.getDeclaredField("textScrollOffset");
        offset.setAccessible(true);
        offset.setInt(screen, 1);
        current.set(uiSnapshot(Role.MODULE, List.of(uiLane("stable", 3, false, "reindexed"), base)));
        screen.scrollableTextLines();
        assertThat(screen.selectedLaneId()).isEqualTo("stable");
        assertThat(offset.getInt(screen)).isEqualTo(1);
        current.set(uiSnapshot(Role.MODULE, List.of(uiLane("first", 9, false, "first"), base)));
        screen.scrollableTextLines();
        assertThat(screen.selectedLaneId()).isEqualTo("base");
        assertThat(offset.getInt(screen)).isZero();
        offset.setInt(screen, 1);
        current.set(uiSnapshot(Role.MODULE, List.of(uiLane("first", 9, false, "first"))));
        screen.scrollableTextLines();
        assertThat(screen.selectedLaneId()).isEqualTo("first");
        assertThat(offset.getInt(screen)).isZero();
        current.set(uiSnapshot(Role.MODULE, List.of()));
        screen.scrollableTextLines();
        assertThat(screen.selectedLaneId()).isNull();
    }

    @Test
    void explicit_thread_change_resets_scroll_but_clicking_the_same_lane_does_not() throws Exception {
        FactoryControllerScreen screen = snapshotScreen(() -> uiSnapshot(Role.NORMAL,
                List.of(uiLane("base", 0, true, "base"), uiLane("other", 2, false, "other"))));
        screen.selectLane("base");
        Field offset = AbstractScrollableTextScreen.class.getDeclaredField("textScrollOffset");
        offset.setAccessible(true);
        offset.setInt(screen, 4);
        screen.selectLane("base");
        assertThat(offset.getInt(screen)).isEqualTo(4);
        screen.selectLane("other");
        assertThat(offset.getInt(screen)).isZero();
    }

    @Test
    void legacy_menu_selection_also_tracks_lane_identity_across_reindexing() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        var first = new FactoryRuntime.ThreadSnapshot(7, "stable", false, false, false, "", 0, 0, 1,
                (ExecutionStatus) null);
        menu.applySnapshot(legacyThreads(List.of(FactoryRuntime.ThreadSnapshot.idleBase(), first)));
        menu.selectThread(7);
        var reindexed = new FactoryRuntime.ThreadSnapshot(3, "stable", false, false, false, "", 0, 0, 1,
                (ExecutionStatus) null);
        menu.applySnapshot(legacyThreads(List.of(reindexed, FactoryRuntime.ThreadSnapshot.idleBase())));
        assertThat(menu.selectedThread().laneId()).isEqualTo("stable");
        assertThat(menu.selectedThreadIndex()).isEqualTo(3);
        menu.applySnapshot(legacyThreads(List.of(first, FactoryRuntime.ThreadSnapshot.idleBase())));
        assertThat(menu.selectedThreadIndex()).isEqualTo(7);
        menu.applySnapshot(legacyThreads(List.of(FactoryRuntime.ThreadSnapshot.idleBase())));
        assertThat(menu.selectedThread().laneId()).isEqualTo("base");
    }

    @Test
    void matched_stage_line_appears_after_status_when_formed_multi_stage() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 1, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, "base", true, false, true,
                        "mmcr:recipe", 1, 20, 1, (ExecutionStatus) null)),
                "Factory", 0, null, List.of(), 4, 10));

        assertThat(FactoryControllerScreen.detailLines(menu))
                .extracting(ControllerTextLine::text)
                .contains(FactoryControllerScreen.matchedStageLine(4));
    }

    @Test
    void matched_stage_line_absent_when_not_formed() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(false, false, List.of(), 2, 0, 1L, false,
                List.of(), "Factory", 0, null, List.of(), 0, 10));

        assertThat(FactoryControllerScreen.detailLines(menu))
                .noneMatch(line -> line.text().equals(FactoryControllerScreen.matchedStageLine(0)));
    }

    @Test
    void matched_stage_line_absent_for_single_stage_machine() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
        menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 2, 1, 1L, false,
                List.of(new FactoryRuntime.ThreadSnapshot(0, "base", true, false, true,
                        "mmcr:recipe", 1, 20, 1, (ExecutionStatus) null)),
                "Factory", 0, null, List.of(), 1, 1));

        assertThat(FactoryControllerScreen.detailLines(menu))
                .extracting(ControllerTextLine::text)
                .doesNotContain(FactoryControllerScreen.matchedStageLine(1));
    }

    @Test
    void factory_detail_rows_use_scaled_pose_coordinates() {
        assertThat(FactoryControllerScreen.detailTextY(34)).isEqualTo(40);
    }

    @Test
    void factory_detail_rows_include_the_screen_offset_before_scaling() {
        assertThat(FactoryControllerScreen.detailTextY(19, 22)).isEqualTo(48);
    }

    @Test
    void factory_detail_rows_keep_uniform_spacing_in_scaled_pose() {
        int statusY = 100;
        int firstDetailY = FactoryControllerScreen.detailLineY(statusY, 22, 32);
        int secondDetailY = FactoryControllerScreen.detailLineY(statusY, 22, 42);

        assertThat(firstDetailY - statusY).isEqualTo(10);
        assertThat(secondDetailY - firstDetailY).isEqualTo(10);
    }

    @Test
    void thread_scroll_hit_test_stays_separate_from_detail_viewport() {
        int left = 37;
        int top = 19;
        AbstractScrollableTextScreen.TextViewport viewport =
                new AbstractScrollableTextScreen.TextViewport(113, 22, 160, 102, 0.85F, 10);

        assertThat(FactoryControllerScreen.mouseOverThreadList(left, top,
                left + FactoryControllerScreen.THREAD_ROW_X + 1,
                top + FactoryControllerScreen.THREAD_ROW_Y + 1)).isTrue();
        assertThat(AbstractScrollableTextScreen.containsViewport(viewport, left, top,
                left + viewport.x(), top + viewport.y())).isTrue();
        assertThat(FactoryControllerScreen.mouseOverThreadList(left, top,
                left + viewport.x(), top + viewport.y())).isFalse();
    }

    @Test
    void clicking_below_visible_threads_cannot_select_an_invisible_row() {
        int left = 37;
        int top = 19;
        int below = top + FactoryControllerScreen.VISIBLE_THREADS
                * (FactoryControllerScreen.THREAD_ROW_HEIGHT + FactoryControllerScreen.THREAD_ROW_GAP);

        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 0, left + 1, below)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 2, left + 1, below)).isEqualTo(-1);
    }

    @Test
    void scrolled_thread_hit_test_preserves_visible_bounds_gaps_and_lane_indices() {
        int left = 37;
        int top = 19;
        int rowStride = FactoryControllerScreen.THREAD_ROW_HEIGHT + FactoryControllerScreen.THREAD_ROW_GAP;
        List<FactoryRuntime.ThreadSnapshot> threads = List.of(
                new FactoryRuntime.ThreadSnapshot(4, "lane-4", false, false, false, "", 0, 0, 1,
                        (ExecutionStatus) null),
                new FactoryRuntime.ThreadSnapshot(9, "lane-9", false, false, false, "", 0, 0, 1,
                        (ExecutionStatus) null),
                new FactoryRuntime.ThreadSnapshot(15, "lane-15", false, false, false, "", 0, 0, 1,
                        (ExecutionStatus) null));

        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1, top, threads)).isEqualTo(9);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1,
                top + rowStride, threads)).isEqualTo(15);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1,
                top + FactoryControllerScreen.THREAD_ROW_HEIGHT, threads)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1,
                top + 2 * rowStride, threads)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1, top - 1)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1,
                left + FactoryControllerScreen.THREAD_ROW_WIDTH, top)).isEqualTo(-1);
        assertThat(FactoryControllerScreen.threadIndexAt(left, top, 1, left + 1,
                top + (FactoryControllerScreen.VISIBLE_THREADS - 1) * rowStride
                        + FactoryControllerScreen.THREAD_ROW_HEIGHT - 1))
                .isEqualTo(FactoryControllerScreen.VISIBLE_THREADS);
    }

    private static FactoryControllerMenu menuWithDetailRows() {
        FactoryControllerMenu menu = FactoryControllerMenu.clientOpen(1, new Inventory(null));
         menu.applySnapshot(new FactorySnapshot(true, true, List.of(), 3, 2, 8L, true,
                List.of(new FactoryRuntime.ThreadSnapshot(0, "base", true, false, true, "mmcr:recipe", 20, 20,
                        4, failure(MMCR.id("selected_failure")))),
                 "Factory", 2, null, DETAIL_LEVEL_IDS.stream().map(ResourceLocation::toString).toList(), 0, 1,
                 "mmcr:factory_detail", "mmcr:factory_detail_pool"));
         return menu;
    }

    private static ExecutionStatus failure(ResourceLocation id) {
        return ExecutionStatus.blocked(id, id, FailureOccurrence.at(BuiltinFailureReasons.MISSING_INPUT, id,
                FailurePhase.RUNTIME, null, null, Map.of()));
    }

    private static LaneData uiLane(String id, int index, boolean base, String text) {
        return new LaneData(id, index, base, false, true, MMCR.id("ui_recipe"), 10, 20, 4,
                null, List.of(new TextLineData(MMCR.id("lane_text"), ControllerUiSnapshot.TextLine.Scope.OPERATION,
                Component.literal(text))), new RecipeData(List.of(), 600, 0, 0, 20, 4));
    }

    private static FactorySnapshot legacyThreads(List<FactoryRuntime.ThreadSnapshot> threads) {
        return new FactorySnapshot(true, false, List.of(), 2, 0, 1, false, threads, "Factory", 0,
                null, List.of(), 0, 1);
    }

    private static ControllerUiSnapshotData uiSnapshot(Role role, List<LaneData> lanes) {
        var opening = FactoryControllerMenu.clientOpen(1, new Inventory(null)).uiOpenData();
        return new ControllerUiSnapshotData(opening.sessionId(), 1, true, opening.dimension(), opening.pos(),
                new HeaderData(MMCR.id("owned_factory"), ControllerUiSnapshot.Kind.FACTORY, role,
                        Component.literal("Owned factory"), true, true, false, 5, MMCR.id("host"), 0, 1,
                        List.of(), 2, 8, 10, lanes.size(), List.of(MMCR.id("server_pool")), MMCR.id("server_pool"),
                        null, false, Map.of(), List.of(new TextLineData(MMCR.id("global_text"),
                        ControllerUiSnapshot.TextLine.Scope.CONTROLLER, Component.literal("global")))), lanes);
    }

    private static FactoryControllerScreen snapshotScreen(Supplier<ControllerUiSnapshot> snapshot) throws Exception {
        var unsafe = unsafe();
        FactoryControllerScreen screen = (FactoryControllerScreen) unsafe.allocateInstance(FactoryControllerScreen.class);
        unsafe.putObject(screen, unsafe.objectFieldOffset(FactoryControllerScreen.class.getDeclaredField("snapshot")), snapshot);
        unsafe.putObject(screen, unsafe.objectFieldOffset(Screen.class.getDeclaredField("font")),
                ControllerScreenTextComposerTest.testFont());
        return screen;
    }

    private static MachineLevel detailLevel(int index) {
        return new MachineLevel(DETAIL_LEVEL_IDS.get(index), DETAIL_LEVEL_TYPE_ID, index + 1,
                new BlockPredicate.OfBlockState(detailBlock(index).defaultBlockState()),
                ItemStack.EMPTY, ModifierDefinition.EMPTY);
    }

    private static Block detailBlock(int index) {
        return switch (index) {
            case 0 -> Blocks.IRON_BLOCK;
            case 1 -> Blocks.GOLD_BLOCK;
            case 2 -> Blocks.DIAMOND_BLOCK;
            default -> throw new IllegalArgumentException("Unknown detail level: " + index);
        };
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

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
